package com.orionisli.gh0stra1n

import java.util.concurrent.TimeUnit

data class SuResult(val exit: Int, val out: String) {
    val ok: Boolean get() = exit == 0
    override fun toString(): String = "[exit=$exit] $out".trim()
}

object SuChannel {
    const val SU = "/data/local/tmp/su"

    fun run(cmd: String, timeoutSec: Int = 30, logCmd: Boolean = true): SuResult = try {
        val p = ProcessBuilder(SU, "-c", cmd)
            .redirectErrorStream(true).start()
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
        AppLogger.i("SU", "正在探测 Root 特权通道 (/data/local/tmp/su -c id)...")
        val r = run("id", 10, logCmd = true)
        val ok = r.ok && r.out.contains("uid=0(root)")
        if (ok) {
            AppLogger.i("SU", "Root 探测成功: uid=0(root)")
        } else {
            AppLogger.w("SU", "Root 探测失败或未授权 (exit=${r.exit})")
        }
        return ok
    }
}
