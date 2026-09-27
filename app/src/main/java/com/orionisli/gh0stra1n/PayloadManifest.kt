package com.orionisli.gh0stra1n

object PayloadManifest {

    data class Payload(val path: String, val contentHex: String, val description: String)

    val PAYLOADS = listOf(
        Payload("/system/beef.txt", "0xdeadbeef", "安装探针 (已 sync: R-OVL6 断电保留)"),
    )

    fun installAll(log: (String) -> Unit): Boolean {
        var allOk = true
        for (pl in PAYLOADS) {
            AppLogger.i("PAYLOAD", "开始注入载荷文件: ${pl.path} (数据: ${pl.contentHex})")
            val partId = pl.path.trimStart('/').substringBefore('/')
            if (!ImageManager.livePartitions().contains(partId)) {
                AppLogger.w("PAYLOAD", "目标分区 [/$partId] 尚未进入 OverlayFS 活跃挂载状态，写入将直接命中只读底层")
            }
            val dir = pl.path.substringBeforeLast('/')
            val r = SuChannel.run(
                "mkdir -p $dir && " +
                "printf '${pl.contentHex}' > ${pl.path} && " +
                "sync && " +
                "SIZE=\$(stat -c%s ${pl.path}) && " +
                "CONTENT=\$(od -An -tx1 ${pl.path} | tr -d ' \\n') && " +
                "echo INFO size=\$SIZE content=\$CONTENT", 30, logCmd = true)
            val info = r.out.trim()
            log("${pl.path}: ${if (r.ok) info else r.out}")
            if (!r.ok) {
                AppLogger.e("PAYLOAD", "注入失败: ${r.out}")
                allOk = false
                continue
            }
            if (!info.contains("content=deadbeef")) {
                allOk = false
                AppLogger.e("PAYLOAD", "校验失败: 未匹配到 0xdeadbeef ($info)")
                log("manifest 核对失败")
            } else {
                AppLogger.i("PAYLOAD", "载荷校验成功: 0xdeadbeef 已落盘生效")
            }
        }
        return allOk
    }

    fun estimatedBytes(marginMiB: Long = 64): Long {
        var sum = 0L
        for (pl in PAYLOADS) sum += pl.contentHex.length / 2
        return sum + marginMiB * 1024 * 1024
    }

    fun probeStatus(path: String = "/system/beef.txt"): Pair<Boolean, String> {
        val r = SuChannel.run("stat -c '%s %n' $path 2>&1", 15, logCmd = false)
        val ok = r.ok && !r.out.contains("No such file") && !r.out.contains("not found")
        return if (ok) Pair(true, r.out.trim()) else Pair(false, r.out.trim())
    }

    fun status(log: (String) -> Unit) {
        for (pl in PAYLOADS) {
            val (ok, out) = probeStatus(pl.path)
            log(if (ok) "${pl.path}: $out" else "${pl.path}: -")
        }
    }
}
