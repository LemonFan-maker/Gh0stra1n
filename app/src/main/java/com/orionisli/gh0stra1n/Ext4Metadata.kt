package com.orionisli.gh0stra1n

data class Ext4Metadata(
    val totalBlocks: Long = 0L,
    val freeBlocks: Long = 0L,
    val blockSize: Long = 4096L,
    val totalInodes: Long = 0L,
    val freeInodes: Long = 0L,
    val features: List<String> = emptyList(),
    val state: String = "clean",
    val uuid: String = ""
) {
    val usedBlocks: Long get() = (totalBlocks - freeBlocks).coerceAtLeast(0L)
    val usedInodes: Long get() = (totalInodes - freeInodes).coerceAtLeast(0L)

    val totalBytes: Long get() = totalBlocks * blockSize
    val usedBytes: Long get() = usedBlocks * blockSize
    val freeBytes: Long get() = freeBlocks * blockSize

    val blockUsagePct: Double get() = if (totalBlocks > 0) (usedBlocks.toDouble() / totalBlocks * 100.0) else 0.0
    val inodeUsagePct: Double get() = if (totalInodes > 0) (usedInodes.toDouble() / totalInodes * 100.0) else 0.0

    companion object {
        fun parse(tune2fsOutput: String): Ext4Metadata {
            var totalBlocks = 0L
            var freeBlocks = 0L
            var blockSize = 4096L
            var totalInodes = 0L
            var freeInodes = 0L
            val features = mutableListOf<String>()
            var state = "clean"
            var uuid = ""

            for (rawLine in tune2fsOutput.lineSequence()) {
                val line = rawLine.trim()
                val key = line.substringBefore(':').trim()
                val value = line.substringAfter(':', "").trim()

                when {
                    key.equals("Block count", ignoreCase = true) -> totalBlocks = value.toLongOrNull() ?: 0L
                    key.equals("Free blocks", ignoreCase = true) -> freeBlocks = value.toLongOrNull() ?: 0L
                    key.equals("Block size", ignoreCase = true) -> blockSize = value.toLongOrNull() ?: 4096L
                    key.equals("Inode count", ignoreCase = true) -> totalInodes = value.toLongOrNull() ?: 0L
                    key.equals("Free inodes", ignoreCase = true) -> freeInodes = value.toLongOrNull() ?: 0L
                    key.equals("Filesystem features", ignoreCase = true) -> {
                        features.addAll(value.split("\\s+".toRegex()).filter { it.isNotBlank() })
                    }
                    key.equals("Filesystem state", ignoreCase = true) -> state = value
                    key.equals("Filesystem UUID", ignoreCase = true) -> uuid = value
                }
            }

            return Ext4Metadata(
                totalBlocks = totalBlocks,
                freeBlocks = freeBlocks,
                blockSize = blockSize,
                totalInodes = totalInodes,
                freeInodes = freeInodes,
                features = features,
                state = state,
                uuid = uuid
            )
        }
    }
}
