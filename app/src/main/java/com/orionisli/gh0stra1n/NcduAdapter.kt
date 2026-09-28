package com.orionisli.gh0stra1n

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class NcduAdapter(
    private val onItemClick: (FileNode) -> Unit,
    private val onParentClick: () -> Unit
) : RecyclerView.Adapter<NcduAdapter.ViewHolder>() {

    private var currentDir: FileNode? = null
    private var allItems: List<FileNode> = emptyList()
    private var displayItems: List<FileNode> = emptyList()
    private var selectedNode: FileNode? = null
    private var parentTotalSize: Long = 1L
    private var currentFilter: String = ""

    fun setDirectory(dir: FileNode, selected: FileNode? = null) {
        currentDir = dir
        selectedNode = selected
        parentTotalSize = dir.size.coerceAtLeast(1L)
        allItems = dir.children.sortedByDescending { it.size }
        applyFilter()
    }

    fun setFilter(query: String) {
        currentFilter = query.trim().lowercase()
        applyFilter()
    }

    private fun applyFilter() {
        displayItems = if (currentFilter.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.name.lowercase().contains(currentFilter) }
        }
        notifyDataSetChanged()
    }

    fun setSelected(selected: FileNode?) {
        selectedNode = selected
        notifyDataSetChanged()
    }

    private val hasParent: Boolean
        get() = currentDir?.parent != null && currentFilter.isEmpty()

    override fun getItemCount(): Int {
        return (if (hasParent) 1 else 0) + displayItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (hasParent && position == 0) 0 else 1
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_ncdu_item)
        val icon: ImageView = view.findViewById(R.id.img_icon)
        val name: TextView = view.findViewById(R.id.txt_name)
        val badgeHidden: TextView = view.findViewById(R.id.badge_hidden)
        val size: TextView = view.findViewById(R.id.txt_size)
        val txtMeta: TextView = view.findViewById(R.id.txt_meta)
        val layoutBar: LinearLayout = view.findViewById(R.id.layout_bar)
        val progressBar: LinearProgressIndicator = view.findViewById(R.id.progress_ratio)
        val ratioDesc: TextView = view.findViewById(R.id.txt_ratio_desc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ncdu_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = ThemeManager.getCurrentPalette(holder.itemView.context)

        if (hasParent && position == 0) {
            holder.icon.setImageResource(R.drawable.ic_arrow_up)
            holder.icon.imageTintList = ColorStateList.valueOf(p.textSecondary)
            holder.name.text = holder.itemView.context.getString(R.string.explorer_parent_dir)
            holder.name.setTextColor(p.textSecondary)
            holder.badgeHidden.visibility = View.GONE
            holder.txtMeta.visibility = View.GONE
            holder.size.text = ""
            holder.layoutBar.visibility = View.GONE
            holder.card.strokeColor = p.cardBorder
            holder.card.strokeWidth = dp(holder.itemView, 1)
            holder.card.setCardBackgroundColor(p.cardInner)

            holder.card.setOnClickListener {
                HapticUtil.click(it)
                onParentClick()
            }
            return
        }

        val itemIndex = if (hasParent) position - 1 else position
        val item = displayItems[itemIndex]
        val isSelected = (item == selectedNode)

        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            val folderColor = if (p.isDark) Color.parseColor("#FBBF24") else Color.rgb(217, 119, 6)
            holder.icon.imageTintList = ColorStateList.valueOf(folderColor)
            holder.name.text = "${item.name}/"
            holder.name.setTextColor(p.textPrimary)
        } else {
            holder.icon.setImageResource(R.drawable.ic_file)
            holder.icon.imageTintList = ColorStateList.valueOf(item.fileType.color)
            holder.name.text = item.name
            holder.name.setTextColor(p.textPrimary)
        }

        if (item.isHidden) {
            holder.badgeHidden.visibility = View.VISIBLE
            ThemeManager.stylePill(holder.badgeHidden, PillType.GRAY, p)
        } else {
            holder.badgeHidden.visibility = View.GONE
        }

        if (item.permissions.isNotEmpty() || item.owner.isNotEmpty()) {
            holder.txtMeta.visibility = View.VISIBLE
            val permStr = if (item.permissions.isNotEmpty()) item.permissions else "-rw-r--r--"
            val ownerStr = if (item.owner.isNotEmpty()) item.owner else "root:root"
            holder.txtMeta.text = "$permStr - $ownerStr"
            holder.txtMeta.setTextColor(p.textSecondary)
        } else {
            holder.txtMeta.visibility = View.GONE
        }

        holder.size.text = item.formattedSize()
        holder.size.setTextColor(p.textPrimary)

        holder.layoutBar.visibility = View.VISIBLE
        val ratio = (item.size.toDouble() / parentTotalSize * 100.0).coerceIn(0.0, 100.0)
        holder.progressBar.progress = ratio.toInt()
        holder.progressBar.setIndicatorColor(item.fileType.color)
        holder.progressBar.trackColor = p.cardBorderSubtle

        val desc = if (item.isDirectory) {
            holder.itemView.context.getString(R.string.ncdu_dir_meta_fmt, ratio, item.fileCount)
        } else {
            "%.1f%%".format(ratio)
        }
        holder.ratioDesc.text = desc
        holder.ratioDesc.setTextColor(p.textSecondary)

        if (isSelected) {
            holder.card.strokeColor = Color.rgb(220, 38, 38) // #DC2626红边
            holder.card.strokeWidth = dp(holder.itemView, 2)
            holder.card.setCardBackgroundColor(if (p.isDark) Color.parseColor("#3B1818") else Color.parseColor("#FFF1F2"))
        } else {
            holder.card.strokeColor = p.cardBorder
            holder.card.strokeWidth = dp(holder.itemView, 1)
            holder.card.setCardBackgroundColor(p.cardBg)
        }

        holder.card.setOnClickListener {
            HapticUtil.click(it)
            onItemClick(item)
        }
    }

    private fun dp(view: View, v: Int): Int {
        return (v * view.resources.displayMetrics.density).toInt()
    }
}
