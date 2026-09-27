package com.orionisli.gh0stra1n

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object SquarifiedTreemap {

    private const val MIN_RECURSION_PX = 8f // 目录递归细分的最小像素尺寸阈值

    fun layout(root: FileNode, bounds: RectF) {
        root.rect.set(bounds)
        if (root.children.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) return

        layoutChildren(root.children, bounds)
    }

    private fun layoutChildren(children: List<FileNode>, bounds: RectF) {
        if (children.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) return

        fun getWeight(n: FileNode): Double = if (n.size > 0L) n.size.toDouble() else (if (n.isDirectory) 4096.0 else 512.0)

        val sorted = children.sortedByDescending { getWeight(it) }
        val totalWeight = sorted.sumOf { getWeight(it) }
        if (totalWeight <= 0.0) {
            layoutEvenly(children, bounds)
            return
        }

        val totalArea = bounds.width().toDouble() * bounds.height().toDouble()

        // 将每个节点的物理权重映射为像素面积
        val itemsWithArea = sorted.map { it to (getWeight(it) / totalWeight * totalArea) }

        squarify(itemsWithArea, mutableListOf(), bounds)

        // 递归为各子目录布局（若子矩形面积充足）
        for (node in sorted) {
            if (node.isDirectory && node.children.isNotEmpty()) {
                val w = node.rect.width()
                val h = node.rect.height()
                if (w >= MIN_RECURSION_PX && h >= MIN_RECURSION_PX) {
                    // 内缩 0.5dp 留出轮廓间隙
                    val innerBounds = RectF(
                        node.rect.left + 0.5f,
                        node.rect.top + 0.5f,
                        node.rect.right - 0.5f,
                        node.rect.bottom - 0.5f
                    )
                    layoutChildren(node.children, innerBounds)
                }
            }
        }
    }

    private fun squarify(
        children: List<Pair<FileNode, Double>>,
        row: MutableList<Pair<FileNode, Double>>,
        bounds: RectF
    ) {
        if (children.isEmpty()) {
            layoutRow(row, bounds)
            return
        }

        val shorterSide = min(bounds.width(), bounds.height()).toDouble()
        if (shorterSide <= 0.0) return

        val head = children.first()
        val tail = children.drop(1)

        if (row.isEmpty()) {
            row.add(head)
            squarify(tail, row, bounds)
            return
        }

        val worstCurrent = worstAspectRatio(row, shorterSide)
        val candidateRow = ArrayList(row).apply { add(head) }
        val worstCandidate = worstAspectRatio(candidateRow, shorterSide)

        if (worstCandidate <= worstCurrent) {
            row.add(head)
            squarify(tail, row, bounds)
        } else {
            val remainingBounds = layoutRow(row, bounds)
            val newRow = mutableListOf(head)
            squarify(tail, newRow, remainingBounds)
        }
    }

    private fun worstAspectRatio(row: List<Pair<FileNode, Double>>, sideLength: Double): Double {
        if (row.isEmpty() || sideLength <= 0.0) return Double.MAX_VALUE
        val totalArea = row.sumOf { it.second }
        if (totalArea <= 0.0) return Double.MAX_VALUE

        val s2 = sideLength * sideLength
        val area2 = totalArea * totalArea
        var maxAspect = 0.0

        for (item in row) {
            val r = item.second
            if (r <= 0.0) continue
            val aspect = max((s2 * r) / area2, area2 / (s2 * r))
            if (aspect > maxAspect) {
                maxAspect = aspect
            }
        }
        return if (maxAspect == 0.0) Double.MAX_VALUE else maxAspect
    }

    private fun layoutRow(row: List<Pair<FileNode, Double>>, bounds: RectF): RectF {
        if (row.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) return bounds

        val isHorizontal = bounds.width() >= bounds.height()
        val totalArea = row.sumOf { it.second }

        if (isHorizontal) {
            val h = bounds.height().toDouble()
            val rowWidth = (totalArea / h).toFloat().coerceIn(0f, bounds.width())
            var currentY = bounds.top

            for ((node, area) in row) {
                val itemHeight = if (rowWidth > 0f) (area / rowWidth).toFloat() else 0f
                val nextY = (currentY + itemHeight).coerceAtMost(bounds.bottom)
                node.rect.set(bounds.left, currentY, bounds.left + rowWidth, nextY)
                currentY = nextY
            }

            return RectF(bounds.left + rowWidth, bounds.top, bounds.right, bounds.bottom)
        } else {
            val w = bounds.width().toDouble()
            val rowHeight = (totalArea / w).toFloat().coerceIn(0f, bounds.height())
            var currentX = bounds.left

            for ((node, area) in row) {
                val itemWidth = if (rowHeight > 0f) (area / rowHeight).toFloat() else 0f
                val nextX = (currentX + itemWidth).coerceAtMost(bounds.right)
                node.rect.set(currentX, bounds.top, nextX, bounds.top + rowHeight)
                currentX = nextX
            }

            return RectF(bounds.left, bounds.top + rowHeight, bounds.right, bounds.bottom)
        }
    }

    private fun layoutEvenly(children: List<FileNode>, bounds: RectF) {
        if (children.isEmpty()) return
        val count = children.size
        val w = bounds.width() / count
        var x = bounds.left
        for (c in children) {
            c.rect.set(x, bounds.top, x + w, bounds.bottom)
            if (c.isDirectory && c.children.isNotEmpty()) {
                layoutEvenly(c.children, RectF(c.rect))
            }
            x += w
        }
    }
}
