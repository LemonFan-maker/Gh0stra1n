package com.orionisli.gh0stra1n

import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ImageAnalysisActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTITION_ID = "extra_partition_id"
    }

    private lateinit var btnBack: ImageView
    private lateinit var btnRefresh: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtSubtitle: TextView

    private lateinit var layoutContent: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var txtLoadingMsg: TextView

    private lateinit var txtExt4Overview: TextView
    private lateinit var txtScanSpeed: TextView
    private lateinit var txtInodeDetail: TextView
    private lateinit var txtFeaturesDetail: TextView

    private lateinit var treemapView: TreemapView
    private lateinit var txtSelectionInfo: TextView
    private lateinit var txtSelectionSize: TextView

    private lateinit var scrollBreadcrumb: HorizontalScrollView
    private lateinit var layoutBreadcrumb: LinearLayout
    private lateinit var txtCurrentDir: TextView
    private lateinit var txtCurrentDirSize: TextView
    private lateinit var recyclerNcdu: RecyclerView

    private lateinit var ncduAdapter: NcduAdapter

    private var targetPartId: String = "system"
    private var partitionDef: PartitionDef = PartitionTable.ALL.first()
    private var rootNode: FileNode? = null
    private var currentDirNode: FileNode? = null
    private var analysisGeneration = 0

    override fun onDestroy() {
        analysisGeneration++
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyStyleTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_analysis)
        ThemeManager.applyToActivity(this)

        HapticUtil.init(this)
        initViews()
        attachHeaderListInsets(findViewById(R.id.root_image_analysis), findViewById(R.id.header_bar), recyclerNcdu)

        targetPartId = intent.getStringExtra(EXTRA_PARTITION_ID) ?: "system"
        partitionDef = PartitionTable.byId[targetPartId] ?: PartitionTable.ALL.first()

        txtTitle.text = getString(R.string.analysis_title)
        txtSubtitle.text = getString(R.string.analysis_subtitle_fmt, partitionDef.id, partitionDef.mountPoint)

        startAnalysis()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        btnRefresh = findViewById(R.id.btn_refresh)
        txtTitle = findViewById(R.id.txt_title)
        txtSubtitle = findViewById(R.id.txt_subtitle)

        layoutContent = findViewById(R.id.layout_content)
        layoutLoading = findViewById(R.id.layout_loading)
        txtLoadingMsg = findViewById(R.id.txt_loading_msg)

        txtExt4Overview = findViewById(R.id.txt_ext4_overview)
        txtScanSpeed = findViewById(R.id.txt_scan_speed)
        txtInodeDetail = findViewById(R.id.txt_inode_detail)
        txtFeaturesDetail = findViewById(R.id.txt_features_detail)

        treemapView = findViewById(R.id.treemap_view)
        txtSelectionInfo = findViewById(R.id.txt_selection_info)
        txtSelectionSize = findViewById(R.id.txt_selection_size)

        scrollBreadcrumb = findViewById(R.id.scroll_breadcrumb)
        layoutBreadcrumb = findViewById(R.id.layout_breadcrumb)
        txtCurrentDir = findViewById(R.id.txt_current_dir)
        txtCurrentDirSize = findViewById(R.id.txt_current_dir_size)
        recyclerNcdu = findViewById(R.id.recycler_ncdu)

        listOf(btnBack, btnRefresh).forEach {
            ViewAnimUtil.addPressScaleEffect(it)
        }

        btnBack.setOnClickListener {
            HapticUtil.click(it)
            finish()
        }

        btnRefresh.setOnClickListener {
            HapticUtil.click(it)
            startAnalysis()
        }

        ncduAdapter = NcduAdapter(
            onItemClick = { clickedItem ->
                if (clickedItem.isDirectory) {
                    navigateToDirectory(clickedItem)
                } else {
                    treemapView.setHighlightedNode(clickedItem)
                    ncduAdapter.setSelected(clickedItem)
                    updateSelectionInfo(clickedItem)
                }
            },
            onParentClick = {
                currentDirNode?.parent?.let { parent ->
                    navigateToDirectory(parent)
                }
            }
        )

        recyclerNcdu.layoutManager = LinearLayoutManager(this)
        recyclerNcdu.adapter = ncduAdapter

        treemapView.onNodeClicked = { clickedNode ->
            updateSelectionInfo(clickedNode)

            if (clickedNode.isDirectory) {
                navigateToDirectory(clickedNode)
            } else {
                val parent = clickedNode.parent
                if (parent != null) {
                    if (currentDirNode != parent) {
                        navigateToDirectory(parent, selectedFile = clickedNode)
                    } else {
                        ncduAdapter.setSelected(clickedNode)
                        scrollToItem(clickedNode)
                    }
                }
            }
        }
    }

    private fun startAnalysis() {
        layoutLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.INVISIBLE
        txtLoadingMsg.text = getString(R.string.analysis_loading_fmt, partitionDef.id)
        val gen = ++analysisGeneration

        Thread {
            val result = ImageAnalyzer.analyze(partitionDef)
            runOnUiThread {
                if (isFinishing || isDestroyed || gen != analysisGeneration) return@runOnUiThread
                layoutLoading.visibility = View.GONE
                result.fold(
                    onSuccess = {
                        layoutContent.visibility = View.VISIBLE
                        bindAnalysisData(it)
                        HapticUtil.success()
                    },
                    onFailure = { e ->
                        HapticUtil.error()
                        showTreeLoadFailure(this, R.string.analysis_failed_title, e) { startAnalysis() }
                    },
                )
            }
        }.start()
    }

    private fun bindAnalysisData(data: ImageAnalysisResult) {
        val meta = data.metadata
        val root = data.rootNode
        rootNode = root

        val usedMb = "%.1f MiB".format(meta.usedBytes / (1024.0 * 1024.0))
        val totalMb = "%.1f MiB".format(meta.totalBytes / (1024.0 * 1024.0))
        val usagePct = "%.1f%%".format(meta.blockUsagePct)
        txtExt4Overview.text = getString(R.string.analysis_ext4_fmt, meta.blockSize, usedMb, totalMb, usagePct)

        txtScanSpeed.text = getString(R.string.analysis_speed_fmt, data.scanDurationMs, data.totalScannedFiles)

        val featuresPreview = if (meta.features.isNotEmpty()) meta.features.take(8).joinToString(", ") else "ext4_standard"
        txtInodeDetail.text = getString(R.string.analysis_inode_fmt, meta.usedInodes, meta.totalInodes, meta.inodeUsagePct)
        txtFeaturesDetail.text = getString(R.string.analysis_features_fmt, featuresPreview)

        treemapView.setRootNode(root)

        val initialDir = root.children.firstOrNull { it.isDirectory && it.name == "u" } ?: root
        navigateToDirectory(initialDir)
    }

    private fun navigateToDirectory(dir: FileNode, selectedFile: FileNode? = null) {
        currentDirNode = dir

        val highlightTarget = selectedFile ?: dir
        treemapView.setHighlightedNode(highlightTarget)

        ncduAdapter.setDirectory(dir, selected = highlightTarget)
        rebuildBreadcrumbs(dir)

        txtCurrentDir.text = getString(R.string.analysis_current_level_fmt, dir.path, dir.fileCount)
        txtCurrentDirSize.text = dir.formattedSize()

        updateSelectionInfo(highlightTarget)

        if (selectedFile != null) {
            scrollToItem(selectedFile)
        } else {
            recyclerNcdu.scrollToPosition(0)
        }
    }

    private fun scrollToItem(target: FileNode) {
        val hasParent = currentDirNode?.parent != null
        val items = currentDirNode?.children?.sortedByDescending { it.size } ?: return
        val idx = items.indexOf(target)
        if (idx >= 0) {
            val pos = if (hasParent) idx + 1 else idx
            recyclerNcdu.smoothScrollToPosition(pos)
        }
    }

    private fun updateSelectionInfo(node: FileNode) {
        val prefix = if (node.isDirectory) getString(R.string.analysis_dir_prefix) else getString(R.string.analysis_file_prefix)
        txtSelectionInfo.text = "$prefix${node.path}"
        txtSelectionSize.text = node.formattedSize()
    }

    private fun rebuildBreadcrumbs(currentDir: FileNode) {
        rebuildBreadcrumbBar(layoutBreadcrumb, scrollBreadcrumb, currentDir) { navigateToDirectory(it) }
    }
}
