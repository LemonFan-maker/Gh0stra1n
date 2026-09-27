package com.orionisli.gh0stra1n

import android.os.SystemClock

data class ImageAnalysisResult(
    val imagePath: String,
    val metadata: Ext4Metadata,
    val rootNode: FileNode,
    val totalScannedFiles: Int,
    val scanDurationMs: Long,
    val isMountedLive: Boolean = false
)

object ImageAnalyzer {

    fun analyze(part: PartitionDef): Result<ImageAnalysisResult> {
        val startTime = SystemClock.elapsedRealtime()

        val (imgs, _) = ImageManager.listImages(part)
        if (imgs.isEmpty()) {
            return Result.failure(IllegalStateException("No image file found for partition [${part.id}]."))
        }

        val targetImage = imgs.first()
        AppLogger.i("ANALYSIS", ">>> 开始解析分区 [${part.id}] 文件树与镜像元数据: ${targetImage.substringAfterLast('/')}")

        val tuneRes = SuChannel.run("/system/bin/tune2fs -l $targetImage", 15, logCmd = false)
        val metadata = if (tuneRes.ok) {
            Ext4Metadata.parse(tuneRes.out)
        } else {
            Ext4Metadata()
        }

        val mntDir = "${PartitionTable.BASE_DIR}/mnt_${part.id}"
        val inspectDir = "${PartitionTable.BASE_DIR}/inspect_${part.id}"
        val isMntActive = SuChannel.run("mount | grep -q '$mntDir' && echo YES", 5, logCmd = false).out.contains("YES")
        val baseDir = if (isMntActive) mntDir else inspectDir

        val cmd = if (isMntActive) {
            """
                sync
                /system/bin/toybox find $mntDir -printf "%s\t%M\t%u\t%g\t%P\n"
            """.trimIndent()
        } else {
            """
                mkdir -p $inspectDir
                DEV=$(losetup -f --show $targetImage)
                if [ -n "${'$'}DEV" ]; then
                    mount -t ext4 -o ro ${'$'}DEV $inspectDir
                    /system/bin/toybox find $inspectDir -printf "%s\t%M\t%u\t%g\t%P\n"
                    umount $inspectDir 2>/dev/null
                    losetup -d ${'$'}DEV 2>/dev/null
                fi
                rmdir $inspectDir 2>/dev/null
            """.trimIndent()
        }

        val scanRes = SuChannel.run(cmd, 60, logCmd = false)
        if (!scanRes.ok && scanRes.out.isBlank()) {
            return Result.failure(RuntimeException("Failed to scan partition image: ${scanRes.out}"))
        }

        val rootNode = FileNode(name = "/", path = "/", isDirectory = true, permissions = "drwxr-xr-x", owner = "root:root", realDiskPath = baseDir)
        val dirMap = mutableMapOf<String, FileNode>()
        dirMap[""] = rootNode
        dirMap["/"] = rootNode

        var scannedCount = 0

        for (rawLine in scanRes.out.lineSequence()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            val parts = line.split('\t')
            if (parts.size < 5) continue

            val sizeBytes = parts[0].toLongOrNull() ?: 0L
            val modeStr = parts[1]
            val user = parts[2]
            val group = parts[3]
            val relPath = parts[4].trim()

            if (relPath.isEmpty()) {
                rootNode.permissions = modeStr
                rootNode.owner = "$user:$group"
                continue
            }

            val isDir = modeStr.startsWith("d")
            scannedCount++

            val segments = relPath.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) continue

            // 逐级确保父目录存在
            var currentParent = rootNode
            var accumulatedPath = ""

            for (i in 0 until segments.size - 1) {
                val seg = segments[i]
                accumulatedPath = if (accumulatedPath.isEmpty()) seg else "$accumulatedPath/$seg"

                currentParent = dirMap.getOrPut(accumulatedPath) {
                    val newDir = FileNode(name = seg, path = accumulatedPath, isDirectory = true)
                    currentParent.addChild(newDir)
                    newDir
                }
            }

            // 当前叶子节点/最终目录
            val leafName = segments.last()
            val fullLeafPath = if (accumulatedPath.isEmpty()) leafName else "$accumulatedPath/$leafName"

            if (isDir) {
                val dirNode = dirMap.getOrPut(fullLeafPath) {
                    val newDir = FileNode(name = leafName, path = fullLeafPath, isDirectory = true)
                    currentParent.addChild(newDir)
                    newDir
                }
                dirNode.permissions = modeStr
                dirNode.owner = "$user:$group"
                dirNode.realDiskPath = "$baseDir/$fullLeafPath"
            } else {
                val fileNode = FileNode(
                    name = leafName,
                    path = fullLeafPath,
                    size = sizeBytes,
                    isDirectory = false,
                    permissions = modeStr,
                    owner = "$user:$group",
                    realDiskPath = "$baseDir/$fullLeafPath"
                )
                currentParent.addChild(fileNode)
            }
        }

        rootNode.recalculate()

        val duration = SystemClock.elapsedRealtime() - startTime
        AppLogger.i("ANALYSIS", "镜像文件系统解析完成: 耗时 ${duration}ms, 扫描 $scannedCount 个条目 (包含隐藏项), 根目录聚合: ${rootNode.formattedSize()}")

        return Result.success(
            ImageAnalysisResult(
                imagePath = targetImage,
                metadata = metadata,
                rootNode = rootNode,
                totalScannedFiles = scannedCount,
                scanDurationMs = duration,
                isMountedLive = isMntActive
            )
        )
    }

    fun readFilePreview(part: PartitionDef, relPath: String, maxBytes: Int = 32768): Pair<Boolean, String> {
        val mntDir = "${PartitionTable.BASE_DIR}/mnt_${part.id}"
        val isMntActive = SuChannel.run("mount | grep -q '$mntDir' && echo YES", 5, logCmd = false).out.contains("YES")
        val cleanPath = relPath.removePrefix("/")

        val rawOutput = if (isMntActive) {
            SuChannel.run("head -c $maxBytes '$mntDir/$cleanPath'", 10, logCmd = false).out
        } else {
            val (imgs, _) = ImageManager.listImages(part)
            if (imgs.isEmpty()) return Pair(false, "Error: Image file not found for this partition")
            val targetImage = imgs.first()
            val inspectDir = "${PartitionTable.BASE_DIR}/inspect_${part.id}"
            val cmd = """
                mkdir -p $inspectDir
                DEV=$(losetup -f --show $targetImage)
                if [ -n "${'$'}DEV" ]; then
                    mount -t ext4 -o ro ${'$'}DEV $inspectDir
                    head -c $maxBytes "$inspectDir/$cleanPath"
                    umount $inspectDir 2>/dev/null
                    losetup -d ${'$'}DEV 2>/dev/null
                fi
                rmdir $inspectDir 2>/dev/null
            """.trimIndent()
            SuChannel.run(cmd, 15, logCmd = false).out
        }

        if (rawOutput.isEmpty()) return Pair(true, "(empty file, 0 bytes)")

        // 检测二进制内容
        val isBinary = rawOutput.any { it == '\u0000' || (it.code < 32 && it != '\n' && it != '\r' && it != '\t') }

        return if (isBinary) {
            val bytes = rawOutput.toByteArray(Charsets.ISO_8859_1)
            val hexDump = bytes.take(256).chunked(16).mapIndexed { idx, chunk ->
                val offset = "%04x".format(idx * 16)
                val hex = chunk.joinToString(" ") { "%02x".format(it) }.padEnd(48)
                val ascii = chunk.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
                "$offset: $hex |$ascii|"
            }.joinToString("\n")
            Pair(false, "[Binary Hex Dump · 256 bytes max]\n\n$hexDump")
        } else {
            Pair(true, rawOutput)
        }
    }
}
