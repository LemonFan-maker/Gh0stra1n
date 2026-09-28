package com.orionisli.gh0stra1n

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object DeviceInfoHelper {

    private val propCache = ConcurrentHashMap<String, String>()

    fun getSystemProp(key: String): String {
        return propCache[key] ?: propCache.computeIfAbsent(key) { readSystemProp(key) }
    }

    private fun readSystemProp(key: String): String {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val res = method.invoke(null, key, "") as? String
            if (!res.isNullOrBlank()) {
                return res.trim()
            }
        } catch (_: Throwable) {}

        var process: Process? = null
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("getprop", key))
            process = proc
            proc.errorStream.close()
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                val line = reader.readLine()
                if (!line.isNullOrBlank()) {
                    return line.trim()
                }
            }
        } catch (_: Throwable) {} finally {
            process?.waitFor()
        }

        return ""
    }

    fun getMarketName(): String {
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

        val model = Build.MODEL.orEmpty().trim()
        return formatWithBrand(model)
    }

    private fun formatWithBrand(name: String): String {
        val brand = Build.BRAND.orEmpty().trim()
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()

        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return if (brand.isNotEmpty()) brand else manufacturer
        }

        val containsBrand = brand.isNotEmpty() && cleanName.contains(brand, ignoreCase = true)
        val containsManufacturer = manufacturer.isNotEmpty() && cleanName.contains(manufacturer, ignoreCase = true)
        if (containsBrand || containsManufacturer) {
            return cleanName
        }

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

    fun getCodename(): String {
        val device = Build.DEVICE.orEmpty().trim()
        if (device.isNotBlank() && !device.equals("generic", ignoreCase = true)) {
            return device
        }

        val propDevice = getSystemProp("ro.product.device")
        if (propDevice.isNotBlank() && !propDevice.equals("generic", ignoreCase = true)) return propDevice

        val product = Build.PRODUCT.orEmpty().trim()
        if (product.isNotBlank()) return product

        val board = Build.BOARD.orEmpty().trim()
        if (board.isNotBlank()) return board

        return "unknown"
    }

    fun getAndroidVersion(): String {
        val release = Build.VERSION.RELEASE.orEmpty().trim()
        return if (release.isNotEmpty() && !release.equals("REL", ignoreCase = true)) {
            "Android $release"
        } else {
            "Android ${Build.VERSION.SDK_INT}"
        }
    }

    fun getDeviceSummary(): String {
        val marketName = getMarketName()
        val codename = getCodename()
        val os = getAndroidVersion()

        return if (codename.isNotEmpty()) {
            "$marketName ($codename) $os"
        } else {
            "$marketName $os"
        }
    }

    fun getDetailedInfo(): String {
        val market = getMarketName()
        val codename = getCodename()
        val model = Build.MODEL
        val os = getAndroidVersion()
        val api = Build.VERSION.SDK_INT
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return "$market ($codename / $model)\n$os (API $api) $abi"
    }
}
