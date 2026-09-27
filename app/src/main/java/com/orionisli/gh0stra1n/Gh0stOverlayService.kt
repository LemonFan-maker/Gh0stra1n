package com.orionisli.gh0stra1n

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.system.Os
import com.orionisli.gh0stra1n.ipc.IGh0stOverlayService

class Gh0stOverlayService : Service() {

    private val binder = object : IGh0stOverlayService.Stub() {
        override fun isRootAlive(): Boolean {
            return SuChannel.probeRoot()
        }

        override fun getPartitionStatus(partitionName: String?): Int {
            val id = (partitionName ?: return -1).trim().removePrefix("/")
            val part = PartitionTable.byId[id] ?: return -1
            return if (part.id in ImageManager.livePartitions()) 1 else 0
        }

        override fun getUpperDir(partitionName: String?): String {
            val id = (partitionName ?: return "").trim().removePrefix("/")
            if (id.isEmpty()) return ""
            return "${PartitionTable.BASE_DIR}/mnt_$id/u"
        }

        override fun getPartitionFreeSpace(partitionName: String?): Long {
            val id = (partitionName ?: return 0L).trim().removePrefix("/")
            val part = PartitionTable.byId[id] ?: return 0L
            val mntPath = "${PartitionTable.BASE_DIR}/mnt_${part.id}"

            try {
                val stat = Os.statvfs(mntPath)
                val free = stat.f_bavail * stat.f_frsize
                if (free > 0L) return free
            } catch (_: Throwable) {}

            try {
                val r = SuChannel.run("stat -f -c '%a %S' $mntPath 2>/dev/null", timeoutSec = 5, logCmd = false)
                if (r.ok && r.out.isNotBlank()) {
                    val parts = r.out.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val blocks = parts[0].toLongOrNull() ?: 0L
                        val size = parts[1].toLongOrNull() ?: 0L
                        val bytes = blocks * size
                        if (bytes > 0L) return bytes
                    }
                }
            } catch (_: Throwable) {}

            try {
                val dfRes = SuChannel.run("df -k $mntPath 2>/dev/null | tail -n 1", timeoutSec = 5, logCmd = false)
                if (dfRes.ok && dfRes.out.isNotBlank()) {
                    val cols = dfRes.out.trim().split("\\s+".toRegex())
                    if (cols.size >= 4) {
                        val availKb = cols[3].toLongOrNull()
                        if (availKb != null && availKb > 0L) return availKb * 1024L
                    }
                }
            } catch (_: Throwable) {}

            try {
                val tuneRes = SuChannel.run(
                    "img=\$(ls -t ${PartitionTable.BASE_DIR}/${part.id}-*.img 2>/dev/null | head -1); [ -n \"\$img\" ] && /system/bin/tune2fs -l \"\$img\" 2>/dev/null | awk '/Block size:/ {bs=\$3} /Free blocks:/ {fb=\$3} END {printf \"%s\", fb*bs}'",
                    timeoutSec = 5,
                    logCmd = false
                )
                val bytes = tuneRes.out.trim().toLongOrNull()
                if (bytes != null && bytes > 0L) return bytes
            } catch (_: Throwable) {}

            return 0L
        }

        override fun mountPartition(partitionName: String?): Boolean {
            val id = (partitionName ?: return false).trim().removePrefix("/")
            val part = PartitionTable.byId[id] ?: return false
            if (part.id in ImageManager.livePartitions()) {
                return true
            }
            val settings = SettingsStore(this@Gh0stOverlayService)
            val res = ImageManager.mountStack(
                part = part,
                log = {},
                neededBytes = 0L,
                noatime = settings.noatimeMount,
                autoFsck = settings.autoFsck
            )
            if (res.isSuccess) {
                ManifestStore.recordLastMount(ImageManager.livePartitions(), "LIVE")
                return true
            }
            return false
        }

        override fun unmountPartition(partitionName: String?): Boolean {
            val id = (partitionName ?: return false).trim().removePrefix("/")
            val part = PartitionTable.byId[id] ?: return false
            val res = ImageManager.unmountStack(part) {}
            if (res.isSuccess) {
                val live = ImageManager.livePartitions()
                ManifestStore.recordLastMount(live, if (live.isEmpty()) "DETACH" else "LIVE")
                return true
            }
            return false
        }

        override fun syncStorage() {
            SuChannel.run("sync")
        }

        override fun restartZygote(): Boolean {
            return SuChannel.run("setprop ctl.restart zygote").ok
        }

        override fun executeRootCommand(command: String?): Boolean {
            if (command.isNullOrBlank()) return false
            return SuChannel.run(command).ok
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}
