package com.orionisli.gh0stra1n

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class LiquidGlassBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class TabInfo(
        val iconRes: Int,
        val titleRes: Int
    )

    private val tabs = listOf(
        TabInfo(R.drawable.ic_tab_control, R.string.tab_control),
        TabInfo(R.drawable.ic_tab_partitions, R.string.tab_partitions),
        TabInfo(R.drawable.ic_tab_logs, R.string.tab_logs),
        TabInfo(R.drawable.ic_tab_settings, R.string.tab_settings),
        TabInfo(R.drawable.ic_tab_about, R.string.tab_about)
    )

    private val cardView: MaterialCardView
    private val innerContainer: FrameLayout
    private val indicatorView: View
    private val tabsLayout: LinearLayout

    private val tabItemViews = mutableListOf<View>()
    private val iconViews = mutableListOf<ImageView>()
    private val textViews = mutableListOf<TextView>()

    private var selectedIndex = 0
    private var hoverIndex = 0
    private var isLiquidDragging = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isLongPressScheduled = false
    private var dragStartTouchX = 0f
    private var initialIndicatorTranslationX = 0f

    private var palette: ThemePalette = ThemeManager.PALETTE_SAKURA

    var onTabSelectedListener: ((Int) -> Unit)? = null
    var onTabDragListener: ((currentPos: Float) -> Unit)? = null
    var onTabDragEndListener: ((fromPos: Float, finalIndex: Int) -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, dp(4), 0, dp(6))

        cardView = MaterialCardView(context).apply {
            radius = dp(29).toFloat()
            cardElevation = dp(3.5f).toFloat()
            strokeWidth = dp(1)
            preventCornerOverlap = false
            useCompatPadding = false
            clipToOutline = true
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(58)).apply {
                gravity = Gravity.CENTER
            }
        }

        innerContainer = FrameLayout(context).apply {
            setPadding(dp(6), dp(4), dp(6), dp(4))
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        indicatorView = View(context).apply {
            elevation = dp(2).toFloat()
            layoutParams = LayoutParams(0, dp(50)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        innerContainer.addView(indicatorView)

        tabsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = tabs.size.toFloat()
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(6).toFloat()
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        tabs.forEachIndexed { index, tabInfo ->
            val itemView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            }

            val iconView = ImageView(context).apply {
                setImageResource(tabInfo.iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            val textView = TextView(context).apply {
                text = context.getString(tabInfo.titleRes)
                textSize = 10.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_HORIZONTAL
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            }

            itemView.addView(iconView)
            itemView.addView(textView)
            tabsLayout.addView(itemView)

            tabItemViews.add(itemView)
            iconViews.add(iconView)
            textViews.add(textView)
        }

        innerContainer.addView(tabsLayout)
        cardView.addView(innerContainer)
        addView(cardView)

        applyTheme(ThemeManager.getCurrentPalette(context))

        post {
            measureIndicator(selectedIndex, animate = false)
            updateTabVisuals(selectedIndex)
        }
    }

    private val longPressRunnable = Runnable {
        isLongPressScheduled = false
        enterLiquidGlassMode()
    }

    private fun enterLiquidGlassMode() {
        if (isLiquidDragging) return
        isLiquidDragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        HapticUtil.heavyClick()

        val innerWidth = innerContainer.width - innerContainer.paddingLeft - innerContainer.paddingRight
        val tabWidth = if (innerWidth > 0) innerWidth / tabs.size.toFloat() else 1f

        if (hoverIndex != selectedIndex) {
            selectTab(hoverIndex, notify = true, animate = false)
        }

        dragStartTouchX = downX
        initialIndicatorTranslationX = selectedIndex * tabWidth

        indicatorView.animate()
            .scaleX(1.06f)
            .scaleY(1.18f)
            .setDuration(130)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (isLiquidDragging) {
                    indicatorView.animate()
                        .scaleX(1.02f)
                        .scaleY(1.04f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
            .start()

        renderIndicatorBackground(isLiquidGlass = true)
    }

    private fun exitLiquidGlassMode(targetIndex: Int) {
        if (!isLiquidDragging) return
        isLiquidDragging = false
        parent?.requestDisallowInterceptTouchEvent(false)

        indicatorView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        renderIndicatorBackground(isLiquidGlass = false)
        HapticUtil.click()

        val innerWidth = innerContainer.width - innerContainer.paddingLeft - innerContainer.paddingRight
        val tabWidth = if (innerWidth > 0) innerWidth / tabs.size.toFloat() else 1f
        val fromPos = (indicatorView.translationX / tabWidth).coerceIn(-0.2f, (tabs.size - 1) + 0.2f)

        measureIndicator(targetIndex, animate = true)
        selectedIndex = targetIndex
        hoverIndex = targetIndex
        updateTabVisuals(targetIndex)

        onTabDragEndListener?.invoke(fromPos, targetIndex)
    }

    private fun renderIndicatorBackground(isLiquidGlass: Boolean) {
        val radiusPx = dp(25).toFloat()
        val bgDrawable = GradientDrawable().apply {
            cornerRadius = radiusPx
            if (isLiquidGlass) {
                setColor(palette.glassBg)
                setStroke(dp(1.5f), palette.glassBorder)
            } else {
                setColor(palette.tabIndicatorBg)
                val strokeColor = if (palette.isDark) Color.argb(40, 255, 255, 255) else Color.argb(25, 0, 0, 0)
                setStroke(dp(0.8f), strokeColor)
            }
        }
        indicatorView.background = bgDrawable
        indicatorView.elevation = dp(2).toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val innerWidth = innerContainer.width - innerContainer.paddingLeft - innerContainer.paddingRight
        if (innerWidth <= 0) return super.onTouchEvent(event)
        val tabWidth = innerWidth / tabs.size.toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                val localX = (event.x - innerContainer.paddingLeft).coerceIn(0f, innerWidth.toFloat())
                hoverIndex = (localX / tabWidth).toInt().coerceIn(0, tabs.size - 1)

                isLongPressScheduled = true
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, 150)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY

                if (!isLiquidDragging) {
                    if (Math.abs(dx) > dp(12) && Math.abs(dx) > Math.abs(dy)) {
                        handler.removeCallbacks(longPressRunnable)
                        isLongPressScheduled = false
                        enterLiquidGlassMode()
                    } else if (Math.abs(dy) > dp(18)) {
                        handler.removeCallbacks(longPressRunnable)
                        isLongPressScheduled = false
                    }
                }

                if (isLiquidDragging) {
                    val moveDelta = event.x - dragStartTouchX
                    val minX = 0f
                    val maxX = innerWidth - tabWidth
                    val rawTargetX = initialIndicatorTranslationX + moveDelta
                    val targetX = when {
                        rawTargetX < minX -> minX - (minX - rawTargetX) * 0.35f
                        rawTargetX > maxX -> maxX + (rawTargetX - maxX) * 0.35f
                        else -> rawTargetX
                    }.coerceIn(minX - dp(16), maxX + dp(16))
                    indicatorView.translationX = targetX

                    val nearestTab = ((targetX + tabWidth / 2f) / tabWidth).toInt().coerceIn(0, tabs.size - 1)
                    if (nearestTab != hoverIndex) {
                        hoverIndex = nearestTab
                        HapticUtil.detentTick()
                        updateTabColors(hoverIndex)
                    }

                    val currentPos = (targetX / tabWidth).coerceIn(-0.15f, (tabs.size - 1) + 0.15f)
                    onTabDragListener?.invoke(currentPos)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isLongPressScheduled) {
                    handler.removeCallbacks(longPressRunnable)
                    isLongPressScheduled = false
                }

                if (isLiquidDragging) {
                    exitLiquidGlassMode(hoverIndex)
                    return true
                } else {
                    val elapsed = System.currentTimeMillis() - downTime
                    val dx = Math.abs(event.x - downX)
                    if (elapsed < 300 && dx < touchSlop) {
                        val localX = (event.x - innerContainer.paddingLeft).coerceIn(0f, innerWidth.toFloat())
                        val targetTab = (localX / tabWidth).toInt().coerceIn(0, tabs.size - 1)
                        HapticUtil.tick()
                        selectTab(targetTab, notify = true, animate = true)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isLongPressScheduled) {
                    handler.removeCallbacks(longPressRunnable)
                    isLongPressScheduled = false
                }
                if (isLiquidDragging) {
                    exitLiquidGlassMode(selectedIndex)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(longPressRunnable)
        isLongPressScheduled = false
        isLiquidDragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    fun selectTab(index: Int, notify: Boolean = true, animate: Boolean = true) {
        val targetIndex = index.coerceIn(0, tabs.size - 1)
        selectedIndex = targetIndex
        hoverIndex = targetIndex
        updateTabVisuals(targetIndex)
        measureIndicator(targetIndex, animate = animate)

        if (notify) {
            onTabSelectedListener?.invoke(targetIndex)
        }
    }

    fun getSelectedTab(): Int = selectedIndex

    private fun measureIndicator(index: Int, animate: Boolean) {
        val innerWidth = innerContainer.width - innerContainer.paddingLeft - innerContainer.paddingRight
        if (innerWidth <= 0) return
        val tabWidth = innerWidth / tabs.size.toFloat()

        val lp = indicatorView.layoutParams as LayoutParams
        lp.width = tabWidth.toInt()
        indicatorView.layoutParams = lp

        val targetTranslationX = index * tabWidth

        if (animate) {
            val anim = ValueAnimator.ofFloat(indicatorView.translationX, targetTranslationX).apply {
                duration = 260
                interpolator = OvershootInterpolator(1.05f)
                addUpdateListener {
                    indicatorView.translationX = it.animatedValue as Float
                }
            }
            anim.start()
        } else {
            indicatorView.translationX = targetTranslationX
        }
    }

    private fun updateTabVisuals(activeIdx: Int) {
        tabs.indices.forEach { i ->
            val isActive = (i == activeIdx)
            val icon = iconViews[i]
            val text = textViews[i]

            val targetColor = if (isActive) palette.accent else palette.textSecondary
            icon.imageTintList = ColorStateList.valueOf(targetColor)
            text.setTextColor(targetColor)

            val targetScale = if (isActive) 1.04f else 1.0f
            tabItemViews[i].animate().scaleX(targetScale).scaleY(targetScale).setDuration(160).start()
        }
    }

    private fun updateTabColors(activeIdx: Int) {
        tabs.indices.forEach { i ->
            val isActive = (i == activeIdx)
            val icon = iconViews[i]
            val text = textViews[i]

            val targetColor = if (isActive) palette.accent else palette.textSecondary
            icon.imageTintList = ColorStateList.valueOf(targetColor)
            text.setTextColor(targetColor)
        }
    }

    fun refreshTitles() {
        tabs.forEachIndexed { index, tabInfo ->
            textViews[index].text = context.getString(tabInfo.titleRes)
        }
    }

    fun applyTheme(p: ThemePalette) {
        this.palette = p
        cardView.setCardBackgroundColor(p.tabBarBg)
        cardView.strokeColor = p.tabBarBorder
        renderIndicatorBackground(isLiquidGlass = isLiquidDragging)
        updateTabVisuals(selectedIndex)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            measureIndicator(selectedIndex, animate = false)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}
