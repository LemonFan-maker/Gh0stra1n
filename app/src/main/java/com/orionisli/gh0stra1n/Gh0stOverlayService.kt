package com.orionisli.gh0stra1n

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.system.Os
import androidx.core.app.NotificationCompat
import com.orionisli.gh0stra1n.ipc.IGh0stOverlayService

class Gh0stOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "gh0st_overlay_daemon_channel"
        const val NOTIFICATION_ID = 2339
        const val PERMISSION_ACCESS_OVERLAY = "com.orionisli.gh0stra1n.permission.ACCESS_OVERLAY_SERVICE"
    }

    private val binder = object : IGh0stOverlayService.Stub() {

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            enforceCallerAuthorized()
            return super.onTransact(code, data, reply, flags)
        }

        private fun enforceCallerAuthorized() {
            val callingUid = Binder.getCallingUid()
            if (callingUid == Process.myUid()) {
                return
            }

            val hasPermission = checkCallingOrSelfPermission(PERMISSION_ACCESS_OVERLAY) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                return
            }

            val match = packageManager.checkSignatures(callingUid, Process.myUid())
            if (match == PackageManager.SIGNATURE_MATCH) {
                return
            }

            val callerPkgs = packageManager.getPackagesForUid(callingUid)?.joinToString() ?: "UID $callingUid"
            throw SecurityException(
                "Access denied to Gh0stOverlayService: Caller [$callerPkgs] (UID $callingUid) does not hold signature permission or have matching signature."
            )
        }

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
            val res = ImageManager.unmountStack(part)
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

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        startForegroundCompat()
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return true
    }

    private fun startForegroundCompat() {
        try {
            createNotificationChannel()
            val notif = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (e: Throwable) {
            AppLogger.w("Gh0stOverlayService", "startForeground failed: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_daemon_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_daemon_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_daemon_notif_title))
            .setContentText(getString(R.string.overlay_daemon_notif_text))
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
