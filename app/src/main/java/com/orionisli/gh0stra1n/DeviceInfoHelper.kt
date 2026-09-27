package com.orionisli.gh0stra1n

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 动态设备与系统环境信息解析工具
 * 自动识别品牌、厂商、商业市场型号、硬件代号 (Codename) 及 Android 版本，替代静态硬编码。
 */
object DeviceInfoHelper {

    /**
     * 读取指定系统属性 (System Property)
     * 优先通过反射读取 android.os.SystemProperties，若受限或异常则尝试通过 getprop 读取
     */
    fun getSystemProp(key: String): String {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val res = method.invoke(null, key, "") as? String
            if (!res.isNullOrBlank()) {
                return res.trim()
            }
        } catch (_: Throwable) {}

        try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val line = reader.readLine()
                if (!line.isNullOrBlank()) {
                    return line.trim()
                }
            }
        } catch (_: Throwable) {}

        return ""
    }

    /**
     * 获取用户友好的机型商业/市场名称 (如 "vivo iQOO Neo9S Pro", "Xiaomi 13 Pro", "Google Pixel 8 Pro")
     */
    fun getMarketName(): String {
        // 1. 尝试各大厂商特定市场名称属性
        val marketProps = listOf(
            "ro.vivo.market.name",
            "ro.product.marketname",
            "ro.vendor.oplus.market.name",
            "ro.product.oplus.market.name",
            "ro.oplus.market.name",
            "ro.product.honor.marketname",
            "ro.config.marketing_name",
            "ro.product.brand.marketname"
        )
        for (prop in marketProps) {
            val market = getSystemProp(prop)
            if (market.isNotBlank()) {
                return formatWithBrand(market)
            }
        }

        // 2. 回退机制：使用 Model 并格式化品牌前缀
        val model = Build.MODEL.orEmpty().trim()
        return formatWithBrand(model)
    }

    /**
     * 若市场名未包含品牌前缀，智能补全品牌前缀
     */
    private fun formatWithBrand(name: String): String {
        val brand = Build.BRAND.orEmpty().trim()
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()

        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return if (brand.isNotEmpty()) brand else manufacturer
        }

        // 如果名称中已经包含了品牌或厂商 (忽略大小写)，直接返回原名
        val containsBrand = brand.isNotEmpty() && cleanName.contains(brand, ignoreCase = true)
        val containsManufacturer = manufacturer.isNotEmpty() && cleanName.contains(manufacturer, ignoreCase = true)
        if (containsBrand || containsManufacturer) {
            return cleanName
        }

        // 选取前缀：如 vivo、iQOO 等特殊大小写品牌保留，普通小写则首字母大写
        val rawPrefix = if (brand.isNotBlank() && !brand.equals("generic", ignoreCase = true)) {
            brand
        } else {
            manufacturer
        }

        val prefix = when {
            rawPrefix.equals("vivo", ignoreCase = true) -> "vivo"
            rawPrefix.equals("iqoo", ignoreCase = true) -> "iQOO"
            rawPrefix.isNotEmpty() -> rawPrefix.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> ""
        }

        return if (prefix.isNotBlank() && !cleanName.startsWith(prefix, ignoreCase = true)) {
            "$prefix $cleanName"
        } else {
            cleanName
        }
    }

    /**
     * 获取设备硬件代号 (Codename)
     * 如 "PD2339", "husky", "fuxi", "taro"
     */
    fun getCodename(): String {
        val device = Build.DEVICE.orEmpty().trim()
        if (device.isNotBlank() && !device.equals("generic", ignoreCase = true)) {
            return device
        }

        val propDevice = getSystemProp("ro.product.device")
        if (propDevice.isNotBlank()) return propDevice

        val product = Build.PRODUCT.orEmpty().trim()
        if (product.isNotBlank()) return product

        val board = Build.BOARD.orEmpty().trim()
        if (board.isNotBlank()) return board

        return "unknown"
    }

    /**
     * 获取 Android 系统主版本
     * 如 "Android 16", "Android 15", "Android 14"
     */
    fun getAndroidVersion(): String {
        val release = Build.VERSION.RELEASE.orEmpty().trim()
        return if (release.isNotEmpty() && !release.equals("REL", ignoreCase = true)) {
            "Android $release"
        } else {
            "Android ${Build.VERSION.SDK_INT}"
        }
    }

    /**
     * 格式化完整的设备芯片摘要信息
     * 格式: "{手机版本} ({codename}) • {系统版本}"
     * 例如: "vivo iQOO Neo9S Pro (PD2339) • Android 16"
     */
    fun getDeviceSummary(): String {
        val marketName = getMarketName()
        val codename = getCodename()
        val os = getAndroidVersion()

        return if (codename.isNotEmpty()) {
            "$marketName ($codename) • $os"
        } else {
            "$marketName • $os"
        }
    }

    /**
     * 获取详细设备信息用于点击复制或展示
     */
    fun getDetailedInfo(): String {
        val market = getMarketName()
        val codename = getCodename()
        val model = Build.MODEL
        val os = getAndroidVersion()
        val api = Build.VERSION.SDK_INT
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return "$market ($codename / $model)\n$os (API $api) • $abi"
    }
}
