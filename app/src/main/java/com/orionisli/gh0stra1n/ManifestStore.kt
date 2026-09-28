package com.orionisli.gh0stra1n

import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

object ManifestStore {

    const val MANIFEST_PATH = "${PartitionTable.BASE_DIR}/manifest.json"
    const val MIN_SIZE_MIB = 16L
    const val MAX_SIZE_MIB = 8192L

    data class ImageEntry(
        val file: String,
        val uuid8: String,
        val size: Long,
        val created: String = "",
        val note: String = "",
    )

    data class PartEntry(
        val defaultSizeMiB: Long,
        val locked: Boolean,
        val images: MutableList<ImageEntry>,
    )

    val lockedLimitTop: Long get() = MAX_SIZE_MIB * 1024 * 1024

    @Volatile
    private var cached: JSONObject? = null

    @Volatile
    private var cacheLoaded = false

    private val saveSeq = AtomicLong(0)

    fun load(): JSONObject? {
        if (cacheLoaded) return cached
        val r = SuChannel.run("cat $MANIFEST_PATH 2>/dev/null", 10)
        val parsed = if (!r.ok || r.out.isBlank()) {
            null
        } else {
            try {
                JSONObject(r.out)
            } catch (e: Exception) {
                AppLogger.e("MANIFEST", "清单解析失败：$MANIFEST_PATH ${e.message}")
                null
            }
        }
        cached = parsed
        cacheLoaded = true
        return parsed
    }

    fun save(root: JSONObject): Boolean {
        val tmp = "$MANIFEST_PATH.tmp.${Thread.currentThread().id}.${saveSeq.incrementAndGet()}"
        val r = SuChannel.run(
            "mkdir -p ${PartitionTable.BASE_DIR} && " +
            "printf '%s' '${root.toString().replace("'", "'\\''")}' > $tmp && mv $tmp $MANIFEST_PATH && sync", 15)
        if (r.ok) {
            cached = root
            cacheLoaded = true
        } else {
            cached = null
            cacheLoaded = false
        }
        return r.ok
    }

    private fun partObj(root: JSONObject, id: String): JSONObject {
        val parts = root.optJSONObject("partitions") ?: JSONObject()
        val p = parts.optJSONObject(id) ?: JSONObject()
        if (!parts.has(id)) parts.put(id, p)
        root.put("partitions", parts)
        return p
    }

    fun getPart(id: String, defaultSizeMiB: Long): PartEntry {
        val root = load() ?: JSONObject().put("version", 1)
        val p = partObj(root, id)
        val size = p.optLong("default_size_mib", defaultSizeMiB)
        val locked = p.optBoolean("locked", false)
        val imgs = JSONArray()
        p.optJSONArray("images")?.let { for (i in 0 until it.length()) imgs.put(it.getJSONObject(i)) }
        val list = mutableListOf<ImageEntry>()
        for (i in 0 until imgs.length()) {
            val o = imgs.getJSONObject(i)
            list.add(ImageEntry(
                file = o.optString("file"),
                uuid8 = o.optString("uuid8"),
                size = o.optLong("size"),
                created = o.optString("created"),
                note = o.optString("note"),
            ))
        }
        return PartEntry(size, locked, list)
    }

    fun putPart(id: String, e: PartEntry): Boolean {
        val root = load() ?: JSONObject().put("version", 1)
        val p = partObj(root, id)
        p.put("default_size_mib", e.defaultSizeMiB)
        p.put("locked", e.locked)
        val arr = JSONArray()
        for (img in e.images) {
            arr.put(JSONObject()
                .put("file", img.file)
                .put("uuid8", img.uuid8)
                .put("size", img.size)
                .put("created", img.created)
                .put("note", img.note))
        }
        p.put("images", arr)
        return save(root)
    }

    fun clearPartImages(id: String): Boolean {
        val root = load() ?: return false
        val parts = root.optJSONObject("partitions") ?: return false
        val p = parts.optJSONObject(id)
        if (p != null) {
            p.put("images", JSONArray())
        }
        val settings = root.optJSONObject("settings")
        if (settings != null) {
            val lastMount = settings.optJSONArray("last_mount")
            if (lastMount != null) {
                val newArr = JSONArray()
                for (i in 0 until lastMount.length()) {
                    val mId = lastMount.optString(i)
                    if (mId != id) newArr.put(mId)
                }
                settings.put("last_mount", newArr)
            }
        }
        return save(root)
    }

    fun clearAllPartImages(): Boolean {
        val root = load() ?: return false
        val parts = root.optJSONObject("partitions") ?: return false
        val it = parts.keys()
        while (it.hasNext()) {
            val k = it.next()
            parts.optJSONObject(k)?.put("images", JSONArray())
        }
        val settings = root.optJSONObject("settings")
        if (settings != null) {
            settings.put("last_mount", JSONArray())
            settings.put("last_state", "DETACH")
        }
        return save(root)
    }

    fun reconcile(part: PartitionDef): PartEntry {
        val entry = getPart(part.id, part.defaultSizeMiB)
        val r = SuChannel.run("ls ${PartitionTable.glob(part)} 2>/dev/null", 15)
        val onDisk = if (r.ok) r.out.trim().lines()
            .filter { it.endsWith(".img") }
            .mapNotNull { fn -> fn.substringAfterLast('/').takeIf { it.startsWith("${part.id}-") } }
        else emptyList()
        val known = entry.images.map { it.file }.toSet()
        val missing = onDisk.filter { it !in known }
        for (f in missing) {
            val size = statSize(f)
            val uuid8 = f.removePrefix("${part.id}-").removeSuffix(".img")
            entry.images.add(ImageEntry(file = f, uuid8 = uuid8, size = size))
        }
        var dirty = missing.isNotEmpty()
        for (i in entry.images.indices) {
            val img = entry.images[i]
            if (img.size <= 0L && img.file in onDisk) {
                entry.images[i] = img.copy(size = statSize(img.file))
                dirty = true
            }
        }
        if (dirty) putPart(part.id, entry)
        return entry
    }

    private fun statSize(f: String): Long {
        val s = SuChannel.run("stat -c %s ${PartitionTable.BASE_DIR}/$f", 10)
        return s.out.trim().toLongOrNull() ?: 0L
    }

    fun lock(id: String, sizeMiB: Long): Boolean {
        val e = getPart(id, sizeMiB)
        return putPart(id, e.copy(defaultSizeMiB = sizeMiB, locked = true))
    }

    data class ImagePresetConfig(
        val fsType: String = "ext4",
        val defaultSizeMiB: Long = 1024,
        val blockSize: Int = 4096,
        val bytesPerInode: Int = 8192,
        val reservedRatioPct: Int = 1,
        val enableDirIndex: Boolean = true,
        val enableFiletype: Boolean = true,
        val enableSparseSuper: Boolean = true,
    )

    fun loadPreset(): ImagePresetConfig {
        val root = load() ?: return ImagePresetConfig()
        val p = settingsObj(root).optJSONObject("image_preset") ?: return ImagePresetConfig()
        return ImagePresetConfig(
            fsType = p.optString("fs_type", "ext4"),
            defaultSizeMiB = p.optLong("default_size_mib", 1024L),
            blockSize = p.optInt("block_size", 4096),
            bytesPerInode = p.optInt("bytes_per_inode", 8192),
            reservedRatioPct = p.optInt("reserved_ratio_pct", 1),
            enableDirIndex = p.optBoolean("enable_dir_index", true),
            enableFiletype = p.optBoolean("enable_filetype", true),
            enableSparseSuper = p.optBoolean("enable_sparse_super", true),
        )
    }

    fun savePreset(preset: ImagePresetConfig): Boolean {
        val root = load() ?: JSONObject().put("version", 2)
        val p = JSONObject()
            .put("fs_type", preset.fsType)
            .put("default_size_mib", preset.defaultSizeMiB)
            .put("block_size", preset.blockSize)
            .put("bytes_per_inode", preset.bytesPerInode)
            .put("reserved_ratio_pct", preset.reservedRatioPct)
            .put("enable_dir_index", preset.enableDirIndex)
            .put("enable_filetype", preset.enableFiletype)
            .put("enable_sparse_super", preset.enableSparseSuper)
        settingsObj(root).put("image_preset", p)
        return save(root)
    }

    private fun settingsObj(root: JSONObject): JSONObject {
        val s = root.optJSONObject("settings") ?: JSONObject()
        if (!root.has("settings")) root.put("settings", s)
        if (root.optInt("version", 0) < 2) root.put("version", 2)
        return s
    }

    fun getSettingInt(key: String, def: Int): Int {
        val root = load() ?: return def
        val v = settingsObj(root).opt(key)
        return (v as? Number)?.toInt() ?: def
    }

    fun getSettingLong(key: String, def: Long): Long {
        val root = load() ?: return def
        val v = settingsObj(root).opt(key)
        return (v as? Number)?.toLong() ?: def
    }

    fun getSettingBool(key: String, def: Boolean): Boolean {
        val root = load() ?: return def
        val v = settingsObj(root).opt(key)
        return (v as? Boolean) ?: def
    }

    fun setSettingInt(key: String, value: Int) = setSetting(key, value)
    fun setSettingLong(key: String, value: Long) = setSetting(key, value)
    fun setSettingBool(key: String, value: Boolean) = setSetting(key, value)

    fun setSetting(key: String, value: Any): Boolean {
        val root = load() ?: JSONObject().put("version", 2)
        settingsObj(root).put(key, value)
        return save(root)
    }

    fun lastMountState(): Pair<List<String>, String> {
        val root = load() ?: return emptyList<String>() to "DETACH"
        val s = settingsObj(root)
        val ids = s.optJSONArray("last_mount")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }
        } ?: emptyList()
        return ids to s.optString("last_state", "DETACH")
    }

    fun recordLastMount(ids: List<String>, state: String) {
        val root = load() ?: JSONObject().put("version", 2)
        val s = settingsObj(root)
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        s.put("last_mount", arr)
        s.put("last_state", state)
        save(root)
    }
}
