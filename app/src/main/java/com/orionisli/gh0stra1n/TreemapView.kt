package com.orionisli.gh0stra1n

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat

class TreemapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rootNode: FileNode? = null
    private var highlightedNode: FileNode? = null
    var onNodeClicked: ((FileNode) -> Unit)? = null

    // 绘制画笔
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tileBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(40, 0, 0, 0)
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(10).toFloat()
        setShadowLayer(dp(2).toFloat(), 1f, 1f, Color.argb(200, 0, 0, 0))
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(226, 232, 240) // #E2E8F0
        textSize = dp(8).toFloat()
        setShadowLayer(dp(2).toFloat(), 1f, 1f, Color.argb(200, 0, 0, 0))
    }

    private val highlightGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(120, 220, 38, 38) // 50% #DC2626
        strokeWidth = dp(5).toFloat()
    }
    private val highlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(220, 38, 38) // 纯正鲜红 #DC2626
        strokeWidth = dp(2.8f)
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(220, 38, 38)
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(9).toFloat()
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        runCatching {
            val mapleTf = ResourcesCompat.getFont(context, R.font.maple_mono_regular)
            if (mapleTf != null) {
                textPaint.typeface = mapleTf
                subTextPaint.typeface = mapleTf
                badgeTextPaint.typeface = mapleTf
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    fun setRootNode(root: FileNode?) {
        rootNode = root
        highlightedNode = null
        if (width > 0 && height > 0 && root != null) {
            SquarifiedTreemap.layout(root, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        }
        invalidate()
    }

    fun setHighlightedNode(node: FileNode?) {
        highlightedNode = node
        invalidate()
    }

    fun getHighlightedNode(): FileNode? = highlightedNode

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val root = rootNode
        if (w > 0 && h > 0 && root != null) {
            SquarifiedTreemap.layout(root, RectF(0f, 0f, w.toFloat(), h.toFloat()))
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val root = rootNode ?: return false
                val clicked = root.findDeepest(event.x, event.y)
                if (clicked != null) {
                    setHighlightedNode(clicked)
                    onNodeClicked?.invoke(clicked)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val root = rootNode ?: return

        // 递归渲染所有文件叶子/可视目录
        drawNode(canvas, root)

        highlightedNode?.let { node ->
            val r = node.rect
            if (!r.isEmpty && r.width() > 1f && r.height() > 1f) {
                canvas.drawRect(r, highlightGlowPaint)
                canvas.drawRect(r, highlightBorderPaint)
            }
        }
    }

    private fun drawNode(canvas: Canvas, node: FileNode) {
        if (node.rect.isEmpty || node.rect.width() <= 0.5f || node.rect.height() <= 0.5f) return

        if (!node.isDirectory || node.children.isEmpty() || node.rect.width() < dp(14) || node.rect.height() < dp(14)) {
            fillPaint.color = node.fileType.color
            canvas.drawRect(node.rect, fillPaint)
            canvas.drawRect(node.rect, tileBorderPaint)

            val w = node.rect.width()
            val h = node.rect.height()
            if (w >= dp(36) && h >= dp(18)) {
                val clipId = canvas.save()
                canvas.clipRect(node.rect)

                val name = if (node.isDirectory) "${node.name}/" else node.name
                val sizeText = node.formattedSize()

                val y1 = node.rect.top + dp(12)
                canvas.drawText(name, node.rect.left + dp(4), y1, textPaint)

                if (h >= dp(28)) {
                    val y2 = y1 + dp(10)
                    canvas.drawText(sizeText, node.rect.left + dp(4), y2, subTextPaint)
                }

                canvas.restoreToCount(clipId)
            }
        } else {
            fillPaint.color = FileType.DIRECTORY.color
            canvas.drawRect(node.rect, fillPaint)
            canvas.drawRect(node.rect, tileBorderPaint)

            // 递归绘制其子节点
            for (child in node.children) {
                drawNode(canvas, child)
            }
        }
    }
}
