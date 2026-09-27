package com.orionisli.gh0stra1n

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("gh0stra1n_settings", Context.MODE_PRIVATE)

    var warnThresholdPct: Int
        get() = ManifestStore.getSettingInt(KEY_WARN, sp.getInt(KEY_WARN, 15))
        set(v) {
            sp.edit().putInt(KEY_WARN, v).apply()
            ManifestStore.setSettingInt(KEY_WARN, v)
        }

    var bootAutoMount: Boolean
        get() = ManifestStore.getSettingBool(KEY_AUTO, sp.getBoolean(KEY_AUTO, true))
        set(v) {
            sp.edit().putBoolean(KEY_AUTO, v).apply()
            ManifestStore.setSettingBool(KEY_AUTO, v)
        }

    var noatimeMount: Boolean
        get() = ManifestStore.getSettingBool(KEY_NOATIME, sp.getBoolean(KEY_NOATIME, true))
        set(v) {
            sp.edit().putBoolean(KEY_NOATIME, v).apply()
            ManifestStore.setSettingBool(KEY_NOATIME, v)
        }

    var autoFsck: Boolean
        get() = ManifestStore.getSettingBool(KEY_AUTO_FSCK, sp.getBoolean(KEY_AUTO_FSCK, true))
        set(v) {
            sp.edit().putBoolean(KEY_AUTO_FSCK, v).apply()
            ManifestStore.setSettingBool(KEY_AUTO_FSCK, v)
        }

    var showHiddenFiles: Boolean
        get() = sp.getBoolean(KEY_SHOW_HIDDEN, false)
        set(v) = sp.edit().putBoolean(KEY_SHOW_HIDDEN, v).apply()

    var uiTheme: String
        get() = sp.getString(KEY_UI_THEME, "sakura") ?: "sakura"
        set(v) = sp.edit().putString(KEY_UI_THEME, v).apply()

    var terminalTheme: String
        get() = sp.getString(KEY_TERM_THEME, "parchment") ?: "parchment"
        set(v) = sp.edit().putString(KEY_TERM_THEME, v).apply()

    var terminalBgCustom: String
        get() = sp.getString(KEY_TERM_BG_CUSTOM, "#1E1E2E") ?: "#1E1E2E"
        set(v) = sp.edit().putString(KEY_TERM_BG_CUSTOM, v).apply()

    var terminalFgCustom: String
        get() = sp.getString(KEY_TERM_FG_CUSTOM, "#CDD6F4") ?: "#CDD6F4"
        set(v) = sp.edit().putString(KEY_TERM_FG_CUSTOM, v).apply()

    var customAccent: String
        get() = sp.getString(KEY_CUSTOM_ACCENT, "#693FB4") ?: "#693FB4"
        set(v) = sp.edit().putString(KEY_CUSTOM_ACCENT, v).apply()

    var hapticStrength: Int
        get() = sp.getInt(KEY_HAPTIC, 2)
        set(v) {
            sp.edit().putInt(KEY_HAPTIC, v).apply()
            HapticUtil.strength = v
        }

    var appLanguage: String
        get() = sp.getString(KEY_LANGUAGE, "system") ?: "system"
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    companion object {
        private const val KEY_WARN = "warn_threshold_pct"
        private const val KEY_AUTO = "boot_auto_mount"
        private const val KEY_NOATIME = "noatime_mount"
        private const val KEY_AUTO_FSCK = "auto_fsck"
        private const val KEY_SHOW_HIDDEN = "show_hidden_files"
        private const val KEY_UI_THEME = "ui_theme"
        private const val KEY_TERM_THEME = "terminal_theme"
        private const val KEY_TERM_BG_CUSTOM = "terminal_bg_custom"
        private const val KEY_TERM_FG_CUSTOM = "terminal_fg_custom"
        private const val KEY_CUSTOM_ACCENT = "custom_accent"
        private const val KEY_HAPTIC = "haptic_strength"
        private const val KEY_LANGUAGE = "app_language"
    }
}
