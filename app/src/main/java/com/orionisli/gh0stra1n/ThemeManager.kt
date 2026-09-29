package com.orionisli.gh0stra1n

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator

data class ThemePalette(
    val name: String,
    val windowBg: Int,
    val cardBg: Int,
    val cardInner: Int,
    val cardBorder: Int,
    val cardBorderSubtle: Int,
    val accent: Int,
    val accentPressed: Int,
    val accentLight: Int,
    val accentBg: Int,
    val tabBarBg: Int,
    val tabBarBorder: Int,
    val tabIndicatorBg: Int,
    val glassBg: Int,
    val glassBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    val isDark: Boolean
)

enum class PillType {
    GREEN, RED, YELLOW, GRAY, ACCENT
}

object ThemeManager {
    val PALETTE_BEIGE = ThemePalette(
        name = "beige",
        windowBg = Color.parseColor("#F6F4EE"),
        cardBg = Color.parseColor("#FFFFFF"),
        cardInner = Color.parseColor("#FAF8F5"),
        cardBorder = Color.parseColor("#E7E2D7"),
        cardBorderSubtle = Color.parseColor("#EFEBE4"),
        accent = Color.parseColor("#693FB4"),
        accentPressed = Color.parseColor("#542E96"),
        accentLight = Color.parseColor("#8257D6"),
        accentBg = Color.parseColor("#F0EBFA"),
        tabBarBg = Color.parseColor("#FFFFFF"),
        tabBarBorder = Color.parseColor("#E3DDD2"),
        tabIndicatorBg = Color.parseColor("#F0EBFA"),
        glassBg = Color.argb(215, 255, 255, 255),
        glassBorder = Color.argb(190, 255, 255, 255),
        textPrimary = Color.parseColor("#1C1A17"),
        textSecondary = Color.parseColor("#6E6961"),
        textMuted = Color.parseColor("#9C968B"),
        isDark = false
    )

    val PALETTE_SLATE = ThemePalette(
        name = "slate",
        windowBg = Color.parseColor("#F3F5F7"),
        cardBg = Color.parseColor("#FFFFFF"),
        cardInner = Color.parseColor("#E8EDF2"),
        cardBorder = Color.parseColor("#CCD5DE"),
        cardBorderSubtle = Color.parseColor("#DCE3EA"),
        accent = Color.parseColor("#1D6FD8"),
        accentPressed = Color.parseColor("#1558B0"),
        accentLight = Color.parseColor("#3B82F6"),
        accentBg = Color.parseColor("#E8F1FC"),
        tabBarBg = Color.parseColor("#FFFFFF"),
        tabBarBorder = Color.parseColor("#CCD5DE"),
        tabIndicatorBg = Color.parseColor("#E8F1FC"),
        glassBg = Color.argb(215, 255, 255, 255),
        glassBorder = Color.argb(190, 255, 255, 255),
        textPrimary = Color.parseColor("#111827"),
        textSecondary = Color.parseColor("#374151"),
        textMuted = Color.parseColor("#6B7280"),
        isDark = false
    )

    val PALETTE_CYBER = ThemePalette(
        name = "cyber",
        windowBg = Color.parseColor("#080B12"),
        cardBg = Color.parseColor("#101626"),
        cardInner = Color.parseColor("#1B2338"),
        cardBorder = Color.parseColor("#283550"),
        cardBorderSubtle = Color.parseColor("#1D273B"),
        accent = Color.parseColor("#8B5CF6"),
        accentPressed = Color.parseColor("#7C3AED"),
        accentLight = Color.parseColor("#A78BFA"),
        accentBg = Color.parseColor("#2C204E"),
        tabBarBg = Color.parseColor("#101626"),
        tabBarBorder = Color.parseColor("#283550"),
        tabIndicatorBg = Color.parseColor("#2C204E"),
        glassBg = Color.argb(210, 16, 22, 38),
        glassBorder = Color.argb(180, 139, 92, 246),
        textPrimary = Color.parseColor("#FFFFFF"),
        textSecondary = Color.parseColor("#CBD5E1"),
        textMuted = Color.parseColor("#94A3B8"),
        isDark = true
    )

    val PALETTE_MATCHA = ThemePalette(
        name = "matcha",
        windowBg = Color.parseColor("#F4F6F0"),
        cardBg = Color.parseColor("#FFFFFF"),
        cardInner = Color.parseColor("#EAF0E2"),
        cardBorder = Color.parseColor("#CFDCC2"),
        cardBorderSubtle = Color.parseColor("#DFEAD4"),
        accent = Color.parseColor("#2F855A"),
        accentPressed = Color.parseColor("#226544"),
        accentLight = Color.parseColor("#38A169"),
        accentBg = Color.parseColor("#E6F4EA"),
        tabBarBg = Color.parseColor("#FFFFFF"),
        tabBarBorder = Color.parseColor("#CFDCC2"),
        tabIndicatorBg = Color.parseColor("#E6F4EA"),
        glassBg = Color.argb(215, 255, 255, 255),
        glassBorder = Color.argb(190, 255, 255, 255),
        textPrimary = Color.parseColor("#1A202C"),
        textSecondary = Color.parseColor("#3F4E38"),
        textMuted = Color.parseColor("#718096"),
        isDark = false
    )

    val PALETTE_NORD = ThemePalette(
        name = "nord",
        windowBg = Color.parseColor("#0A0F1D"),
        cardBg = Color.parseColor("#131B2E"),
        cardInner = Color.parseColor("#1B253D"),
        cardBorder = Color.parseColor("#2C3A5A"),
        cardBorderSubtle = Color.parseColor("#1E283F"),
        accent = Color.parseColor("#00E5BC"),
        accentPressed = Color.parseColor("#00BFA0"),
        accentLight = Color.parseColor("#4EECD2"),
        accentBg = Color.parseColor("#143538"),
        tabBarBg = Color.parseColor("#131B2E"),
        tabBarBorder = Color.parseColor("#2C3A5A"),
        tabIndicatorBg = Color.parseColor("#143538"),
        glassBg = Color.argb(215, 19, 27, 46),
        glassBorder = Color.argb(180, 0, 229, 188),
        textPrimary = Color.parseColor("#FFFFFF"),
        textSecondary = Color.parseColor("#CBD5E1"),
        textMuted = Color.parseColor("#94A3B8"),
        isDark = true
    )

    val PALETTE_SAKURA = ThemePalette(
        name = "sakura",
        windowBg = Color.parseColor("#FFF8F9"),
        cardBg = Color.parseColor("#FFFFFF"),
        cardInner = Color.parseColor("#FFF0F3"),
        cardBorder = Color.parseColor("#FCD5DF"),
        cardBorderSubtle = Color.parseColor("#FFE4EB"),
        accent = Color.parseColor("#E14979"),
        accentPressed = Color.parseColor("#C2185B"),
        accentLight = Color.parseColor("#F06292"),
        accentBg = Color.parseColor("#FCE4EC"),
        tabBarBg = Color.parseColor("#FFFFFF"),
        tabBarBorder = Color.parseColor("#FCD5DF"),
        tabIndicatorBg = Color.parseColor("#FCE4EC"),
        glassBg = Color.argb(215, 255, 255, 255),
        glassBorder = Color.argb(190, 255, 255, 255),
        textPrimary = Color.parseColor("#341B26"),
        textSecondary = Color.parseColor("#6A4A57"),
        textMuted = Color.parseColor("#A17F8D"),
        isDark = false
    )

    private val allPrimaryTextColors = setOf(
        Color.parseColor("#1C1A17"),
        Color.parseColor("#212529"),
        Color.parseColor("#111827"),
        Color.parseColor("#0F172A"),
        Color.parseColor("#F1F5F9"),
        Color.parseColor("#F3F4F6"),
        Color.parseColor("#FFFFFF"),
        Color.parseColor("#1D261A"),
        Color.parseColor("#1A202C"),
        Color.parseColor("#ECEFF4"),
        Color.parseColor("#F0FDFA"),
        Color.parseColor("#341B26"),
        Color.parseColor("#2B231D"),
    )

    private val allSecondaryTextColors = setOf(
        Color.parseColor("#6E6961"),
        Color.parseColor("#495057"),
        Color.parseColor("#374151"),
        Color.parseColor("#475569"),
        Color.parseColor("#94A3B8"),
        Color.parseColor("#9CA3AF"),
        Color.parseColor("#E2E8F0"),
        Color.parseColor("#4A5546"),
        Color.parseColor("#3F4E38"),
        Color.parseColor("#D8DEE9"),
        Color.parseColor("#CCFBF1"),
        Color.parseColor("#6A4A57"),
        Color.parseColor("#57606A"),
    )

    private val allMutedTextColors = setOf(
        Color.parseColor("#9C968B"),
        Color.parseColor("#868E96"),
        Color.parseColor("#6B7280"),
        Color.parseColor("#64748B"),
        Color.parseColor("#82907E"),
        Color.parseColor("#718096"),
        Color.parseColor("#7B88A1"),
        Color.parseColor("#9AA7BD"),
        Color.parseColor("#7CD0D8"),
        Color.parseColor("#A17F8D"),
        Color.parseColor("#8C867B"),
    )

    fun getPalette(name: String): ThemePalette {
        return when (name.lowercase()) {
            "beige" -> PALETTE_BEIGE
            "slate" -> PALETTE_SLATE
            "cyber" -> PALETTE_CYBER
            "matcha" -> PALETTE_MATCHA
            "nord", "aurora" -> PALETTE_NORD
            "sakura" -> PALETTE_SAKURA
            else -> PALETTE_SAKURA
        }
    }

    fun getCurrentPalette(context: Context): ThemePalette =
        getPalette(SettingsStore(context).uiTheme)

    fun applyStyleTheme(activity: Activity) {
        val style = when (SettingsStore(activity).uiTheme.lowercase()) {
            "beige" -> R.style.Theme_Gh0stra1n_Beige
            "slate" -> R.style.Theme_Gh0stra1n_Slate
            "cyber" -> R.style.Theme_Gh0stra1n_CyberDark
            "matcha" -> R.style.Theme_Gh0stra1n_Matcha
            "nord", "aurora" -> R.style.Theme_Gh0stra1n_Nord
            else -> R.style.Theme_Gh0stra1n_Sakura
        }
        activity.setTheme(style)
    }

    fun stylePill(view: TextView, type: PillType, p: ThemePalette) {
        val density = view.resources.displayMetrics.density
        val (bg, stroke, text) = when (type) {
            PillType.GREEN -> if (p.isDark) {
                Triple(Color.parseColor("#143823"), Color.parseColor("#22C55E"), Color.parseColor("#4ADE80"))
            } else {
                Triple(Color.parseColor("#EAF5EC"), Color.parseColor("#BEE3C8"), Color.parseColor("#1A7F37"))
            }
            PillType.RED -> if (p.isDark) {
                Triple(Color.parseColor("#381619"), Color.parseColor("#EF4444"), Color.parseColor("#F87171"))
            } else {
                Triple(Color.parseColor("#FFEBE9"), Color.parseColor("#FFC1BA"), Color.parseColor("#CF222E"))
            }
            PillType.YELLOW -> if (p.isDark) {
                Triple(Color.parseColor("#332508"), Color.parseColor("#F59E0B"), Color.parseColor("#FBBF24"))
            } else {
                Triple(Color.parseColor("#FFF8C5"), Color.parseColor("#F5E08A"), Color.parseColor("#9A6700"))
            }
            PillType.GRAY -> if (p.isDark) {
                Triple(Color.parseColor("#1E293B"), Color.parseColor("#475569"), Color.parseColor("#94A3B8"))
            } else {
                Triple(Color.parseColor("#F0EDE6"), Color.parseColor("#DED8CD"), Color.parseColor("#57606A"))
            }
            PillType.ACCENT -> {
                Triple(p.accentBg, p.accent, p.accent)
            }
        }
        val pillBg = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(bg)
            setStroke((1 * density).toInt(), stroke)
        }
        view.background = pillBg
        view.setTextColor(text)
    }

    fun applyToActivity(activity: Activity) {
        val p = getCurrentPalette(activity)
        val window = activity.window

        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.isAppearanceLightStatusBars = !p.isDark
        wic.isAppearanceLightNavigationBars = !p.isDark

        window.statusBarColor = p.windowBg
        window.navigationBarColor = p.windowBg
        window.decorView.setBackgroundColor(p.windowBg)

        activity.findViewById<View>(R.id.root_layout)?.setBackgroundColor(p.windowBg)
        activity.findViewById<View>(R.id.root_create_image)?.setBackgroundColor(p.windowBg)
        activity.findViewById<View>(R.id.header_bar)?.setBackgroundColor(p.windowBg)
        activity.findViewById<View>(R.id.bottom_action_card)?.let { card ->
            if (card is MaterialCardView) {
                card.setCardBackgroundColor(p.windowBg)
            }
        }

        val root = activity.findViewById<View>(android.R.id.content)
        if (root is ViewGroup) {
            applyThemeRecursively(root, p)
        }
    }

    fun applyToBottomSheetDialog(
        dialog: BottomSheetDialog,
        contentView: View,
        p: ThemePalette,
        heightRatio: Float? = null
    ) {
        val density = contentView.resources.displayMetrics.density
        val cornerRadius = 24f * density

        fun styleSheetFrame(frame: FrameLayout) {
            frame.backgroundTintList = ColorStateList.valueOf(p.cardBg)
            val shapeDrawable = GradientDrawable().apply {
                cornerRadii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
                setColor(p.cardBg)
            }
            frame.background = shapeDrawable
            frame.clipToOutline = true
            if (heightRatio != null && heightRatio > 0f) {
                val targetHeight = (frame.resources.displayMetrics.heightPixels * heightRatio).toInt()
                frame.layoutParams.height = targetHeight
                try {
                    val behavior = BottomSheetBehavior.from(frame)
                    behavior.maxHeight = targetHeight
                    behavior.peekHeight = targetHeight
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                } catch (_: Exception) {}
            }
        }

        val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (sheet != null) {
            styleSheetFrame(sheet)
            try {
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                if (heightRatio != null && heightRatio > 0f) {
                    val targetHeight = (sheet.resources.displayMetrics.heightPixels * heightRatio).toInt()
                    behavior.maxHeight = targetHeight
                    behavior.peekHeight = targetHeight
                    sheet.layoutParams.height = targetHeight
                }
            } catch (_: Exception) {}
        }

        val contentBg = GradientDrawable().apply {
            cornerRadii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
            setColor(p.cardBg)
        }
        contentView.background = contentBg
        contentView.clipToOutline = true

        if (contentView is ViewGroup) {
            applyThemeRecursively(contentView, p)
        }

        dialog.window?.let { window ->
            window.navigationBarColor = p.cardBg
            val wic = WindowCompat.getInsetsController(window, window.decorView)
            wic.isAppearanceLightNavigationBars = !p.isDark
        }

        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { frame ->
                styleSheetFrame(frame)
                try {
                    val behavior = BottomSheetBehavior.from(frame)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                    if (heightRatio != null && heightRatio > 0f) {
                        val targetHeight = (frame.resources.displayMetrics.heightPixels * heightRatio).toInt()
                        behavior.maxHeight = targetHeight
                        behavior.peekHeight = targetHeight
                        frame.layoutParams.height = targetHeight
                        frame.requestLayout()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun applyToView(view: View, p: ThemePalette) {
        if (view is MaterialCardView) {
            view.setCardBackgroundColor(p.cardBg)
            view.strokeColor = p.cardBorder
        } else if (view is ViewGroup) {
            view.setBackgroundColor(p.cardBg)
        }
        if (view is ViewGroup) {
            applyThemeRecursively(view, p)
        }
    }

    private fun isNestedInCard(view: View, root: View): Boolean {
        var ancestor = view.parent as? View
        while (ancestor != null) {
            if (ancestor is MaterialCardView) return true
            if (ancestor === root) return false
            ancestor = ancestor.parent as? View
        }
        return false
    }

    private fun applyThemeRecursively(viewGroup: ViewGroup, p: ThemePalette) {
        if (viewGroup is LiquidGlassBottomBar) return
        if (viewGroup.id == R.id.card_terminal_log || viewGroup.id == R.id.scroll_log) return

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is LiquidGlassBottomBar) {
                child.applyTheme(p)
                continue
            }

            val childId = child.id
            if (childId == R.id.header_status_pill ||
                childId == R.id.status_badge_text ||
                childId == R.id.status_state ||
                childId == R.id.status_dot ||
                childId == R.id.card_terminal_log ||
                childId == R.id.scroll_log ||
                childId == R.id.txt_log
            ) {
                continue
            }

            when (child) {
                is MaterialCardView -> {
                    if (childId == R.id.card_device_info || isNestedInCard(child, viewGroup)) {
                        child.setCardBackgroundColor(p.cardInner)
                        child.strokeColor = p.cardBorderSubtle
                    } else {
                        child.setCardBackgroundColor(p.cardBg)
                        child.strokeColor = p.cardBorder
                    }
                }
                is MaterialSwitch -> {
                    child.thumbIconDrawable = null
                    val isNord = p.name.equals("nord", ignoreCase = true) || p.name.equals("aurora", ignoreCase = true)
                    val thumbCheckedColor = if (isNord) Color.parseColor("#0A0F1D") else Color.WHITE
                    val thumbUncheckedColor = if (p.isDark) Color.parseColor("#94A3B8") else Color.WHITE

                    val thumbStates = arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    )
                    child.thumbTintList = ColorStateList(thumbStates, intArrayOf(thumbCheckedColor, thumbUncheckedColor))

                    val trackCheckedColor = p.accent
                    val trackUncheckedColor = if (p.isDark) Color.parseColor("#283550") else Color.parseColor("#D1D5DB")
                    child.trackTintList = ColorStateList(thumbStates, intArrayOf(trackCheckedColor, trackUncheckedColor))
                    child.trackDecorationTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
                is MaterialButton -> {
                    if (childId == R.id.btn_mount ||
                        childId == R.id.btn_settings_save ||
                        childId == R.id.btn_settings_backup ||
                        childId == R.id.btn_settings_restore ||
                        childId == R.id.btn_save_custom_term ||
                        childId == R.id.btn_resize_confirm ||
                        childId == R.id.btn_shrink_confirm ||
                        childId == R.id.btn_create_image
                    ) {
                        child.backgroundTintList = ColorStateList.valueOf(p.accent)
                        child.setTextColor(Color.WHITE)
                    } else if (childId == R.id.btn_delete_all_part) {
                        val dangerBg = if (p.isDark) Color.parseColor("#381619") else Color.parseColor("#FFEBE9")
                        val dangerBorder = if (p.isDark) Color.parseColor("#EF4444") else Color.parseColor("#CF222E")
                        val dangerText = if (p.isDark) Color.parseColor("#F87171") else Color.parseColor("#CF222E")
                        child.backgroundTintList = ColorStateList.valueOf(dangerBg)
                        child.strokeColor = ColorStateList.valueOf(dangerBorder)
                        child.setTextColor(dangerText)
                    } else if (childId == R.id.btn_menu_cancel ||
                        childId == R.id.btn_resize_cancel ||
                        childId == R.id.btn_compact_close ||
                        childId == R.id.btn_cancel_custom_term ||
                        childId == R.id.btn_detail_close ||
                        childId == R.id.btn_detail_browse ||
                        childId == R.id.btn_detail_analysis ||
                        childId == R.id.btn_refresh_partitions ||
                        childId == R.id.btn_unmount_all_part ||
                        childId == R.id.btn_reboot ||
                        childId == R.id.btn_refresh_status
                    ) {
                        child.backgroundTintList = ColorStateList.valueOf(p.cardInner)
                        child.strokeColor = ColorStateList.valueOf(p.cardBorder)
                        child.setTextColor(p.textPrimary)
                        child.iconTint = ColorStateList.valueOf(p.textPrimary)
                    } else if (childId == R.id.btn_tune_0 || childId == R.id.btn_tune_1 || childId == R.id.btn_tune_5) {
                        child.backgroundTintList = ColorStateList.valueOf(p.cardInner)
                        child.strokeColor = ColorStateList.valueOf(p.cardBorder)
                        child.setTextColor(p.accent)
                    } else if (childId == R.id.btn_term_custom) {
                        child.setTextColor(p.accent)
                        child.iconTint = ColorStateList.valueOf(p.accent)
                        child.strokeColor = ColorStateList.valueOf(p.cardBorder)
                    } else if (childId == R.id.btn_copy_log || childId == R.id.btn_clear_log) {
                        child.backgroundTintList = ColorStateList.valueOf(p.cardInner)
                        child.strokeColor = ColorStateList.valueOf(p.cardBorder)
                        child.setTextColor(p.textSecondary)
                        child.iconTint = ColorStateList.valueOf(p.textSecondary)
                    } else if (childId == R.id.btn_add_256 || childId == R.id.btn_add_512 ||
                        childId == R.id.btn_add_1024 || childId == R.id.btn_add_2048 ||
                        childId == R.id.btn_size_256 || childId == R.id.btn_size_512 ||
                        childId == R.id.btn_size_1024 || childId == R.id.btn_size_2048
                    ) {
                        child.backgroundTintList = ColorStateList.valueOf(p.cardInner)
                        child.strokeColor = ColorStateList.valueOf(p.cardBorder)
                        child.setTextColor(p.textPrimary)
                    }
                }
                is ImageView -> {
                    if (childId == R.id.menu_item_delete || childId == R.id.menu_icon_delete) {
                        child.setColorFilter(if (p.isDark) Color.parseColor("#F87171") else Color.parseColor("#CF222E"))
                    } else if (childId == R.id.menu_part_dot) {
                    } else if (childId == R.id.menu_mount_icon ||
                        childId == R.id.menu_icon_format ||
                        childId == R.id.menu_icon_resize ||
                        childId == R.id.menu_icon_compact ||
                        childId == R.id.menu_icon_analysis ||
                        childId == R.id.menu_icon_create
                    ) {
                        child.setColorFilter(p.accent)
                    } else if (childId == R.id.btn_back) {
                        child.imageTintList = ColorStateList.valueOf(p.textPrimary)
                    }
                }
                is LinearProgressIndicator -> {
                    child.setIndicatorColor(p.accent)
                    child.trackColor = p.cardBorderSubtle
                }
                is Chip -> {
                    val chipStates = arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    )
                    val chipBgColors = intArrayOf(
                        p.accentBg,
                        p.cardInner
                    )
                    child.chipBackgroundColor = ColorStateList(chipStates, chipBgColors)
                    val strokeColors = intArrayOf(
                        p.accent,
                        p.cardBorder
                    )
                    child.chipStrokeColor = ColorStateList(chipStates, strokeColors)
                    val textColors = intArrayOf(
                        p.accent,
                        p.textPrimary
                    )
                    child.setTextColor(ColorStateList(chipStates, textColors))
                    child.chipIconTint = ColorStateList(chipStates, textColors)
                }
                is RadioButton -> {
                    val rbStates = arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    )
                    val rbColors = intArrayOf(
                        p.accent,
                        if (p.isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
                    )
                    child.buttonTintList = ColorStateList(rbStates, rbColors)
                    child.setTextColor(p.textPrimary)
                }
                is EditText -> {
                    child.setTextColor(p.textPrimary)
                    child.setHintTextColor(p.textMuted)
                }
                is TextView -> {
                    if (child !is Button) {
                        if (childId == R.id.menu_pill_delete) {
                            stylePill(child, PillType.RED, p)
                        } else if (childId == R.id.menu_delete_title) {
                            child.setTextColor(if (p.isDark) Color.parseColor("#F87171") else Color.parseColor("#CF222E"))
                        } else if (childId == R.id.menu_delete_desc) {
                            child.setTextColor(if (p.isDark) Color.parseColor("#FCA5A5") else Color.parseColor("#CF222E"))
                        } else if (childId == R.id.menu_pill_format ||
                            childId == R.id.menu_pill_resize ||
                            childId == R.id.menu_pill_compact ||
                            childId == R.id.menu_pill_analysis ||
                            childId == R.id.menu_pill_create ||
                            childId == R.id.resize_part_tag ||
                            childId == R.id.compact_part_tag ||
                            childId == R.id.custom_term_pill ||
                            childId == R.id.tag_preview_type ||
                            childId == R.id.txt_scan_speed
                        ) {
                            stylePill(child, PillType.ACCENT, p)
                        } else if (childId == R.id.tag_preview_hidden) {
                            stylePill(child, PillType.GRAY, p)
                        } else if (childId == R.id.btn_reset_preset ||
                            childId == R.id.menu_part_stats ||
                            childId == R.id.txt_target_gib_preview ||
                            childId == R.id.txt_dialog_image
                        ) {
                            child.setTextColor(p.accent)
                        } else if (childId == R.id.menu_part_pill || childId == R.id.txt_dialog_status) {
                        } else {
                            val cur = child.currentTextColor
                            val isRed = cur == Color.parseColor("#CF222E") || cur == Color.parseColor("#E53E3E") || cur == Color.parseColor("#F87171")
                            val isGreen = cur == Color.parseColor("#15803D") || cur == Color.parseColor("#1A7F37") || cur == Color.parseColor("#22C55E") || cur == Color.parseColor("#4ADE80")
                            val isYellow = cur == Color.parseColor("#D97706") || cur == Color.parseColor("#9A6700") || cur == Color.parseColor("#EAB308") || cur == Color.parseColor("#FBBF24")

                            when {
                                isRed -> child.setTextColor(if (p.isDark) Color.parseColor("#F87171") else Color.parseColor("#CF222E"))
                                isGreen -> child.setTextColor(if (p.isDark) Color.parseColor("#4ADE80") else Color.parseColor("#1A7F37"))
                                isYellow -> child.setTextColor(if (p.isDark) Color.parseColor("#FBBF24") else Color.parseColor("#9A6700"))
                                cur in allPrimaryTextColors -> child.setTextColor(p.textPrimary)
                                cur in allSecondaryTextColors -> child.setTextColor(p.textSecondary)
                                cur in allMutedTextColors -> child.setTextColor(p.textMuted)
                                else -> {
                                    if (child.textSize >= 13 * child.resources.displayMetrics.scaledDensity) {
                                        child.setTextColor(p.textPrimary)
                                    } else {
                                        child.setTextColor(p.textSecondary)
                                    }
                                }
                            }
                        }
                    } else if (child !is MaterialButton) {
                        if (childId == R.id.btn_detail_browse) {
                            child.backgroundTintList = ColorStateList.valueOf(p.accent)
                            child.setTextColor(Color.WHITE)
                        } else if (childId == R.id.btn_detail_analysis || childId == R.id.btn_detail_close) {
                            child.backgroundTintList = ColorStateList.valueOf(p.cardInner)
                            child.setTextColor(p.textPrimary)
                        }
                    }
                }
            }

            if (child !is ViewGroup && child !is TextView && child !is ImageView && child !is Button) {
                val density = child.resources.displayMetrics.density
                val lp = child.layoutParams
                if (childId == R.id.dialog_drag_handle ||
                    (lp != null && lp.height in 1..(6 * density).toInt() && lp.width in 20..(80 * density).toInt())
                ) {
                    val handleBg = GradientDrawable().apply {
                        cornerRadius = 28f * density
                        setColor(if (p.isDark) Color.argb(90, 255, 255, 255) else Color.argb(60, 0, 0, 0))
                    }
                    child.background = handleBg
                } else if (childId == R.id.dialog_divider_1 || childId == R.id.dialog_divider_2 ||
                    (lp != null && lp.height in 1..(2 * density).toInt())
                ) {
                    child.setBackgroundColor(p.cardBorderSubtle)
                }
            }

            val bg = child.background
            if (bg is GradientDrawable && (child !is TextView || child is EditText)) {
                if (child.tag != "live_cell" &&
                    childId != R.id.header_status_pill &&
                    childId != R.id.status_badge_text
                ) {
                    bg.setColor(p.cardInner)
                    bg.setStroke(1, p.cardBorder)
                }
            }

            if (child is ViewGroup) {
                applyThemeRecursively(child, p)
            }
        }
    }
}
