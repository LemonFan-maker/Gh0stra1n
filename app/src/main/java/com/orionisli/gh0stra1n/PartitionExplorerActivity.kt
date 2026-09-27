package com.orionisli.gh0stra1n

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PartitionExplorerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTITION_ID = "extra_partition_id"
    }

    private lateinit var btnBack: ImageView
    private lateinit var btnSearch: ImageView
    private lateinit var btnToggleHidden: ImageView
    private lateinit var btnSort: ImageView
    private lateinit var btnRefresh: ImageView

    private lateinit var txtTitle: TextView
    private lateinit var txtSubtitle: TextView

    private lateinit var layoutSearch: LinearLayout
    private lateinit var editSearch: EditText
    private lateinit var btnClearSearch: ImageView

    private lateinit var chipLayerRoot: TextView
    private lateinit var chipLayerUpper: TextView
    private lateinit var chipLayerWork: TextView

    private lateinit var layoutContent: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var txtLoadingMsg: TextView
    private lateinit var layoutEmptyFolder: LinearLayout

    private lateinit var scrollBreadcrumb: HorizontalScrollView
    private lateinit var layoutBreadcrumb: LinearLayout
    private lateinit var txtFolderStat: TextView
    private lateinit var txtFolderSize: TextView
    private lateinit var recyclerFiles: RecyclerView

    private lateinit var adapter: PartitionExplorerAdapter

    private var targetPartId: String = "system"
    private var partitionDef: PartitionDef = PartitionTable.ALL.first()
    private var rootNode: FileNode? = null
    private var currentDirNode: FileNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val sp = getSharedPreferences("gh0stra1n_settings", Context.MODE_PRIVATE)
        when (sp.getString("ui_theme", "beige")) {
            "slate" -> setTheme(R.style.Theme_Gh0stra1n_Slate)
            "cyber" -> setTheme(R.style.Theme_Gh0stra1n_CyberDark)
            "matcha" -> setTheme(R.style.Theme_Gh0stra1n_Matcha)
            "nord", "aurora" -> setTheme(R.style.Theme_Gh0stra1n_Nord)
            "sakura" -> setTheme(R.style.Theme_Gh0stra1n_Sakura)
            else -> setTheme(R.style.Theme_Gh0stra1n_Beige)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partition_explorer)
        ThemeManager.applyToActivity(this)

        HapticUtil.init(this)
        initViews()
        setupInsets()

        targetPartId = intent.getStringExtra(EXTRA_PARTITION_ID) ?: "system"
        partitionDef = PartitionTable.byId[targetPartId] ?: PartitionTable.ALL.first()

        txtTitle.text = getString(R.string.explorer_title_fmt, partitionDef.id)
        txtSubtitle.text = getString(R.string.explorer_sub_fmt, partitionDef.mountPoint)

        startLoadDirectoryTree()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        btnSearch = findViewById(R.id.btn_search)
        btnToggleHidden = findViewById(R.id.btn_toggle_hidden)
        btnSort = findViewById(R.id.btn_sort)
        btnRefresh = findViewById(R.id.btn_refresh)

        txtTitle = findViewById(R.id.txt_explorer_title)
        txtSubtitle = findViewById(R.id.txt_explorer_subtitle)

        layoutSearch = findViewById(R.id.layout_search)
        editSearch = findViewById(R.id.edit_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)

        chipLayerRoot = findViewById(R.id.chip_layer_root)
        chipLayerUpper = findViewById(R.id.chip_layer_upper)
        chipLayerWork = findViewById(R.id.chip_layer_work)

        layoutContent = findViewById(R.id.layout_explorer_content)
        layoutLoading = findViewById(R.id.layout_loading)
        txtLoadingMsg = findViewById(R.id.txt_loading_msg)
        layoutEmptyFolder = findViewById(R.id.layout_empty_folder)

        scrollBreadcrumb = findViewById(R.id.scroll_breadcrumb)
        layoutBreadcrumb = findViewById(R.id.layout_breadcrumb)
        txtFolderStat = findViewById(R.id.txt_folder_stat)
        txtFolderSize = findViewById(R.id.txt_folder_size)
        recyclerFiles = findViewById(R.id.recycler_files)

        listOf(btnBack, btnSearch, btnToggleHidden, btnSort, btnRefresh, btnClearSearch,
            chipLayerRoot, chipLayerUpper, chipLayerWork).forEach {
            ViewAnimUtil.addPressScaleEffect(it)
        }

        btnBack.setOnClickListener {
            HapticUtil.click(it)
            finish()
        }

        btnRefresh.setOnClickListener {
            HapticUtil.click(it)
            startLoadDirectoryTree()
        }

        // 搜索栏展开/关闭
        btnSearch.setOnClickListener {
            HapticUtil.click(it)
            if (layoutSearch.visibility == View.VISIBLE) {
                layoutSearch.visibility = View.GONE
                editSearch.setText("")
                adapter.searchFilter = ""
            } else {
                layoutSearch.visibility = View.VISIBLE
                editSearch.requestFocus()
            }
        }

        btnClearSearch.setOnClickListener {
            HapticUtil.click(it)
            editSearch.setText("")
            adapter.searchFilter = ""
            layoutSearch.visibility = View.GONE
        }

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.searchFilter = s?.toString() ?: ""
                updateFolderStats()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 隐藏文件显示开关
        btnToggleHidden.setOnClickListener {
            HapticUtil.click(it)
            adapter.showHiddenFiles = !adapter.showHiddenFiles
            val active = adapter.showHiddenFiles
            val p = ThemeManager.getCurrentPalette(this)
            btnToggleHidden.imageTintList = ColorStateList.valueOf(
                if (active) p.accent else p.textSecondary
            )
            Toast.makeText(
                this,
                if (active) getString(R.string.toast_hidden_shown) else getString(R.string.toast_hidden_hidden),
                Toast.LENGTH_SHORT
            ).show()
            updateFolderStats()
        }

        // 排序选项
        btnSort.setOnClickListener {
            HapticUtil.click(it)
            showSortOptionsDialog()
        }

        // 快捷层级入口
        chipLayerRoot.setOnClickListener {
            HapticUtil.click(it)
            rootNode?.let { r -> navigateToDirectory(r) }
        }
        chipLayerUpper.setOnClickListener {
            HapticUtil.click(it)
            val u = rootNode?.children?.firstOrNull { it.isDirectory && it.name == "u" }
            if (u != null) {
                navigateToDirectory(u)
            } else {
                Toast.makeText(this, getString(R.string.toast_layer_not_found, "u"), Toast.LENGTH_SHORT).show()
            }
        }
        chipLayerWork.setOnClickListener {
            HapticUtil.click(it)
            val w = rootNode?.children?.firstOrNull { it.isDirectory && it.name == "w" }
            if (w != null) {
                navigateToDirectory(w)
            } else {
                Toast.makeText(this, getString(R.string.toast_layer_not_found, "w"), Toast.LENGTH_SHORT).show()
            }
        }

        // 初始化文件管理器适配器
        adapter = PartitionExplorerAdapter(
            onItemClick = { clickedItem ->
                if (clickedItem.isDirectory) {
                    navigateToDirectory(clickedItem)
                } else {
                    showFilePreviewDialog(clickedItem)
                }
            },
            onParentClick = {
                currentDirNode?.parent?.let { parent ->
                    navigateToDirectory(parent)
                }
            },
            onMoreClick = { clickedItem ->
                showFileMoreMenu(clickedItem)
            }
        )

        recyclerFiles.layoutManager = LinearLayoutManager(this)
        recyclerFiles.adapter = adapter

        val prefHidden = SettingsStore(this).showHiddenFiles
        adapter.showHiddenFiles = prefHidden
        if (prefHidden) {
            val p = ThemeManager.getCurrentPalette(this)
            btnToggleHidden.imageTintList = ColorStateList.valueOf(p.accent)
        }
    }

    private fun setupInsets() {
        val root = findViewById<View>(R.id.root_partition_explorer)
        val header = findViewById<View>(R.id.header_bar)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            header.setPadding(dp(16), statusBars.top + dp(6), dp(16), dp(6))
            recyclerFiles.setPadding(0, 0, 0, navBars.bottom + dp(16))

            insets
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startLoadDirectoryTree() {
        layoutLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.INVISIBLE
        txtLoadingMsg.text = getString(R.string.explorer_loading)

        Thread {
            val result = ImageAnalyzer.analyze(partitionDef)
            runOnUiThread {
                layoutLoading.visibility = View.GONE
                if (result.isSuccess) {
                    val analysis = result.getOrThrow()
                    layoutContent.visibility = View.VISIBLE
                    rootNode = analysis.rootNode
                    // 默认定位进入 root 或 u/
                    val initialDir = analysis.rootNode.children.firstOrNull { it.isDirectory && it.name == "u" } ?: analysis.rootNode
                    navigateToDirectory(initialDir)
                    HapticUtil.success()
                } else {
                    HapticUtil.error()
                    val error = result.exceptionOrNull()?.message ?: ""
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.explorer_load_failed_title)
                        .setMessage(error)
                        .setPositiveButton(R.string.btn_retry) { _, _ -> startLoadDirectoryTree() }
                        .setNegativeButton(R.string.back) { _, _ -> finish() }
                        .show()
                }
            }
        }.start()
    }

    private fun navigateToDirectory(dir: FileNode) {
        currentDirNode = dir

        if (editSearch.text.isNotEmpty()) {
            editSearch.setText("")
            adapter.searchFilter = ""
        }

        adapter.setDirectory(dir)
        rebuildBreadcrumbs(dir)
        updateFolderStats()
        updateLayerChips(dir)

        recyclerFiles.scrollToPosition(0)
    }

    private fun updateFolderStats() {
        val dir = currentDirNode ?: return
        val count = dir.children.size
        val hiddenCount = dir.children.count { it.isHidden }
        txtFolderStat.text = if (hiddenCount > 0) {
            getString(R.string.explorer_stats_fmt, count, hiddenCount)
        } else {
            getString(R.string.explorer_items_count_fmt, count)
        }
        txtFolderSize.text = dir.formattedSize()

        layoutEmptyFolder.visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    private fun updateLayerChips(dir: FileNode) {
        val isRoot = dir.path == "/"
        val isUpper = dir.path == "u" || dir.path.startsWith("u/")
        val isWork = dir.path == "w" || dir.path.startsWith("w/")
        val p = ThemeManager.getCurrentPalette(this)
        val density = resources.displayMetrics.density

        fun styleChip(chip: TextView, active: Boolean) {
            val pillBg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * density
                if (active) {
                    setColor(p.accentBg)
                    setStroke((1 * density).toInt(), p.accent)
                } else {
                    setColor(p.cardInner)
                    setStroke((1 * density).toInt(), p.cardBorder)
                }
            }
            chip.background = pillBg
            chip.setTextColor(if (active) p.accent else p.textSecondary)
        }

        styleChip(chipLayerRoot, isRoot)
        styleChip(chipLayerUpper, isUpper)
        styleChip(chipLayerWork, isWork)
    }

    private fun showSortOptionsDialog() {
        val options = arrayOf(
            getString(R.string.sort_by_type),
            getString(R.string.sort_by_size),
            getString(R.string.sort_by_name)
        )
        val checkedItem = when (adapter.sortMode) {
            ExplorerSortMode.TYPE_FIRST -> 0
            ExplorerSortMode.SIZE_DESC -> 1
            ExplorerSortMode.NAME_ASC -> 2
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                HapticUtil.click()
                adapter.sortMode = when (which) {
                    0 -> ExplorerSortMode.TYPE_FIRST
                    1 -> ExplorerSortMode.SIZE_DESC
                    else -> ExplorerSortMode.NAME_ASC
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showFileMoreMenu(node: FileNode) {
        val items = mutableListOf(
            getString(R.string.action_preview),
            getString(R.string.action_copy_rel_path),
            getString(R.string.action_copy_real_path)
        )
        if (!node.isDirectory) {
            items.add(getString(R.string.action_copy_content))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(node.name)
            .setItems(items.toTypedArray()) { _, which ->
                HapticUtil.click()
                when (which) {
                    0 -> showFilePreviewDialog(node)
                    1 -> copyToClipboard("RelativePath", node.path, getString(R.string.toast_rel_path_copied))
                    2 -> {
                        val real = if (node.realDiskPath.isNotEmpty()) node.realDiskPath else "${PartitionTable.BASE_DIR}/mnt_${partitionDef.id}${node.path}"
                        copyToClipboard("DiskPath", real, getString(R.string.toast_disk_path_copied))
                    }
                    3 -> {
                        Thread {
                            val (_, content) = ImageAnalyzer.readFilePreview(partitionDef, node.path)
                            runOnUiThread {
                                copyToClipboard("FileContent", content, getString(R.string.toast_file_content_copied))
                            }
                        }.start()
                    }
                }
            }
            .show()
    }

    private fun copyToClipboard(label: String, text: String, toastMsg: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
    }

    private fun showFilePreviewDialog(node: FileNode) {
        HapticUtil.click()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_preview, null)
        val p = ThemeManager.getCurrentPalette(this)
        ThemeManager.applyToView(dialogView, p)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val imgIcon = dialogView.findViewById<ImageView>(R.id.img_preview_icon)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txt_preview_title)
        val tagType = dialogView.findViewById<TextView>(R.id.tag_preview_type)
        val tagHidden = dialogView.findViewById<TextView>(R.id.tag_preview_hidden)
        val btnCloseTop = dialogView.findViewById<ImageView>(R.id.btn_preview_close_top)

        val txtFilePath = dialogView.findViewById<TextView>(R.id.txt_file_path)
        val txtFileSize = dialogView.findViewById<TextView>(R.id.txt_file_size_detail)
        val txtFilePerm = dialogView.findViewById<TextView>(R.id.txt_file_perm_detail)
        val txtDiskPath = dialogView.findViewById<TextView>(R.id.txt_disk_path_detail)

        val txtSectionHeader = dialogView.findViewById<TextView>(R.id.txt_preview_section_header)
        val txtContentPreview = dialogView.findViewById<TextView>(R.id.txt_file_content_preview)

        val btnCopyPath = dialogView.findViewById<MaterialButton>(R.id.btn_copy_path)
        val btnCopyContent = dialogView.findViewById<MaterialButton>(R.id.btn_copy_content)
        val btnDone = dialogView.findViewById<MaterialButton>(R.id.btn_preview_done)

        imgIcon.imageTintList = ColorStateList.valueOf(node.fileType.color)
        txtTitle.text = node.name
        txtTitle.setTextColor(p.textPrimary)
        tagType.text = node.fileType.getDisplayName(this)
        ThemeManager.stylePill(tagType, PillType.ACCENT, p)

        if (node.isHidden) {
            tagHidden.visibility = View.VISIBLE
            ThemeManager.stylePill(tagHidden, PillType.GRAY, p)
        } else {
            tagHidden.visibility = View.GONE
        }

        btnDone.backgroundTintList = ColorStateList.valueOf(p.cardInner)
        btnDone.setTextColor(p.textPrimary)
        btnCopyPath.backgroundTintList = ColorStateList.valueOf(p.accent)
        btnCopyPath.setTextColor(Color.WHITE)
        btnCopyContent.backgroundTintList = ColorStateList.valueOf(p.cardInner)
        btnCopyContent.setTextColor(p.textPrimary)
        btnCloseTop.imageTintList = ColorStateList.valueOf(p.textSecondary)

        txtFilePath.text = getString(R.string.preview_rel_path_fmt, node.path)
        txtFileSize.text = getString(R.string.preview_file_size_fmt, node.formattedSize(), node.size)

        val permStr = if (node.permissions.isNotEmpty()) node.permissions else "-rw-r--r--"
        val ownerStr = if (node.owner.isNotEmpty()) node.owner else "root:root"
        txtFilePerm.text = getString(R.string.preview_file_perm_fmt, permStr, ownerStr)

        val diskPath = if (node.realDiskPath.isNotEmpty()) node.realDiskPath else "${PartitionTable.BASE_DIR}/mnt_${partitionDef.id}${node.path}"
        txtDiskPath.text = getString(R.string.preview_disk_path_fmt, diskPath)

        btnCloseTop.setOnClickListener {
            HapticUtil.click(it)
            dialog.dismiss()
        }
        btnDone.setOnClickListener {
            HapticUtil.click(it)
            dialog.dismiss()
        }

        btnCopyPath.setOnClickListener {
            HapticUtil.click(it)
            copyToClipboard("FilePath", diskPath, getString(R.string.toast_file_path_copied))
        }

        txtContentPreview.text = getString(R.string.preview_reading)
        var loadedContent = ""

        Thread {
            val (isText, content) = ImageAnalyzer.readFilePreview(partitionDef, node.path)
            loadedContent = content
            runOnUiThread {
                txtSectionHeader.text = if (isText) getString(R.string.preview_text_header) else getString(R.string.preview_hex_header)
                txtContentPreview.text = content
                if (!isText) {
                    btnCopyContent.visibility = View.GONE
                }
            }
        }.start()

        btnCopyContent.setOnClickListener {
            HapticUtil.click(it)
            if (loadedContent.isNotEmpty()) {
                copyToClipboard("FileContent", loadedContent, getString(R.string.toast_file_content_copied))
            }
        }

        dialog.show()
    }

    private fun rebuildBreadcrumbs(currentDir: FileNode) {
        layoutBreadcrumb.removeAllViews()
        val crumbs = currentDir.getBreadcrumbList()
        val p = ThemeManager.getCurrentPalette(this)

        for (i in crumbs.indices) {
            val node = crumbs[i]
            val isLast = (i == crumbs.size - 1)

            val crumbView = TextView(this).apply {
                val displayName = if (node.path == "/") getString(R.string.explorer_root_breadcrumb) else "${node.name}/"
                text = displayName
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(if (isLast) p.accent else p.textSecondary)
                val density = resources.displayMetrics.density
                val pillBg = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12f * density
                    if (isLast) {
                        setColor(p.accentBg)
                        setStroke((1 * density).toInt(), p.accent)
                    } else {
                        setColor(p.cardInner)
                        setStroke((1 * density).toInt(), p.cardBorder)
                    }
                }
                background = pillBg
                setPadding(dp(8), dp(3), dp(8), dp(3))
                gravity = Gravity.CENTER
                setOnClickListener {
                    HapticUtil.click(it)
                    navigateToDirectory(node)
                }
            }
            ViewAnimUtil.addPressScaleEffect(crumbView)
            layoutBreadcrumb.addView(crumbView)

            if (!isLast) {
                val arrow = TextView(this).apply {
                    text = " > "
                    textSize = 10f
                    setTextColor(p.textMuted)
                }
                layoutBreadcrumb.addView(arrow)
            }
        }

        scrollBreadcrumb.post {
            scrollBreadcrumb.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }
}
