package com.orionisli.gh0stra1n

import java.util.concurrent.TimeUnit

data class SuResult(val exit: Int, val out: String) {
    val ok: Boolean get() = exit == 0
    override fun toString(): String = "[exit=$exit] $out".trim()
}

object SuChannel {
    const val GH0ST_SU = "/data/local/tmp/su"

    private val CANDIDATES = listOf(
        "/system_ext/bin/su",
        "/product/bin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/tmp/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/data/adb/magisk/su",
        "/sbin/su",
        "/system/sbin/su",
        "/odm/bin/su",
        "/vendor/bin/su",
        "su"
    )

    @Volatile
    var activeSu: String = "su"
        private set

    @Volatile
    var rootProvider: String = "未探测"
        private set

    val SU: String get() = activeSu

    fun run(cmd: String, timeoutSec: Int = 30, logCmd: Boolean = true): SuResult {
        var res = runWithBinary(activeSu, cmd, timeoutSec, logCmd)
        if (res.exit == -2 && res.out.contains("exception") && probeRoot()) {
            res = runWithBinary(activeSu, cmd, timeoutSec, logCmd)
        }
        return res
    }

    fun runWithBinary(binary: String, cmd: String, timeoutSec: Int = 30, logCmd: Boolean = true): SuResult = try {
        val p = ProcessBuilder(binary, "-c", cmd)
            .redirectErrorStream(true)
            .start()
        val res = if (!p.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS)) {
            p.destroyForcibly()
            SuResult(-1, "<timeout ${timeoutSec}s>")
        } else {
            SuResult(p.exitValue(), p.inputStream.bufferedReader().readText())
        }
        if (logCmd) {
            AppLogger.su(cmd, res.exit, res.out)
        }
        res
    } catch (e: Exception) {
        val res = SuResult(-2, "exception: ${e.message}")
        if (logCmd) {
            AppLogger.su(cmd, res.exit, res.out)
        }
        res
    }

    fun probeRoot(): Boolean {
        AppLogger.i("SU", "正在探测 Root 特权通道...")
        for (candidate in CANDIDATES) {
            try {
                val r = runWithBinary(candidate, "id", timeoutSec = 8, logCmd = false)
                if (r.ok && r.out.contains("uid=0")) {
                    activeSu = candidate
                    rootProvider = identifyRootProvider(candidate)
                    AppLogger.i("SU", "Root 特权通道就绪: $rootProvider (命令: $activeSu, 输出: ${r.out.trim()})")
                    return true
                }
            } catch (_: Exception) {
            }
        }
        AppLogger.w("SU", "Root 探测失败或未授权：遍历 su 路径均未获得 root (uid=0) 权限，请在 Magisk / KernelSU / APatch 中授权")
        return false
    }

    private fun identifyRootProvider(binary: String): String {
        val magiskVer = runWithBinary(binary, "magisk -v 2>/dev/null", timeoutSec = 3, logCmd = false)
        if (magiskVer.ok && magiskVer.out.isNotBlank()) {
            return "Magisk (${magiskVer.out.trim()})"
        }
        val ksuCheck = runWithBinary(binary, "if [ -d /data/adb/ksu ] || [ -f /data/adb/ksu/bin/su ]; then echo KSU; fi", timeoutSec = 3, logCmd = false)
        if (ksuCheck.ok && ksuCheck.out.contains("KSU")) {
            val ksuVer = runWithBinary(binary, "ksud -V 2>/dev/null", timeoutSec = 3, logCmd = false)
            val verDesc = if (ksuVer.ok && ksuVer.out.isNotBlank()) " ${ksuVer.out.trim()}" else ""
            return "KernelSU$verDesc"
        }
        val apCheck = runWithBinary(binary, "if [ -d /data/adb/ap ] || [ -f /data/adb/ap/bin/su ]; then echo APATCH; fi", timeoutSec = 3, logCmd = false)
        if (apCheck.ok && apCheck.out.contains("APATCH")) {
            return "APatch"
        }
        if (binary == "/data/local/tmp/su") {
            return "Gh0st Local Root (/data/local/tmp/su)"
        }
        val suVer = runWithBinary(binary, "$binary -v 2>/dev/null", timeoutSec = 3, logCmd = false)
        if (suVer.ok && suVer.out.isNotBlank()) {
            return "Root ($binary, ${suVer.out.trim()})"
        }
        return "通用 Root ($binary)"
    }
}
