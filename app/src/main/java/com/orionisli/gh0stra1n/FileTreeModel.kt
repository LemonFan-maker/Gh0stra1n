package com.orionisli.gh0stra1n

import android.graphics.Color
import android.graphics.RectF

enum class FileType(val color: Int) {
    APK(Color.rgb(37, 99, 235)), // #2563EB
    SO(Color.rgb(124, 58, 237)), // #7C3AED
    DEX(Color.rgb(5, 150, 105)), // #059669
    MEDIA(Color.rgb(217, 119, 6)), // #D97706
    CONFIG(Color.rgb(8, 145, 178)), // #0891B2
    DATABASE(Color.rgb(219, 39, 119)), // #DB2777
    DIRECTORY(Color.rgb(71, 85, 105)), // #475569
    OTHER(Color.rgb(100, 116, 139)); // #64748B

    fun getDisplayName(context: android.content.Context): String = when (this) {
        APK -> context.getString(R.string.file_type_apk)
        SO -> context.getString(R.string.file_type_so)
        DEX -> context.getString(R.string.file_type_dex)
        MEDIA -> context.getString(R.string.file_type_media)
        CONFIG -> context.getString(R.string.file_type_config)
        DATABASE -> context.getString(R.string.file_type_database)
        DIRECTORY -> context.getString(R.string.file_type_directory)
        OTHER -> context.getString(R.string.file_type_other)
    }

    companion object {
        fun fromExtension(ext: String, isDirectory: Boolean): FileType {
            if (isDirectory) return DIRECTORY
            return when (ext.lowercase()) {
                "apk", "apex", "jar", "zip", "tar", "gz", "7z", "tgz" -> APK
                "so", "bin", "elf", "sh", "rc" -> SO
                "dex", "odex", "vdex", "art", "oat" -> DEX
                "ttf", "otf", "png", "jpg", "jpeg", "webp", "svg", "ogg", "mp3", "wav", "mp4" -> MEDIA
                "xml", "json", "prop", "conf", "yaml", "yml", "txt", "csv", "ini", "rc" -> CONFIG
                "db", "sqlite", "journal", "cache" -> DATABASE
                else -> OTHER
            }
        }
    }
}

class FileNode(
    val name: String,
    val path: String,
    var size: Long = 0L,
    val isDirectory: Boolean = false,
    val children: MutableList<FileNode> = mutableListOf(),
    var permissions: String = "",
    var owner: String = "",
    var realDiskPath: String = ""
) {
    var parent: FileNode? = null
    var rect: RectF = RectF()
    var fileCount: Int = if (isDirectory) 0 else 1

    val isHidden: Boolean = name.startsWith(".")

    val extension: String = if (isDirectory) "" else {
        val dot = name.lastIndexOf('.')
        if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else ""
    }

    val fileType: FileType = FileType.fromExtension(extension, isDirectory)

    fun addChild(child: FileNode) {
        child.parent = this
        children.add(child)
    }

    fun recalculate(): Long {
        if (!isDirectory) {
            fileCount = 1
            return size
        }
        var total = 0L
        var count = 0
        for (c in children) {
            total += c.recalculate()
            count += c.fileCount
        }
        size = total
        fileCount = count
        return total
    }

    fun findDeepest(x: Float, y: Float): FileNode? {
        if (!rect.contains(x, y)) return null
        for (c in children) {
            if (c.rect.contains(x, y)) {
                val deeper = c.findDeepest(x, y)
                return deeper ?: c
            }
        }
        return this
    }

    fun getBreadcrumbList(): List<FileNode> {
        val list = mutableListOf<FileNode>()
        var curr: FileNode? = this
        while (curr != null) {
            list.add(0, curr)
            curr = curr.parent
        }
        return list
    }

    fun formattedSize(): String = formatBytes(size)

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
                bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}
