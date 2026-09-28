package com.orionisli.gh0stra1n

data class PartitionDef(
    val id: String,
    val mountPoint: String,
    val defaultSizeMiB: Long,
)

object PartitionTable {
    const val BASE_DIR = "/data/local/gh0stra1n-overlayfs"

    val ALL = listOf(
        PartitionDef("system", "/system", 1024L),
        PartitionDef("vendor", "/vendor", 512L),
        PartitionDef("product", "/product", 256L),
        PartitionDef("system_ext", "/system_ext", 256L),
        PartitionDef("odm", "/odm", 256L),
    )

    val byId: Map<String, PartitionDef> = ALL.associateBy { it.id }

    fun glob(part: PartitionDef): String = "${BASE_DIR}/${part.id}-*.img"
}
