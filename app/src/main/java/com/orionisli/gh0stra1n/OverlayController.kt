package com.orionisli.gh0stra1n

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

enum class State { BOOT, NO_ROOT, READY, MOUNTING, LIVE, FAILED, TEARDOWN, DETACH }

class OverlayController(
    private val listener: (State, String) -> Unit,
    private val settings: SettingsStore? = null,
) {

    @Volatile var state: State = State.BOOT
        private set

    private var running = false
    @Volatile
    private var detached = false

    fun detach() {
        detached = true
    }


    private fun setState(s: State, detail: String = "") {
        state = s
        if (!detached) listener(s, detail)
    }

    private fun settle() {
        val live = ImageManager.livePartitions()
        if (live.isEmpty()) {
            setState(State.DETACH, "")
            ManifestStore.recordLastMount(emptyList(), "DETACH")
        } else {
            setState(State.LIVE, "")
            ManifestStore.recordLastMount(live, "LIVE")
        }
    }

    fun boot() {
        if (running) return
        running = true
        Thread {
            AppLogger.i("INIT", "Gh0stra1n系统初始化启动")
            AppLogger.i("SYS", "宿主：${DeviceInfoHelper.getDeviceSummary()}")
            if (!SuChannel.probeRoot()) {
                AppLogger.e("INIT", "Root特权通道不可用，请确认su授权")
                setState(State.NO_ROOT, "")
                running = false
                return@Thread
            }
            AppLogger.i("SYS", "Root授权方案：${SuChannel.rootProvider}")
            val live = ImageManager.livePartitions()
            if (live.isNotEmpty()) {
                AppLogger.i("INIT", "检测到已有活跃OverlayFS挂载：${live.joinToString()}")
                setState(State.LIVE, "")
            } else {
                val auto = settings?.bootAutoMount ?: false
                val (lastIds, lastState) = ManifestStore.lastMountState()
                AppLogger.i("INIT", "当前分区未挂载(开机自动挂载=$auto，上次记录=$lastState)")
                if (!auto) {
                    AppLogger.w("CONFIG", "开机自动恢复挂载未启用，系统当前处于冷备离线状态")
                }
                if (auto && lastState == "LIVE" && lastIds.isNotEmpty()) {
                    val validLastIds = lastIds.filter { id ->
                        PartitionTable.byId[id]?.takeIf { ImageManager.hasImage(it) } != null
                    }
                    if (validLastIds.isNotEmpty()) {
                        AppLogger.i("INIT", "根据策略恢复上次挂载：${validLastIds.joinToString()}")
                        setState(State.MOUNTING, "")
                        doMount(validLastIds)
                    } else {
                        AppLogger.w("INIT", "上次挂载记录中的分区镜像已不存在，跳过自动恢复挂载")
                        setState(State.READY, "")
                    }
                } else {
                    setState(State.READY, "")
                }
            }
            running = false
        }.start()
    }

    fun mountAll() {
        if (running) return
        running = true
        Thread {
            AppLogger.i("ACTION", "挂载分区OverlayFS")
            if (state == State.NO_ROOT) {
                AppLogger.i("MOUNT", "重新检测Root通道...")
                if (!SuChannel.probeRoot()) {
                    AppLogger.e("MOUNT", "Root通道不可用，请在授权管理器中授权")
                    setState(State.NO_ROOT, "")
                    running = false
                    return@Thread
                }
            }
            val targetParts = PartitionTable.ALL.filter { ImageManager.hasImage(it) }
            if (targetParts.isEmpty()) {
                AppLogger.w("MOUNT", "未检测到任何已创建的分区镜像，终止挂载，请先创建镜像")
                setState(State.READY, "未检测到镜像")
                running = false
                return@Thread
            }
            AppLogger.i("MOUNT", "准备挂载已创建镜像的分区${targetParts.size}个：${targetParts.map { it.id }.joinToString()}")
            setState(State.MOUNTING, "")
            doMount(targetParts.map { it.id })
            running = false
        }.start()
    }

    private fun doMount(ids: List<String>) {
        val live = ImageManager.livePartitions().toSet()
        val succeeded = Collections.synchronizedList(mutableListOf<String>())
        val noatime = settings?.noatimeMount ?: true
        val autoFsck = settings?.autoFsck ?: true
        val validPartitions = ids.mapNotNull { PartitionTable.byId[it] }

        if (validPartitions.isEmpty()) {
            setState(State.READY, "")
            return
        }

        var firstError: String? = null
        val poolSize = minOf(validPartitions.size, 5)
        val executor = Executors.newFixedThreadPool(poolSize)
        val latch = CountDownLatch(validPartitions.size)

        for (p in validPartitions) {
            executor.execute {
                try {
                    if (p.id in live) {
                        AppLogger.i("MOUNT", "${p.id}已处于挂载状态，同步挂载策略(noatime=$noatime)")
                        val b = PartitionTable.BASE_DIR
                        val mnt = "$b/mnt_${p.id}"
                        val remountOpt = if (noatime) "remount,noatime" else "remount,atime,relatime"
                        SuChannel.run("mount -o $remountOpt $mnt 2>/dev/null", 10)
                        SuChannel.run("mount -o $remountOpt ${p.mountPoint} 2>/dev/null", 10)
                        succeeded.add(p.id)
                        return@execute
                    }
                    val mntOk = ImageManager.mountStack(
                        p,
                        neededBytes = 0L,
                        noatime = noatime,
                        autoFsck = autoFsck
                    )
                    if (mntOk.isFailure) {
                        val errMsg = mntOk.exceptionOrNull()?.message ?: "error"
                        AppLogger.e("MOUNT", "分区${p.id}挂载失败：$errMsg")
                        synchronized(succeeded) {
                            if (firstError == null) firstError = "${p.id}: $errMsg"
                        }
                    } else {
                        succeeded.add(p.id)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await()
        } catch (_: InterruptedException) {}
        executor.shutdown()

        if (firstError != null) {
            setState(State.FAILED, firstError!!)
            return
        }

        AppLogger.i("MOUNT", "全部分区挂载完成：${succeeded.joinToString()}")
        setState(State.LIVE, "")
        ManifestStore.recordLastMount(succeeded.toList(), "LIVE")
    }

    fun unmountAll() {
        if (running) return
        running = true
        Thread {
            AppLogger.i("ACTION", "卸载全部分区")
            setState(State.TEARDOWN, "")
            doUnmountAll()
            running = false
        }.start()
    }

    private fun doUnmountAll(): Boolean {
        val live = ImageManager.livePartitions()
        if (live.isEmpty()) {
            AppLogger.i("UMOUNT", "当前无活跃OverlayFS挂载")
            setState(State.DETACH, "")
            ManifestStore.recordLastMount(emptyList(), "DETACH")
            return true
        }

        val validParts = live.mapNotNull { PartitionTable.byId[it] }
        var firstError: String? = null
        val poolSize = minOf(validParts.size, 5)
        val executor = Executors.newFixedThreadPool(poolSize)
        val latch = CountDownLatch(validParts.size)

        for (p in validParts) {
            executor.execute {
                try {
                    val r = ImageManager.unmountStack(p)
                    if (r.isFailure) {
                        val errMsg = r.exceptionOrNull()?.message ?: "error"
                        AppLogger.e("UMOUNT", "分区${p.id}卸载失败：$errMsg")
                        synchronized(validParts) {
                            if (firstError == null) firstError = "${p.id}: $errMsg"
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await()
        } catch (_: InterruptedException) {}
        executor.shutdown()

        if (firstError != null) {
            setState(State.FAILED, firstError!!)
            return false
        }

        AppLogger.i("UMOUNT", "全部OverlayFS分区卸载完成")
        settle()
        return true
    }

    fun rebootWithTeardown() {
        if (running) return
        running = true
        Thread {
            val auto = true
            AppLogger.i("ACTION", "重启设备")
            if (auto) {
                setState(State.TEARDOWN, "")
                val ok = doUnmountAll()
                if (!ok) {
                    runCatching {
                        AppLogger.w("REBOOT", "卸载中断，暂未执行重启")
                        setState(State.FAILED, "Unmount interrupted")
                    }
                    running = false
                    return@Thread
                }
            } else {
                setState(State.TEARDOWN, "")
            }
            AppLogger.i("REBOOT", "正在发送reboot指令...")
            setState(State.TEARDOWN, "")
            val r = SuChannel.run("reboot", 15)
            if (!r.ok) {
                AppLogger.e("REBOOT", "reboot命令失败：${r.out}")
                setState(State.FAILED, "Reboot failed:${r.out}")
            }
            running = false
        }.start()
    }

    fun mountPartition(part: PartitionDef) {
        if (running) return
        running = true
        Thread {
            AppLogger.i("ACTION", "挂载单个分区${part.id}")
            if (state == State.NO_ROOT) {
                AppLogger.i("MOUNT", "重新检测Root通道...")
                if (!SuChannel.probeRoot()) {
                    AppLogger.e("MOUNT", "Root通道仍不可用，请在授权管理器中授权")
                    setState(State.NO_ROOT, "")
                    running = false
                    return@Thread
                }
            }
            if (!ImageManager.hasImage(part)) {
                AppLogger.w("MOUNT", "分区${part.id}镜像未初始化，跳过挂载，请先创建镜像")
                setState(State.READY, "分区${part.id}镜像未创建")
                running = false
                return@Thread
            }
            setState(State.MOUNTING, "")
            val noatime = settings?.noatimeMount ?: true
            val autoFsck = settings?.autoFsck ?: true
            val mntOk = ImageManager.mountStack(
                part,
                neededBytes = 0L,
                noatime = noatime,
                autoFsck = autoFsck
            )
            if (mntOk.isFailure) {
                val errMsg = mntOk.exceptionOrNull()?.message ?: "error"
                AppLogger.e("MOUNT", "分区${part.id}挂载失败：$errMsg")
                setState(State.FAILED, "${part.id}: $errMsg")
            } else {
                AppLogger.i("MOUNT", "分区${part.id}挂载成功")
                settle()
            }
            running = false
        }.start()
    }

    fun unmountPartition(part: PartitionDef) {
        if (running) return
        running = true
        Thread {
            AppLogger.i("ACTION", "卸载单个分区${part.id}")
            setState(State.TEARDOWN, "")
            val r = ImageManager.unmountStack(part)
            if (r.isFailure) {
                val errMsg = r.exceptionOrNull()?.message ?: "error"
                AppLogger.e("UMOUNT", "分区${part.id}卸载失败：$errMsg")
                setState(State.FAILED, "${part.id}: $errMsg")
            } else {
                AppLogger.i("UMOUNT", "分区${part.id}卸载成功")
                settle()
            }
            running = false
        }.start()
    }

    fun deletePartition(part: PartitionDef) {
        if (running) return
        running = true
        Thread {
            AppLogger.i("ACTION", "删除分区镜像${part.id}")
            setState(State.TEARDOWN, "")
            val r = ImageManager.deletePartitionData(part)
            if (r.isFailure) {
                val errMsg = r.exceptionOrNull()?.message ?: "error"
                AppLogger.e("DELETE", "分区${part.id}删除失败：$errMsg")
                setState(State.FAILED, "${part.id}: $errMsg")
            } else {
                AppLogger.i("DELETE", "分区${part.id}镜像与数据已删除")
                settle()
            }
            running = false
        }.start()
    }

    fun deleteAllPartitions() {
        if (running) return
        running = true
        Thread {
            AppLogger.i("ACTION", "删除所有分区镜像")
            setState(State.TEARDOWN, "")
            val r = ImageManager.deleteAllPartitionData()
            if (r.isFailure) {
                val errMsg = r.exceptionOrNull()?.message ?: "error"
                AppLogger.e("DELETE", "全部分区删除失败：$errMsg")
                setState(State.FAILED, "error: $errMsg")
            } else {
                AppLogger.i("DELETE", "全部分区镜像与数据已彻底删除")
                settle()
            }
            running = false
        }.start()
    }

    fun formatPartition(part: PartitionDef) {
        if (running) return
        running = true
        Thread {
            AppLogger.i("ACTION", "格式化分区镜像${part.id}")
            setState(State.TEARDOWN, "")
            val r = ImageManager.formatPartitionData(part)
            if (r.isFailure) {
                val errMsg = r.exceptionOrNull()?.message ?: "error"
                AppLogger.e("FORMAT", "分区${part.id}格式化失败：$errMsg")
                setState(State.FAILED, "${part.id}: $errMsg")
            } else {
                AppLogger.i("FORMAT", "分区${part.id}镜像已成功格式化")
                settle()
            }
            running = false
        }.start()
    }
}
