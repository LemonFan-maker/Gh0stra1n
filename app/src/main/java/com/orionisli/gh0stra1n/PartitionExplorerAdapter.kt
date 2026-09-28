package com.orionisli.gh0stra1n

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

enum class ExplorerSortMode {
    NAME_ASC,
    SIZE_DESC,
    TYPE_FIRST
}

class PartitionExplorerAdapter(
    private val onItemClick: (FileNode) -> Unit,
    private val onParentClick: () -> Unit,
    private val onMoreClick: (FileNode) -> Unit
) : RecyclerView.Adapter<PartitionExplorerAdapter.ViewHolder>() {

    private var currentDir: FileNode? = null
    private var allRawChildren: List<FileNode> = emptyList()
    private var displayItems: List<FileNode> = emptyList()

    var showHiddenFiles: Boolean = true
        set(value) {
            field = value
            applyFilterAndSort()
        }

    var searchFilter: String = ""
        set(value) {
            field = value.trim().lowercase()
            applyFilterAndSort()
        }

    var sortMode: ExplorerSortMode = ExplorerSortMode.TYPE_FIRST
        set(value) {
            field = value
            applyFilterAndSort()
        }

    fun setDirectory(dir: FileNode) {
        currentDir = dir
        allRawChildren = dir.children
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        var filtered = allRawChildren

        if (!showHiddenFiles) {
            filtered = filtered.filter { !it.isHidden }
        }

        if (searchFilter.isNotEmpty()) {
            filtered = filtered.filter { it.name.lowercase().contains(searchFilter) }
        }

        displayItems = when (sortMode) {
            ExplorerSortMode.TYPE_FIRST -> {
                filtered.sortedWith(
                    compareByDescending<FileNode> { it.isDirectory }
                        .thenBy { it.extension }
                        .thenBy { it.name.lowercase() }
                )
            }
            ExplorerSortMode.SIZE_DESC -> {
                filtered.sortedWith(
                    compareByDescending<FileNode> { it.isDirectory }
                        .thenByDescending { it.size }
                )
            }
            ExplorerSortMode.NAME_ASC -> {
                filtered.sortedWith(
                    compareByDescending<FileNode> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            }
        }

        notifyDataSetChanged()
    }

    private val hasParent: Boolean
        get() = currentDir?.parent != null && searchFilter.isEmpty()

    override fun getItemCount(): Int {
        return (if (hasParent) 1 else 0) + displayItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (hasParent && position == 0) 0 else 1
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_explorer_item)
        val iconContainer: FrameLayout = view.findViewById(R.id.layout_icon_container)
        val icon: ImageView = view.findViewById(R.id.img_file_icon)
        val name: TextView = view.findViewById(R.id.txt_file_name)
        val badgeHidden: TextView = view.findViewById(R.id.badge_file_hidden)
        val details: TextView = view.findViewById(R.id.txt_file_details)
        val btnMore: ImageView = view.findViewById(R.id.btn_file_more)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_explorer_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = ThemeManager.getCurrentPalette(holder.itemView.context)
        holder.card.setCardBackgroundColor(p.cardBg)
        holder.card.strokeColor = p.cardBorder
        holder.btnMore.imageTintList = ColorStateList.valueOf(p.textSecondary)

        if (hasParent && position == 0) {
            holder.icon.setImageResource(R.drawable.ic_arrow_up)
            holder.icon.imageTintList = ColorStateList.valueOf(p.textSecondary)
            val parentBg = GradientDrawable().apply {
                val density = holder.itemView.resources.displayMetrics.density
                cornerRadius = 10f * density
                setColor(p.cardInner)
                setStroke((1 * density).toInt(), p.cardBorderSubtle)
            }
            holder.iconContainer.background = parentBg
            holder.name.text = holder.itemView.context.getString(R.string.explorer_parent_dir)
            holder.name.setTextColor(p.textSecondary)
            holder.badgeHidden.visibility = View.GONE
            holder.details.text = holder.itemView.context.getString(R.string.explorer_parent_dir_hint)
            holder.details.setTextColor(p.textMuted)
            holder.btnMore.visibility = View.GONE

            holder.card.setOnClickListener {
                HapticUtil.click(it)
                onParentClick()
            }
            return
        }

        val itemIndex = if (hasParent) position - 1 else position
        val item = displayItems[itemIndex]

        holder.name.text = item.name
        holder.btnMore.visibility = View.VISIBLE

        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            val folderIconColor = if (p.isDark) Color.parseColor("#FBBF24") else Color.rgb(217, 119, 6)
            holder.icon.imageTintList = ColorStateList.valueOf(folderIconColor)
            val folderBg = GradientDrawable().apply {
                val density = holder.itemView.resources.displayMetrics.density
                cornerRadius = 10f * density
                setColor(if (p.isDark) Color.parseColor("#332508") else Color.parseColor("#FFF8C5"))
                setStroke((1 * density).toInt(), if (p.isDark) Color.parseColor("#78500C") else Color.parseColor("#F5E08A"))
            }
            holder.iconContainer.background = folderBg
            holder.name.setTextColor(p.textPrimary)
            val itemsCount = holder.itemView.context.getString(R.string.explorer_items_count_fmt, item.fileCount)
            val permStr = if (item.permissions.isNotEmpty()) item.permissions else "drwxr-xr-x"
            val ownerStr = if (item.owner.isNotEmpty()) item.owner else "root:root"
            holder.details.text = "$itemsCount - $permStr - $ownerStr"
            holder.details.setTextColor(p.textSecondary)
        } else {
            holder.icon.setImageResource(R.drawable.ic_file)
            holder.icon.imageTintList = ColorStateList.valueOf(item.fileType.color)
            val fileBg = GradientDrawable().apply {
                val density = holder.itemView.resources.displayMetrics.density
                cornerRadius = 10f * density
                setColor(p.cardInner)
                setStroke((1 * density).toInt(), p.cardBorderSubtle)
            }
            holder.iconContainer.background = fileBg
            holder.name.setTextColor(p.textPrimary)
            val sizeStr = item.formattedSize()
            val permStr = if (item.permissions.isNotEmpty()) item.permissions else "-rw-r--r--"
            val ownerStr = if (item.owner.isNotEmpty()) item.owner else "root:root"
            holder.details.text = "$sizeStr - $permStr - $ownerStr"
            holder.details.setTextColor(p.textSecondary)
        }

        if (item.isHidden) {
            holder.badgeHidden.visibility = View.VISIBLE
            ThemeManager.stylePill(holder.badgeHidden, PillType.GRAY, p)
        } else {
            holder.badgeHidden.visibility = View.GONE
        }

        holder.card.setOnClickListener {
            HapticUtil.click(it)
            onItemClick(item)
        }

        holder.btnMore.setOnClickListener {
            HapticUtil.click(it)
            onMoreClick(item)
        }
    }
}
