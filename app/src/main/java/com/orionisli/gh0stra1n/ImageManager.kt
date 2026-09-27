package com.orionisli.gh0stra1n

object ImageManager {

    fun listImages(part: PartitionDef): Pair<List<String>, String> {
        val r = SuChannel.run("ls -t ${PartitionTable.glob(part)} 2>/dev/null")
        if (!r.ok || r.out.isBlank()) return emptyList<String>() to r.out
        return r.out.trim().lines().filter { it.isNotBlank() && it.endsWith(".img") } to r.out
    }

    fun createImage(part: PartitionDef, sizeMiB: Long): Result<String> {
        return createImageWithPreset(part.id, sizeMiB, ManifestStore.loadPreset())
    }

    fun createImageWithPreset(
        partId: String,
        sizeMiB: Long,
        preset: ManifestStore.ImagePresetConfig = ManifestStore.loadPreset()
    ): Result<String> {
        val tmp = "${PartitionTable.BASE_DIR}/$partId-new.img"
        val sizeBytes = sizeMiB * 1024 * 1024

        val features = mutableListOf<String>()
        if (preset.enableDirIndex) features.add("dir_index")
        if (preset.enableFiletype) features.add("filetype")
        if (preset.enableSparseSuper) features.add("sparse_super")
        val featArg = if (features.isNotEmpty()) "-O ${features.joinToString(",")}" else ""

        val cmd = "mkdir -p ${PartitionTable.BASE_DIR} && " +
                "rm -f $tmp && " +
                "truncate -s $sizeBytes $tmp && " +
                "mke2fs -F -q -t ${preset.fsType} -b ${preset.blockSize} -i ${preset.bytesPerInode} -m ${preset.reservedRatioPct} $featArg $tmp && " +
                "UUID=\$(blkid $tmp | sed -n 's/.*UUID=\"\\([0-9a-fA-F]\\{8\\}\\).*/\\1/p') && " +
                "mv $tmp ${PartitionTable.BASE_DIR}/$partId-\$UUID.img && " +
                "sync && echo ${PartitionTable.BASE_DIR}/$partId-\$UUID.img"

        AppLogger.i("CREATE", "正在创建分区 [$partId] 镜像 (大小=${sizeMiB}MB, 块=${preset.blockSize}, Inode比=${preset.bytesPerInode})...")
        val mk = SuChannel.run(cmd, 60, logCmd = true)
        val path = mk.out.trim().lines().lastOrNull()
        return if (mk.ok && !path.isNullOrBlank() && path.startsWith(PartitionTable.BASE_DIR)) {
            AppLogger.i("CREATE", "镜像创建成功: ${path.substringAfterLast('/')}")
            PartitionTable.ALL.firstOrNull { it.id == partId }?.let {
                ManifestStore.reconcile(it)
            }
            Result.success(path)
        } else {
            AppLogger.e("CREATE", "镜像创建失败: ${mk.out}")
            Result.failure(RuntimeException("createImage failed: $mk"))
        }
    }

    fun imageStats(image: String): Triple<Long, Long, Long> {
        val r = SuChannel.run(
            "/system/bin/tune2fs -l $image 2>/dev/null | grep -e 'Block count' -e 'Free blocks' -e 'Block size'", 20)
        val bs = r.out.lineSequence().firstOrNull { it.contains("Block size") }
            ?.substringAfter(":")?.trim()?.toLongOrNull() ?: 4096L
        val bc = r.out.lineSequence().firstOrNull { it.contains("Block count") }
            ?.substringAfter(":")?.trim()?.toLongOrNull() ?: 0L
        val fb = r.out.lineSequence().firstOrNull { it.contains("Free blocks") }
            ?.substringAfter(":")?.trim()?.toLongOrNull() ?: 0L
        val st = SuChannel.run("stat -c %b $image", 10)
        val disk = st.out.trim().toLongOrNull()?.times(512L) ?: 0L
        return Triple(bc * bs, fb * bs, disk)
    }

    fun attachLoop(image: String): Result<String> {
        val cmd = "LOOP=\$(losetup -j $image 2>/dev/null | head -1 | cut -d: -f1); " +
                "if [ -z \"\$LOOP\" ]; then LOOP=\$(losetup -f --show $image 2>/dev/null); fi; " +
                "if [ -z \"\$LOOP\" ]; then " +
                "for n in \$(seq 30 2 50); do losetup /dev/block/loop\$n $image 2>/dev/null && LOOP=/dev/block/loop\$n && break; done; " +
                "fi; " +
                "echo \"\$LOOP\""
        val r = SuChannel.run(cmd, 15, logCmd = true)
        val dev = r.out.trim().lines().lastOrNull { it.startsWith("/dev/block/loop") || it.startsWith("/dev/loop") }
        if (r.ok && !dev.isNullOrBlank()) {
            return Result.success(dev)
        }
        return Result.failure(RuntimeException("loop attach 失败: ${r.out}"))
    }

    fun growImage(image: String, newBytes: Long): Result<Unit> {
        val cur = SuChannel.run("stat -c %s $image", 10)
        val curSize = cur.out.trim().toLongOrNull() ?: 0L
        require(newBytes > curSize) { "growImage: new($newBytes) <= cur($curSize)" }
        val r = SuChannel.run(
            "truncate -s $newBytes $image && " +
            "/system/bin/e2fsck -fy $image >/dev/null 2>&1; " +
            "/system/bin/resize2fs -f $image && sync && echo GROW-OK", 120)
        return if (r.ok && r.out.contains("GROW-OK")) Result.success(Unit) else
            Result.failure(RuntimeException("resize2fs failed: $r"))
    }

    fun growImageOnline(part: PartitionDef, image: String, newBytes: Long): Result<Unit> {
        val cur = SuChannel.run("stat -c %s $image", 10)
        val curSize = cur.out.trim().toLongOrNull() ?: 0L
        require(newBytes > curSize) { "growImageOnline: new($newBytes) <= cur($curSize)" }

        val loopCheck = SuChannel.run("losetup -a 2>/dev/null | grep '${image.substringAfterLast('/')}' | head -1 | cut -d: -f1", 10)
        val loopDev = loopCheck.out.trim()

        AppLogger.i("RESIZE", "正在对 [${part.id}] 进行在线热扩容: $image -> ${newBytes / (1024 * 1024)}MiB")
        val cmd = if (loopDev.startsWith("/dev/block/loop")) {
            "truncate -s $newBytes $image && " +
            "losetup -c $loopDev && " +
            "/system/bin/resize2fs -f $loopDev && sync && echo ONLINE-GROW-OK"
        } else {
            "truncate -s $newBytes $image && " +
            "/system/bin/e2fsck -fy $image >/dev/null 2>&1; " +
            "/system/bin/resize2fs -f $image && sync && echo ONLINE-GROW-OK"
        }

        val r = SuChannel.run(cmd, 120, logCmd = true)
        return if (r.ok && r.out.contains("ONLINE-GROW-OK")) {
            AppLogger.i("RESIZE", "[${part.id}] 扩容成功已生效")
            // 更新manifest
            val entry = ManifestStore.reconcile(part)
            val base = image.substringAfterLast('/')
            val idx = entry.images.indexOfFirst { it.file == base }
            if (idx >= 0) {
                entry.images[idx] = entry.images[idx].copy(size = newBytes)
                ManifestStore.putPart(part.id, entry)
            }
            Result.success(Unit)
        } else {
            AppLogger.e("RESIZE", "[${part.id}] 扩容失败: ${r.out}")
            Result.failure(RuntimeException("在线扩容失败: ${r.out}"))
        }
    }

    fun tuneReservedBlocks(image: String, percent: Int): Result<String> {
        AppLogger.i("TUNE", "正在调整保留块比例为 ${percent}%: ${image.substringAfterLast('/')}")
        val r = SuChannel.run("/system/bin/tune2fs -m $percent $image 2>&1", 15, logCmd = true)
        return if (r.ok) {
            AppLogger.i("TUNE", "保留块比例设置成功: ${r.out.trim()}")
            Result.success(r.out.trim())
        } else {
            AppLogger.e("TUNE", "保留块比例设置失败: ${r.out}")
            Result.failure(RuntimeException("tune2fs 失败: ${r.out}"))
        }
    }

    fun getMinimumFsSize(image: String): Long? {
        val r = SuChannel.run("/system/bin/resize2fs -P $image 2>&1", 15)
        val line = r.out.lineSequence().firstOrNull { it.contains("minimum size") } ?: return null
        val blocks = line.substringAfter(":").trim().toLongOrNull() ?: return null
        // 读取 block size
        val bsRes = SuChannel.run("/system/bin/tune2fs -l $image 2>/dev/null | grep 'Block size'", 10)
        val bs = bsRes.out.substringAfter(":").trim().toLongOrNull() ?: 4096L
        return blocks * bs
    }

    fun shrinkImage(part: PartitionDef, image: String, targetBytes: Long, log: (String) -> Unit): Result<Unit> {
        AppLogger.i("SHRINK", "=== 开始收缩镜像 [${part.id}]: 目标大小=${targetBytes / (1024 * 1024)}MiB ===")
        log("准备安全收缩 ${part.id}...")

        val live = livePartitions()
        val wasLive = part.id in live
        if (wasLive) {
            log("正在安全卸载以进行离线收缩...")
            val uRes = unmountStack(part, log)
            if (uRes.isFailure) return Result.failure(uRes.exceptionOrNull()!!)
        }

        val targetMiB = targetBytes / (1024 * 1024)
        val cmd = "/system/bin/e2fsck -fy $image >/dev/null 2>&1; " +
                "/system/bin/resize2fs -f $image ${targetMiB}M && " +
                "truncate -s $targetBytes $image && " +
                "sync && echo SHRINK-OK"
        val r = SuChannel.run(cmd, 120, logCmd = true)
        if (!r.ok || !r.out.contains("SHRINK-OK")) {
            AppLogger.e("SHRINK", "收缩失败: ${r.out}")
            return Result.failure(RuntimeException("收缩失败: ${r.out}"))
        }

        val entry = ManifestStore.reconcile(part)
        val base = image.substringAfterLast('/')
        val idx = entry.images.indexOfFirst { it.file == base }
        if (idx >= 0) {
            entry.images[idx] = entry.images[idx].copy(size = targetBytes)
            ManifestStore.putPart(part.id, entry)
        }

        if (wasLive) {
            log("收缩完成，正在重新挂载...")
            mountStack(part, log)
        }

        AppLogger.i("SHRINK", "[${part.id}] 镜像收缩成功: ${targetMiB}MiB")
        log("${part.id} 成功收缩至 ${targetMiB}MiB")
        return Result.success(Unit)
    }

    data class BackupInfo(
        val filename: String,
        val fullPath: String,
        val sizeBytes: Long,
        val sizeDisplay: String,
        val modifiedTime: Long,
        val dateDisplay: String
    )

    fun listBackups(): List<BackupInfo> {
        val destDir = "/sdcard/Gh0stra1n_Backup"
        val r = SuChannel.run("stat -c \"%s %Y %n\" $destDir/*.tar.gz 2>/dev/null", 10, logCmd = false)
        if (!r.ok || r.out.isBlank()) return emptyList()

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val list = mutableListOf<BackupInfo>()
        for (line in r.out.trim().lines()) {
            val parts = line.trim().split(" ", limit = 3)
            if (parts.size == 3) {
                val size = parts[0].toLongOrNull() ?: 0L
                val timeSec = parts[1].toLongOrNull() ?: 0L
                val path = parts[2].trim()
                if (path.endsWith(".tar.gz")) {
                    val name = path.substringAfterLast('/')
                    val dateStr = sdf.format(java.util.Date(timeSec * 1000L))
                    list.add(BackupInfo(
                        filename = name,
                        fullPath = path,
                        sizeBytes = size,
                        sizeDisplay = fmt(size),
                        modifiedTime = timeSec,
                        dateDisplay = dateStr
                    ))
                }
            }
        }
        return list.sortedByDescending { it.modifiedTime }
    }

    fun backupUpperData(log: (String) -> Unit): Result<String> {
        val destDir = "/sdcard/Gh0stra1n_Backup"
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val archive = "$destDir/gh0stra1n_backup_$timeStamp.tar.gz"
        val b = PartitionTable.BASE_DIR

        AppLogger.i("BACKUP", "=== 开始备份所有 OverlayFS 修改层至 $archive ===")
        log("正在准备备份环境...")
        SuChannel.run("mkdir -p $destDir && chmod 777 $destDir 2>/dev/null", 10)

        val live = livePartitions().toSet()
        val tempMounted = mutableListOf<Triple<String, String, String>>() // (partId, loop, mnt)

        try {
            // 对未挂载但存在镜像的分区进行只读临时挂载以读取 u/ 数据
            for (p in PartitionTable.ALL) {
                if (p.id !in live) {
                    val (imgs, _) = listImages(p)
                    if (imgs.isNotEmpty()) {
                        val upper = imgs.first()
                        val loopRes = attachLoop(upper)
                        if (loopRes.isSuccess) {
                            val loop = loopRes.getOrThrow()
                            val mnt = "$b/mnt_${p.id}"
                            val mountRes = SuChannel.run("mkdir -p $mnt && mount -t ext4 -o ro $loop $mnt && echo OK", 15)
                            if (mountRes.ok && mountRes.out.contains("OK")) {
                                tempMounted.add(Triple(p.id, loop, mnt))
                            } else {
                                SuChannel.run("losetup -d $loop 2>/dev/null", 5)
                            }
                        }
                    }
                }
            }

            val availableTargets = mutableListOf<String>()
            for (p in PartitionTable.ALL) {
                val check = SuChannel.run("test -d $b/mnt_${p.id}/u && echo EXISTS", 5)
                if (check.ok && check.out.contains("EXISTS")) {
                    availableTargets.add("mnt_${p.id}/u")
                }
            }

            if (availableTargets.isEmpty()) {
                AppLogger.w("BACKUP", "未检测到任何已挂载或已配置的修改层(u/)，无需备份")
                return Result.failure(RuntimeException("未检测到任何分区修改层(u/)，请先创建镜像或挂载"))
            }

            val targetsStr = availableTargets.joinToString(" ")
            log("正在计算各文件 SHA-256 校验和...")
            AppLogger.i("BACKUP", "正在生成 SHA256SUMS 清单: $targetsStr")
            val shaRes = SuChannel.run(
                "cd $b && " +
                "rm -f SHA256SUMS && " +
                "find $targetsStr -type f -exec sha256sum {} + > SHA256SUMS 2>&1 && " +
                "echo SHA-OK", 60, logCmd = true)
            if (!shaRes.ok || !shaRes.out.contains("SHA-OK")) {
                AppLogger.e("BACKUP", "生成 SHA-256 清单失败: ${shaRes.out}")
                return Result.failure(RuntimeException("生成校验清单失败: ${shaRes.out.trim()}"))
            }

            val fileCountRes = SuChannel.run("wc -l < $b/SHA256SUMS", 10)
            val count = fileCountRes.out.trim().toIntOrNull() ?: 0
            AppLogger.i("BACKUP", "已计算 $count 个常规文件的 SHA-256 校验和")
            log("已生成 $count 个文件的哈希校验清单，正在打包压缩...")

            val r = SuChannel.run(
                "cd $b && " +
                "tar -czf $archive SHA256SUMS $targetsStr 2>&1 && " +
                "rm -f SHA256SUMS && " +
                "chmod 666 $archive && sync && echo BACKUP-OK", 120, logCmd = true)

            return if (r.ok && r.out.contains("BACKUP-OK")) {
                AppLogger.i("BACKUP", "备份完成: $archive (包含 SHA256SUMS)")
                log("备份成功生成: $archive")
                Result.success(archive)
            } else {
                AppLogger.e("BACKUP", "备份失败: ${r.out}")
                Result.failure(RuntimeException("备份失败: ${r.out.trim()}"))
            }
        } finally {
            SuChannel.run("rm -f $b/SHA256SUMS", 5)
            for ((pid, loop, mnt) in tempMounted) {
                SuChannel.run("umount $mnt 2>/dev/null; losetup -d $loop 2>/dev/null", 10)
            }
        }
    }

    fun restoreUpperData(archivePath: String, log: (String) -> Unit): Result<String> {
        val b = PartitionTable.BASE_DIR
        val tmpDir = "/data/local/tmp/gh0stra1n_restore_tmp"
        AppLogger.i("RESTORE", "=== 开始从备份恢复 OverlayFS 修改层: $archivePath ===")
        log("正在准备隔离解包与校验环境...")

        try {
            SuChannel.run("rm -rf $tmpDir && mkdir -p $tmpDir", 10)

            log("正在解包归档至隔离环境...")
            val extractRes = SuChannel.run("tar -xzf $archivePath -C $tmpDir 2>&1 && echo EXTRACT-OK", 120, logCmd = true)
            if (!extractRes.ok || !extractRes.out.contains("EXTRACT-OK")) {
                AppLogger.e("RESTORE", "解压归档失败: ${extractRes.out}")
                return Result.failure(RuntimeException("解压归档失败: ${extractRes.out.trim()}"))
            }

            log("正在执行防篡改与 SHA-256 完整性自检...")
            val shaFileCheck = SuChannel.run("test -f $tmpDir/SHA256SUMS && echo EXISTS", 5)
            if (!shaFileCheck.ok || !shaFileCheck.out.contains("EXISTS")) {
                val err = "归档文件缺少 SHA256SUMS 校验清单，无法确认文件真实性与完整性，恢复已阻断！"
                AppLogger.e("RESTORE", err)
                return Result.failure(SecurityException(err))
            }

            val shaSizeCheck = SuChannel.run("test -s $tmpDir/SHA256SUMS && echo NON_EMPTY", 5)
            val isNonEmpty = shaSizeCheck.ok && shaSizeCheck.out.contains("NON_EMPTY")
            val actualFilesRes = SuChannel.run("cd $tmpDir && find mnt_*/u -type f 2>/dev/null | wc -l", 10)
            val actualCount = actualFilesRes.out.trim().toIntOrNull() ?: 0

            if (isNonEmpty) {
                val expectedCountRes = SuChannel.run("wc -l < $tmpDir/SHA256SUMS", 10)
                val expectedCount = expectedCountRes.out.trim().toIntOrNull() ?: -1
                if (actualCount != expectedCount) {
                    val err = "归档内文件数量与校验清单不符 (实际 $actualCount 个, 清单记录 $expectedCount 个)！检测到植入文件或文件缺失，恢复已阻断！"
                    AppLogger.e("RESTORE", err)
                    return Result.failure(SecurityException(err))
                }

                val verifyRes = SuChannel.run("cd $tmpDir && sha256sum -c SHA256SUMS 2>&1; echo RC=\$?", 60, logCmd = true)
                val rc = verifyRes.out.substringAfter("RC=", "?").trim()
                if (rc != "0" || verifyRes.out.contains("FAILED")) {
                    val failedDetails = verifyRes.out.lines()
                        .filter { it.contains("FAILED") || it.contains("FAILED open") }
                        .joinToString("\n") { it.trim() }
                    val err = "SHA-256 校验失败！检测到文件被篡改或损坏：\n${failedDetails.ifBlank { verifyRes.out.trim() }}"
                    AppLogger.e("RESTORE", err)
                    return Result.failure(SecurityException(err))
                }
            } else {
                if (actualCount > 0) {
                    val err = "校验清单为空但归档中包含文件，完整性验证失败，恢复已阻断！"
                    AppLogger.e("RESTORE", err)
                    return Result.failure(SecurityException(err))
                }
            }

            AppLogger.i("RESTORE", "完整性校验通过！共校验 $actualCount 个文件，无任何篡改")
            log("完整性校验通过 (已验证 $actualCount 个文件)，开始写入各分区修改层...")

            val live = livePartitions().toSet()
            val restoredPartitions = mutableListOf<String>()

            for (p in PartitionTable.ALL) {
                val partUpperDir = "$tmpDir/mnt_${p.id}/u"
                val hasData = SuChannel.run("test -d $partUpperDir && echo HAS_DIR", 5).out.contains("HAS_DIR")
                if (!hasData) continue

                log("正在还原 [${p.id}] 修改层...")
                AppLogger.i("RESTORE", "[${p.id}] 正在还原 upper 数据...")

                if (p.id in live) {
                    val targetDir = "$b/mnt_${p.id}/u"
                    SuChannel.run("mkdir -p $targetDir", 5)
                    val cpRes = SuChannel.run("cp -a $partUpperDir/. $targetDir/ && echo CP-OK", 60)
                    if (!cpRes.ok || !cpRes.out.contains("CP-OK")) {
                        AppLogger.e("RESTORE", "[${p.id}] LIVE 写入失败: ${cpRes.out}")
                        return Result.failure(RuntimeException("写入 [${p.id}] 修改层失败: ${cpRes.out}"))
                    }
                    restoredPartitions.add(p.id)
                } else {
                    val (imgs, _) = listImages(p)
                    val upper = if (imgs.isNotEmpty()) {
                        imgs.first()
                    } else {
                        val entry = ManifestStore.reconcile(p)
                        val created = createImage(p, entry.defaultSizeMiB)
                        if (created.isFailure) {
                            return Result.failure(created.exceptionOrNull()!!)
                        }
                        created.getOrThrow()
                    }

                    val loopRes = attachLoop(upper)
                    if (loopRes.isFailure) return Result.failure(loopRes.exceptionOrNull()!!)
                    val loop = loopRes.getOrThrow()
                    val mnt = "$b/mnt_${p.id}"
                    val mountRes = SuChannel.run("mkdir -p $mnt && mount -t ext4 $loop $mnt && echo OK", 15)
                    if (!mountRes.ok || !mountRes.out.contains("OK")) {
                        SuChannel.run("losetup -d $loop 2>/dev/null", 5)
                        return Result.failure(RuntimeException("临时挂载 [${p.id}] 镜像失败: ${mountRes.out}"))
                    }

                    try {
                        val targetDir = "$mnt/u"
                        SuChannel.run("mkdir -p $targetDir", 5)
                        val cpRes = SuChannel.run("cp -a $partUpperDir/. $targetDir/ && echo CP-OK", 60)
                        if (!cpRes.ok || !cpRes.out.contains("CP-OK")) {
                            return Result.failure(RuntimeException("写入 [${p.id}] 修改层失败: ${cpRes.out}"))
                        }
                        restoredPartitions.add(p.id)
                    } finally {
                        SuChannel.run("sync && umount $mnt 2>/dev/null; losetup -d $loop 2>/dev/null", 15)
                    }
                }
            }

            SuChannel.run("sync", 10)
            val summary = if (restoredPartitions.isNotEmpty()) {
                "成功恢复 ${restoredPartitions.size} 个分区的修改层 (${restoredPartitions.joinToString(", ")})"
            } else {
                "归档中未包含任何分区的修改层数据"
            }
            AppLogger.i("RESTORE", "恢复完成: $summary")
            log(summary)
            return Result.success(summary)
        } finally {
            SuChannel.run("rm -rf $tmpDir", 10)
        }
    }

    data class PreflightResult(
        val upper: String,
        val images: List<String>
    )

    fun preflight(part: PartitionDef, neededBytes: Long = 0L, log: (String) -> Unit): Result<PreflightResult> {
        var (imgs, _) = listImages(part)
        if (imgs.isEmpty()) {
            val entry = ManifestStore.reconcile(part)
            val size = entry.defaultSizeMiB.coerceIn(ManifestStore.MIN_SIZE_MIB, ManifestStore.MAX_SIZE_MIB)
            val created = createImage(part, size)
            if (created.isFailure) return Result.failure(created.exceptionOrNull()!!)
            log("created ${created.getOrThrow().substringAfterLast('/')} (${size}MiB, manifest)")
            imgs = listImages(part).first
        }
        val upper = imgs.first()
        if (neededBytes <= 0L) {
            return Result.success(PreflightResult(upper, imgs))
        }

        val entry = ManifestStore.getPart(part.id, part.defaultSizeMiB)
        val (total, free, _) = imageStats(upper)
        val limitBytes = if (entry.locked)
            entry.defaultSizeMiB.coerceIn(ManifestStore.MIN_SIZE_MIB, ManifestStore.MAX_SIZE_MIB) * 1024 * 1024
        else ManifestStore.MAX_SIZE_MIB * 1024 * 1024L

        log("${upper.substringAfterLast('/')}: free=${fmt(free)} need=${fmt(neededBytes)} total=${fmt(total)}")
        if (free >= neededBytes) return Result.success(PreflightResult(upper, imgs))

        val upperSize = SuChannel.run("stat -c %s $upper", 10).out.trim().toLongOrNull() ?: upper.sizeOf(entry)
        val growTo = ((upperSize + neededBytes - free + 16 * 1024 * 1024 - 1) /
            (16 * 1024 * 1024)) * (16 * 1024 * 1024)
        if (growTo <= limitBytes && upperSize < limitBytes) {
            val g = growImage(upper, growTo)
            if (g.isFailure) return Result.failure(g.exceptionOrNull()!!)
            log("grow ${upper.substringAfterLast('/')} -> ${fmt(growTo)}")
            val base = upper.substringAfterLast('/')
            val idx = entry.images.indexOfFirst { it.file == base }
            if (idx >= 0) {
                entry.images[idx] = entry.images[idx].copy(size = growTo)
                ManifestStore.putPart(part.id, entry)
            }
            return Result.success(PreflightResult(upper, imgs))
        }
        val newSize = (entry.defaultSizeMiB * 1024 * 1024)
            .coerceAtLeast(growTo.coerceAtMost(limitBytes))
            .coerceAtMost(limitBytes)
            .coerceAtLeast(ManifestStore.MIN_SIZE_MIB * 1024 * 1024)
        val created = createImage(part, newSize / (1024 * 1024))
        if (created.isFailure) return Result.failure(created.exceptionOrNull()!!)
        log("stack-bump: ${created.getOrThrow().substringAfterLast('/')} ($newSize) 成为 upper")
        return Result.success(PreflightResult(created.getOrThrow(), listImages(part).first))
    }

    private fun String.sizeOf(entry: ManifestStore.PartEntry): Long {
        val base = substringAfterLast('/')
        for (img in entry.images) if (img.file == base) return img.size
        return 0L
    }

    private fun fmt(b: Long): String = when {
        b >= 1024 * 1024 * 1024L -> "%.1fGiB".format(b / (1024.0 * 1024 * 1024))
        b >= 1024 * 1024L -> "%.1fMiB".format(b / (1024.0 * 1024))
        else -> "${b / 1024}KiB"
    }

    fun fsck(part: PartitionDef, image: String, enabled: Boolean = true, log: ((String) -> Unit)? = null): String? {
        if (!enabled) {
            AppLogger.i("FSCK", "[${part.id}] 挂载前自动自检已根据设置关闭，跳过自检")
            log?.invoke("fsck: 已跳过 (自动自检未开启)")
            return null
        }
        val imgName = image.substringAfterLast('/')
        val cmd = "/system/bin/e2fsck -p $image"
        AppLogger.i("FSCK", "[${part.id}] 正在执行文件系统自检: $cmd")
        log?.invoke("fsck: 正在自检 $imgName ...")
        var r = SuChannel.run("$cmd 2>&1; echo RC=\$?", 60, logCmd = true)
        var rc = r.out.substringAfter("RC=", "?").trim()
        var outDetail = r.out.substringBefore("RC=").trim().lines().joinToString(" ") { it.trim() }

        if (rc != "0" && rc != "1" && rc != "2") {
            AppLogger.w("FSCK", "[${part.id}] 预检报告需进一步自愈 (RC=$rc)，执行 e2fsck -y 自动修复...")
            val healCmd = "/system/bin/e2fsck -y $image"
            r = SuChannel.run("$healCmd 2>&1; echo RC=\$?", 60, logCmd = true)
            rc = r.out.substringAfter("RC=", "?").trim()
            outDetail = r.out.substringBefore("RC=").trim().lines().joinToString(" ") { it.trim() }
        }

        val err = if (rc == "0" || rc == "1" || rc == "2") null else "e2fsck $rc: $outDetail"
        if (err == null) {
            val statusDesc = if (outDetail.isNotBlank()) outDetail else "clean"
            AppLogger.i("FSCK", "[${part.id}] 文件系统自检通过/已自愈 (RC=$rc): $statusDesc")
            log?.invoke("fsck: $statusDesc (RC=$rc)")
        } else {
            AppLogger.e("FSCK", "[${part.id}] 自检修复异常: $err")
            log?.invoke("fsck: 异常 $err")
        }
        return err
    }

    fun runManualFsck(part: PartitionDef): Pair<Boolean, String> {
        val (imgs, _) = listImages(part)
        if (imgs.isEmpty()) {
            return false to "该分区尚未创建任何镜像文件"
        }
        val upper = imgs.first()
        val imgName = upper.substringAfterLast('/')
        val cmd = "/system/bin/e2fsck -y -v $upper"
        AppLogger.i("FSCK", "[${part.id}] 手动触发文件系统自检修复: $cmd")
        val r = SuChannel.run("$cmd 2>&1; echo RC=\$?", 60, logCmd = true)
        val rc = r.out.substringAfter("RC=", "?").trim()
        val detail = r.out.substringBefore("RC=").trim()
        val ok = rc == "0" || rc == "1" || rc == "2"
        if (ok) {
            AppLogger.i("FSCK", "[${part.id}] 手动自检成功 (RC=$rc): $detail")
        } else {
            AppLogger.e("FSCK", "[${part.id}] 手动自检报错 (RC=$rc): $detail")
        }
        return ok to "执行命令: $cmd\n退出代码: RC=$rc (${if (ok) "正常/已修复" else "异常"})\n\n自检报告:\n${detail.ifBlank { "文件系统状态正常 (Clean)" }}"
    }

    fun remountLivePartitions(noatime: Boolean): Int {
        val live = livePartitions()
        if (live.isEmpty()) return 0
        var count = 0
        val b = PartitionTable.BASE_DIR
        val ext4Opt = if (noatime) "remount,noatime" else "remount,atime,relatime"
        val ovlOpt = if (noatime) "remount,noatime" else "remount,atime,relatime"
        for (id in live) {
            val part = PartitionTable.byId[id] ?: continue
            val mnt = "$b/mnt_${part.id}"
            SuChannel.run("mount -o $ext4Opt $mnt 2>/dev/null", 10)
            SuChannel.run("mount -o $ovlOpt ${part.mountPoint} 2>/dev/null", 10)
            count++
        }
        val strategy = if (noatime) "noatime (已开启闪存寿命保护)" else "relatime (标准模式)"
        AppLogger.i("SETTINGS", "已对当前活跃的 $count 个 OverlayFS 挂载点即时同步挂载策略: $strategy")
        return count
    }

    fun mountStack(
        part: PartitionDef,
        log: (String) -> Unit,
        neededBytes: Long = 0L,
        noatime: Boolean = true,
        autoFsck: Boolean = true
    ): Result<Unit> {
        val strategyDesc = if (noatime) "noatime" else "relatime"
        AppLogger.i("MOUNT", "=== 开始挂载分区 [${part.id}] (${part.mountPoint}) [策略: $strategyDesc, autoFsck=$autoFsck] ===")
        val preRes = preflight(part, neededBytes, log)
        if (preRes.isFailure) {
            AppLogger.e("MOUNT", "[${part.id}] 预检失败: ${preRes.exceptionOrNull()?.message}")
            return Result.failure(preRes.exceptionOrNull()!!)
        }
        val (upper, imgs) = preRes.getOrThrow()
        AppLogger.i("MOUNT", "[${part.id}] 选定 Upper 镜像: ${upper.substringAfterLast('/')}")

        fsck(part, upper, enabled = autoFsck, log = log)?.let {
            AppLogger.e("MOUNT", "[${part.id}] 自检失败，终止挂载: $it")
            return Result.failure(RuntimeException(it))
        }

        val b = PartitionTable.BASE_DIR
        val mnt = "$b/mnt_${part.id}"

        val upperLoopRes = attachLoop(upper)
        if (upperLoopRes.isFailure) {
            AppLogger.e("MOUNT", "[${part.id}] loop 关联失败")
            return Result.failure(upperLoopRes.exceptionOrNull()!!)
        }
        val upperLoop = upperLoopRes.getOrThrow()
        AppLogger.i("MOUNT", "[${part.id}] 环回设备绑定: $upperLoop <- ${upper.substringAfterLast('/')}")
        log("upper $upperLoop <- ${upper.substringAfterLast('/')}")

        val lowers = imgs.drop(1)
        val lowerDirs = mutableListOf<String>()
        val lowerSetupCmds = StringBuilder()
        var n = 0
        for (img in lowers) {
            val loopRes = attachLoop(img)
            if (loopRes.isFailure) return Result.failure(loopRes.exceptionOrNull()!!)
            val loop = loopRes.getOrThrow()
            val d = "$b/low${n}_${part.id}"
            val ext4LowerOpts = if (noatime) "-o ro,noatime" else "-o ro"
            lowerSetupCmds.append("mkdir -p $d && mount -t ext4 $ext4LowerOpts $loop $d && ")
            lowerDirs.add(d)
            AppLogger.i("MOUNT", "[${part.id}] 挂载 lower 镜像: $loop <- ${img.substringAfterLast('/')}")
            log("lower $loop <- ${img.substringAfterLast('/')} (ro)")
            n++
        }

        val lowerdir = (lowerDirs + part.mountPoint).joinToString(":")
        AppLogger.i("MOUNT", "[${part.id}] 执行 OverlayFS 挂载至 ${part.mountPoint}")

        val ext4UpperOpts = if (noatime) "-o noatime" else ""
        val remountNoatime = if (noatime) "mount -o remount,noatime ${part.mountPoint} 2>/dev/null; " else ""

        val pipelineCmd = "mkdir -p $mnt/u $mnt/w && " +
            lowerSetupCmds.toString() +
            "mount -t ext4 $ext4UpperOpts $upperLoop $mnt && " +
            "mount -t overlay ovl_gh0stra1n_${part.id} -o lowerdir=$lowerdir,upperdir=$mnt/u,workdir=$mnt/w ${part.mountPoint} && " +
            remountNoatime +
            "grep ' ${part.mountPoint} ' /proc/mounts | grep -m1 ovl_gh0stra1n"

        val r = SuChannel.run(pipelineCmd, 30, logCmd = true)
        if (!r.ok || !r.out.contains("lowerdir=") || !r.out.contains("upperdir=")) {
            AppLogger.e("MOUNT", "[${part.id}] overlay mount 失败: $r")
            return Result.failure(RuntimeException("overlay mount: $r"))
        }

        val finalStrategy = if (noatime) "noatime (闪存保护)" else "relatime"
        AppLogger.i("MOUNT", "[${part.id}] 挂载验证成功 (LIVE, $finalStrategy)")
        log("overlay LIVE: ${r.out.trim().split(" ").take(3).joinToString(" ")}")
        return Result.success(Unit)
    }

    fun unmountStack(part: PartitionDef, log: (String) -> Unit): Result<Unit> {
        AppLogger.i("UMOUNT", "=== 开始卸载分区 [${part.id}] (${part.mountPoint}) ===")
        val b = PartitionTable.BASE_DIR
        val cmd = "if grep ' ${part.mountPoint} ' /proc/mounts | grep -q ovl_gh0stra1n; then " +
            "umount ${part.mountPoint} 2>/dev/null || umount -l ${part.mountPoint} 2>/dev/null; " +
            "fi; " +
            "for d in $b/mnt_${part.id} $b/low*_${part.id}; do umount \$d 2>/dev/null; done; " +
            "sync; grep -c '${part.id}' /proc/mounts"
        val r = SuChannel.run(cmd, 25, logCmd = true)
        AppLogger.i("UMOUNT", "[${part.id}] 底层 ext4 与 overlay 清理完成 (剩余挂载: ${r.out.trim()})")
        log("residue mounts: ${r.out.trim()}")
        return Result.success(Unit)
    }

    fun livePartitions(): List<String> {
        val r = SuChannel.run(
            "awk '\$3==\"overlay\" && \$1 ~ /ovl/ {print \$2}' /proc/mounts | sort -u", 15)
        if (!r.ok) return emptyList()
        return r.out.trim().lines().mapNotNull { mp ->
            PartitionTable.ALL.firstOrNull { it.mountPoint == mp }?.id
        }
    }

    fun deletePartitionData(part: PartitionDef, log: (String) -> Unit = {}): Result<Unit> {
        AppLogger.i("DELETE", "=== 开始删除分区 [${part.id}] (${part.mountPoint}) 数据 ===")
        log("开始安全清理 ${part.id}...")

        val live = livePartitions()
        if (part.id in live) {
            AppLogger.i("DELETE", "[${part.id}] 当前处于挂载状态，先执行卸载")
            val uRes = unmountStack(part, log)
            if (uRes.isFailure) {
                val err = uRes.exceptionOrNull()?.message ?: "卸载失败"
                AppLogger.e("DELETE", "[${part.id}] 卸载中断，终止删除: $err")
                return Result.failure(RuntimeException("卸载失败: $err"))
            }
        }

        val b = PartitionTable.BASE_DIR
        val cmd = "rm -f $b/${part.id}-*.img && rm -rf $b/mnt_${part.id} $b/work_${part.id} $b/low*_${part.id} && sync"
        val r = SuChannel.run(cmd, 30)
        if (!r.ok) {
            AppLogger.e("DELETE", "[${part.id}] 清理镜像文件失败: ${r.out}")
            return Result.failure(RuntimeException("清理镜像文件失败: ${r.out}"))
        }

        ManifestStore.clearPartImages(part.id)
        AppLogger.i("DELETE", "[${part.id}] 镜像与工作目录删除成功")
        log("${part.id} 镜像文件已删除")
        return Result.success(Unit)
    }

    fun deleteAllPartitionData(log: (String) -> Unit = {}): Result<Unit> {
        AppLogger.i("DELETE", "=== 开始删除全部分区镜像数据 ===")
        log("开始清理全部分区数据...")

        for (p in PartitionTable.ALL) {
            val uRes = unmountStack(p, log)
            if (uRes.isFailure) {
                AppLogger.w("DELETE", "[${p.id}] 卸载遇到警告: ${uRes.exceptionOrNull()?.message}")
            }
        }

        val b = PartitionTable.BASE_DIR
        val cmd = "rm -f $b/*.img && rm -rf $b/mnt_* $b/work_* $b/low*_* && sync"
        val r = SuChannel.run(cmd, 60)
        if (!r.ok) {
            AppLogger.e("DELETE", "清理镜像文件失败: ${r.out}")
            return Result.failure(RuntimeException("清理全部镜像失败: ${r.out}"))
        }

        ManifestStore.clearAllPartImages()
        AppLogger.i("DELETE", "全部分区镜像与缓存目录已完全清除")
        log("全部镜像与工作目录已删除")
        return Result.success(Unit)
    }

    fun formatPartitionData(part: PartitionDef, log: (String) -> Unit = {}): Result<Unit> {
        AppLogger.i("FORMAT", "=== 开始格式化分区 [${part.id}] (${part.mountPoint}) 镜像 ===")
        log("开始安全卸载 ${part.id}...")

        val live = livePartitions()
        if (part.id in live) {
            AppLogger.i("FORMAT", "[${part.id}] 当前处于挂载状态，先执行卸载")
            val uRes = unmountStack(part, log)
            if (uRes.isFailure) {
                val err = uRes.exceptionOrNull()?.message ?: "卸载失败"
                AppLogger.e("FORMAT", "[${part.id}] 卸载中断，终止格式化: $err")
                return Result.failure(RuntimeException("卸载失败: $err"))
            }
        }

        val (imgs, _) = listImages(part)
        val b = PartitionTable.BASE_DIR
        if (imgs.isNotEmpty()) {
            val upper = imgs.first()
            log("正在格式化 ext4 镜像: ${upper.substringAfterLast('/')}")
            AppLogger.i("FORMAT", "正在执行 mke2fs 格式化: $upper")
            val fmtCmd = "/system/bin/mke2fs -F -q -t ext4 -I 256 $upper && " +
                    "rm -rf $b/mnt_${part.id}/* $b/work_${part.id}/* && sync"
            val r = SuChannel.run(fmtCmd, 30)
            if (!r.ok) {
                AppLogger.e("FORMAT", "格式化失败: ${r.out}")
                return Result.failure(RuntimeException("格式化失败: ${r.out}"))
            }
        } else {
            val entry = ManifestStore.reconcile(part)
            val size = entry.defaultSizeMiB.coerceIn(ManifestStore.MIN_SIZE_MIB, ManifestStore.MAX_SIZE_MIB)
            val created = createImage(part, size)
            if (created.isFailure) {
                return Result.failure(created.exceptionOrNull()!!)
            }
        }

        AppLogger.i("FORMAT", "分区 [${part.id}] 格式化完成")
        log("${part.id} 格式化完成，已恢复为干净 ext4 卷")
        return Result.success(Unit)
    }
}
