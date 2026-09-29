package com.orionisli.gh0stra1n

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun rebuildBreadcrumbBar(
    container: LinearLayout,
    scroller: HorizontalScrollView,
    currentDir: FileNode,
    onCrumb: (FileNode) -> Unit,
) {
    container.removeAllViews()
    val crumbs = currentDir.getBreadcrumbList()
    val ctx = container.context
    val p = ThemeManager.getCurrentPalette(ctx)

    for (i in crumbs.indices) {
        val node = crumbs[i]
        val isLast = i == crumbs.size - 1

        val crumbView = TextView(ctx).apply {
            text = if (node.path == "/") ctx.getString(R.string.explorer_root_breadcrumb) else "${node.name}/"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(if (isLast) p.accent else p.textSecondary)
            val density = resources.displayMetrics.density
            val pillBg = GradientDrawable().apply {
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
                onCrumb(node)
            }
        }
        ViewAnimUtil.addPressScaleEffect(crumbView)
        container.addView(crumbView)

        if (!isLast) {
            val arrow = TextView(ctx).apply {
                text = " > "
                textSize = 10f
                setTextColor(p.textMuted)
            }
            container.addView(arrow)
        }
    }

    scroller.post {
        scroller.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
    }
}

fun attachHeaderListInsets(root: View, header: View, list: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

        with(header) { setPadding(dp(16), statusBars.top + dp(6), dp(16), dp(6)) }
        with(list) { setPadding(0, 0, 0, navBars.bottom + dp(16)) }

        insets
    }
}

fun showTreeLoadFailure(activity: AppCompatActivity, titleRes: Int, error: Throwable, retry: () -> Unit) {
    MaterialAlertDialogBuilder(activity)
        .setTitle(titleRes)
        .setMessage(error.message ?: "")
        .setPositiveButton(R.string.btn_retry) { _, _ -> retry() }
        .setNegativeButton(R.string.back) { _, _ -> activity.finish() }
        .show()
}
