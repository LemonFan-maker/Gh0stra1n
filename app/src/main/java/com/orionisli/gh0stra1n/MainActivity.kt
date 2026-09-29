package com.orionisli.gh0stra1n

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "Gh0stra1n"
    }

    private lateinit var controller: OverlayController
    private lateinit var settings: SettingsStore

    // Header & Status
    private lateinit var headerStatusPill: TextView
    private lateinit var statusDot: ImageView
    private lateinit var txtState: TextView
    private lateinit var statusBadgeText: TextView
    private lateinit var txtDetail: TextView
    private lateinit var txtQuickSummary: TextView

    data class PartitionOverviewHolder(
        val cell: View,
        val dot: ImageView,
        val txtSize: TextView,
        val progress: LinearProgressIndicator,
        val txtPct: TextView
    )
    private lateinit var overviewHolders: Map<String, PartitionOverviewHolder>
    private lateinit var totalOverviewHolder: PartitionOverviewHolder

    // Action buttons
    private lateinit var btnMount: MaterialButton
    private lateinit var btnUnmount: MaterialButton
    private lateinit var btnReboot: MaterialButton
    private lateinit var btnRefreshStatus: MaterialButton
    private lateinit var btnRefreshPartitions: MaterialButton
    private lateinit var btnUnmountAllPart: MaterialButton
    private lateinit var btnDeleteAllPart: MaterialButton

    // Settings
    private lateinit var setWarn: EditText
    private lateinit var setAutoMount: MaterialSwitch
    private lateinit var setNoatime: MaterialSwitch
    private lateinit var setAutoFsck: MaterialSwitch
    private lateinit var setShowHidden: MaterialSwitch
    private lateinit var btnSettingsBackup: MaterialButton
    private lateinit var btnSettingsRestore: MaterialButton
    private lateinit var btnSettingsSave: MaterialButton

    // Lists
    private lateinit var partitionRecyclerView: RecyclerView
    private lateinit var partitionAdapter: PartitionAdapter
    private val latestPartStats = java.util.concurrent.ConcurrentHashMap<String, PartitionImageStat>()
    private val isRefreshingRows = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pendingRefreshRows = java.util.concurrent.atomic.AtomicBoolean(false)

    // Logs
    private lateinit var txtLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var btnCopyLog: MaterialButton
    private lateinit var btnClearLog: MaterialButton

    // Navigation & Tabs
    private lateinit var liquidBottomBar: LiquidGlassBottomBar
    private lateinit var tabControlView: View
    private lateinit var tabPartitionsView: View
    private lateinit var tabLogsView: View
    private lateinit var tabSettingsView: View
    private lateinit var tabAboutView: View

    private enum class Tab { CONTROL, PARTITIONS, LOGS, SETTINGS, ABOUT }
    private var currentTab = Tab.CONTROL

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val currentPalette: ThemePalette
        get() = ThemeManager.getCurrentPalette(this)

    private val colorAccent get() = currentPalette.accent
    private val colorRed by lazy { ContextCompat.getColor(this, R.color.status_red) }
    private val colorDark get() = currentPalette.cardInner
    private val colorBorder get() = currentPalette.cardBorder

    private val colorGreenText: Int
        get() = if (currentPalette.isDark) Color.parseColor("#4ADE80") else Color.rgb(26, 127, 55)
    private val colorRedText: Int
        get() = if (currentPalette.isDark) Color.parseColor("#F87171") else Color.rgb(207, 34, 46)
    private val colorYellowText: Int
        get() = if (currentPalette.isDark) Color.parseColor("#FBBF24") else Color.rgb(154, 103, 0)
    private val colorGrayText: Int
        get() = if (currentPalette.isDark) Color.parseColor("#94A3B8") else Color.rgb(87, 96, 106)

    private fun getStateColor(state: State): Int = when (state) {
        State.BOOT, State.READY, State.DETACH -> colorGrayText
        State.NO_ROOT, State.FAILED -> colorRedText
        State.MOUNTING, State.TEARDOWN -> colorYellowText
        State.LIVE -> colorGreenText
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("active_tab_index", currentTab.ordinal)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val sp = getSharedPreferences("gh0stra1n_settings", Context.MODE_PRIVATE)
        when (sp.getString("ui_theme", "sakura")) {
            "beige" -> setTheme(R.style.Theme_Gh0stra1n_Beige)
            "slate" -> setTheme(R.style.Theme_Gh0stra1n_Slate)
            "cyber" -> setTheme(R.style.Theme_Gh0stra1n_CyberDark)
            "matcha" -> setTheme(R.style.Theme_Gh0stra1n_Matcha)
            "nord", "aurora" -> setTheme(R.style.Theme_Gh0stra1n_Nord)
            "sakura" -> setTheme(R.style.Theme_Gh0stra1n_Sakura)
            else -> setTheme(R.style.Theme_Gh0stra1n_Sakura)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        HapticUtil.init(this)

        initViews()
        initNavigation(savedInstanceState)
        initSettings()
        initLogging()

        applyCurrentTheme()

        controller = OverlayController(
            { state, detail -> runOnUiThread { if (!isFinishing && !isDestroyed) renderState(state, detail) } },
            settings = settings,
        )
        controller.boot()
        startOverlayDaemon()
    }

    private fun startOverlayDaemon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        try {
            val intent = Intent(this, Gh0stOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (e: Throwable) {
            AppLogger.w("MainActivity", "Failed to start Gh0stOverlayService: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRows()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.detach()
        AppLogger.removeListener(logListener)
        super.onDestroy()
    }

    private val logSpannableList = mutableListOf<CharSequence>()

    private fun formatLogEntry(entry: LogEntry): CharSequence {
        val ssb = SpannableStringBuilder()

        val timeStart = ssb.length
        ssb.append("[${entry.time}]")
        ssb.setSpan(
            ForegroundColorSpan(Color.rgb(140, 134, 123)),
            timeStart,
            ssb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val levelName = when (entry.level) {
            LogLevel.ERROR -> "ERROR"
            LogLevel.WARN -> "WARN"
            LogLevel.INFO -> "INFO"
            LogLevel.SU -> "SU"
            LogLevel.DEBUG -> "DEBUG"
        }
        val levelColor = when (entry.level) {
            LogLevel.ERROR -> Color.rgb(220, 38, 38)
            LogLevel.WARN -> Color.rgb(217, 119, 6)
            LogLevel.INFO -> Color.rgb(29, 78, 216)
            LogLevel.SU -> Color.rgb(105, 63, 180)
            LogLevel.DEBUG -> Color.rgb(100, 116, 139)
        }

        val levelStart = ssb.length
        ssb.append("[$levelName]")
        ssb.setSpan(
            ForegroundColorSpan(levelColor),
            levelStart,
            ssb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        ssb.setSpan(
            StyleSpan(Typeface.BOLD),
            levelStart,
            ssb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        if (entry.level != LogLevel.SU && entry.tag.isNotEmpty() && !entry.tag.equals(levelName, ignoreCase = true)) {
            val tagStart = ssb.length
            ssb.append("[${entry.tag}]")
            ssb.setSpan(
                ForegroundColorSpan(Color.rgb(100, 116, 139)),
                tagStart,
                ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val msgStart = ssb.length
        ssb.append(entry.msg)

        when (entry.level) {
            LogLevel.ERROR -> {
                ssb.setSpan(
                    ForegroundColorSpan(Color.rgb(185, 28, 28)),
                    msgStart,
                    ssb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    StyleSpan(Typeface.BOLD),
                    msgStart,
                    ssb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            LogLevel.WARN -> {
                ssb.setSpan(
                    ForegroundColorSpan(Color.rgb(180, 83, 9)),
                    msgStart,
                    ssb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            LogLevel.SU -> {
                val okIdx = entry.msg.indexOf("[OK]")
                if (okIdx != -1) {
                    val fullOkStart = msgStart + okIdx
                    ssb.setSpan(
                        ForegroundColorSpan(Color.rgb(21, 128, 61)),
                        fullOkStart,
                        fullOkStart + 4,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    ssb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        fullOkStart,
                        fullOkStart + 4,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                val rcIdx = entry.msg.indexOf("[RC=")
                if (rcIdx != -1) {
                    val endRc = entry.msg.indexOf("]", rcIdx)
                    if (endRc != -1) {
                        val fullRcStart = msgStart + rcIdx
                        val fullRcEnd = msgStart + endRc + 1
                        ssb.setSpan(
                            ForegroundColorSpan(Color.rgb(220, 38, 38)),
                            fullRcStart,
                            fullRcEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        ssb.setSpan(
                            StyleSpan(Typeface.BOLD),
                            fullRcStart,
                            fullRcEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            LogLevel.INFO -> {
                ssb.setSpan(
                    ForegroundColorSpan(Color.rgb(30, 41, 59)),
                    msgStart,
                    ssb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            LogLevel.DEBUG -> {
                ssb.setSpan(
                    ForegroundColorSpan(Color.rgb(100, 116, 139)),
                    msgStart,
                    ssb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return ssb
    }

    private fun rebuildLogView() {
        val ssb = SpannableStringBuilder()
        for (item in logSpannableList) {
            ssb.append(item).append("\n")
        }
        txtLog.setText(ssb, TextView.BufferType.SPANNABLE)
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private val logRebuildPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val logRebuildRunnable = Runnable {
        logRebuildPending.set(false)
        if (isFinishing || isDestroyed) return@Runnable
        rebuildLogView()
    }

    private fun logSpawnTrim() {
        logSpannableList.removeAt(0)
        if (logRebuildPending.compareAndSet(false, true)) {
            txtLog.post(logRebuildRunnable)
        }
    }

    private val logListener: (LogEntry) -> Unit = { entry ->
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val formatted = formatLogEntry(entry)
            logSpannableList.add(formatted)
            if (logSpannableList.size > 500) {
                logSpawnTrim()
            } else {
                txtLog.append(formatted)
                txtLog.append("\n")
                scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }


    private fun initLogging() {
        val existing = AppLogger.getAllLogs()
        logSpannableList.clear()
        if (existing.isNotEmpty()) {
            for (entry in existing.takeLast(500)) {
                logSpannableList.add(formatLogEntry(entry))
            }
            rebuildLogView()
        } else {
            val initial = SpannableStringBuilder("[Gh0stra1n] ${getString(R.string.status_ready_text)}")
            initial.setSpan(ForegroundColorSpan(Color.rgb(100, 116, 139)), 0, initial.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            logSpannableList.add(initial)
            rebuildLogView()
        }

        AppLogger.removeListener(logListener)
            AppLogger.addListener(logListener)
    }

    private fun initViews() {
        headerStatusPill = findViewById(R.id.header_status_pill)
        statusDot = findViewById(R.id.status_dot)
        txtState = findViewById(R.id.status_state)
        statusBadgeText = findViewById(R.id.status_badge_text)
        txtDetail = findViewById(R.id.status_detail)
        txtQuickSummary = findViewById(R.id.txt_quick_summary)

        val txtDeviceInfo = findViewById<TextView>(R.id.txt_device_info)
        val cardDeviceInfo = findViewById<View>(R.id.card_device_info)
        txtDeviceInfo?.text = DeviceInfoHelper.getDeviceSummary()
        cardDeviceInfo?.let { card ->
            ViewAnimUtil.addPressScaleEffect(card)
            card.setOnClickListener {
                HapticUtil.click(it)
                val info = DeviceInfoHelper.getDetailedInfo()
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Device Info", info))
                Toast.makeText(this, getString(R.string.toast_device_info_copied), Toast.LENGTH_SHORT).show()
            }
        }

        overviewHolders = mapOf(
            "system" to PartitionOverviewHolder(
                findViewById(R.id.cell_overview_system),
                findViewById(R.id.dot_overview_system),
                findViewById(R.id.txt_overview_size_system),
                findViewById(R.id.progress_overview_system),
                findViewById(R.id.txt_overview_pct_system)
            ),
            "vendor" to PartitionOverviewHolder(
                findViewById(R.id.cell_overview_vendor),
                findViewById(R.id.dot_overview_vendor),
                findViewById(R.id.txt_overview_size_vendor),
                findViewById(R.id.progress_overview_vendor),
                findViewById(R.id.txt_overview_pct_vendor)
            ),
            "product" to PartitionOverviewHolder(
                findViewById(R.id.cell_overview_product),
                findViewById(R.id.dot_overview_product),
                findViewById(R.id.txt_overview_size_product),
                findViewById(R.id.progress_overview_product),
                findViewById(R.id.txt_overview_pct_product)
            ),
            "system_ext" to PartitionOverviewHolder(
                findViewById(R.id.cell_overview_system_ext),
                findViewById(R.id.dot_overview_system_ext),
                findViewById(R.id.txt_overview_size_system_ext),
                findViewById(R.id.progress_overview_system_ext),
                findViewById(R.id.txt_overview_pct_system_ext)
            ),
            "odm" to PartitionOverviewHolder(
                findViewById(R.id.cell_overview_odm),
                findViewById(R.id.dot_overview_odm),
                findViewById(R.id.txt_overview_size_odm),
                findViewById(R.id.progress_overview_odm),
                findViewById(R.id.txt_overview_pct_odm)
            )
        )
        totalOverviewHolder = PartitionOverviewHolder(
            findViewById(R.id.cell_overview_total),
            findViewById(R.id.dot_overview_total),
            findViewById(R.id.txt_overview_size_total),
            findViewById(R.id.progress_overview_total),
            findViewById(R.id.txt_overview_pct_total)
        )

        overviewHolders.forEach { (partId, holder) ->
            ViewAnimUtil.addPressScaleEffect(holder.cell)
            holder.cell.setOnClickListener {
                HapticUtil.click(it)
                showPartitionDetailDialog(partId)
            }
        }
        ViewAnimUtil.addPressScaleEffect(totalOverviewHolder.cell)
        totalOverviewHolder.cell.setOnClickListener {
            HapticUtil.click(it)
            liquidBottomBar.selectTab(Tab.PARTITIONS.ordinal, notify = true, animate = true)
        }

        btnMount = findViewById(R.id.btn_mount)
        btnUnmount = findViewById(R.id.btn_unmount)
        btnReboot = findViewById(R.id.btn_reboot)
        btnRefreshStatus = findViewById(R.id.btn_refresh_status)
        btnRefreshPartitions = findViewById(R.id.btn_refresh_partitions)
        btnUnmountAllPart = findViewById(R.id.btn_unmount_all_part)
        btnDeleteAllPart = findViewById(R.id.btn_delete_all_part)

        btnMount.setOnClickListener { onMountClick(it) }
        btnUnmount.setOnClickListener { onUnmountClick(it) }
        btnReboot.setOnClickListener { onRebootClick(it) }
        btnRefreshStatus.setOnClickListener { onRefreshClick(it) }
        btnRefreshPartitions.setOnClickListener { onRefreshClick(it) }
        btnUnmountAllPart.setOnClickListener { onUnmountClick(it) }
        btnDeleteAllPart.setOnClickListener { onDeleteAllPartitionsClick(it) }

        setWarn = findViewById(R.id.set_warn)
        setAutoMount = findViewById(R.id.set_auto_mount)
        setNoatime = findViewById(R.id.set_noatime)
        setAutoFsck = findViewById(R.id.set_auto_fsck)
        setShowHidden = findViewById(R.id.set_show_hidden)
        btnSettingsBackup = findViewById(R.id.btn_settings_backup)
        btnSettingsRestore = findViewById(R.id.btn_settings_restore)
        btnSettingsSave = findViewById(R.id.btn_settings_save)

        btnSettingsBackup.setOnClickListener { onBackupClick(it) }
        btnSettingsRestore.setOnClickListener { onRestoreClick(it) }
        btnSettingsSave.setOnClickListener { onSettingsSaveClick(it) }

        findViewById<View>(R.id.btn_theme_beige)?.setOnClickListener { changeUiTheme("beige") }
        findViewById<View>(R.id.btn_theme_slate)?.setOnClickListener { changeUiTheme("slate") }
        findViewById<View>(R.id.btn_theme_cyber)?.setOnClickListener { changeUiTheme("cyber") }
        findViewById<View>(R.id.btn_theme_matcha)?.setOnClickListener { changeUiTheme("matcha") }
        findViewById<View>(R.id.btn_theme_nord)?.setOnClickListener { changeUiTheme("nord") }
        findViewById<View>(R.id.btn_theme_sakura)?.setOnClickListener { changeUiTheme("sakura") }

        findViewById<View>(R.id.btn_term_parchment)?.setOnClickListener { changeTerminalTheme("parchment") }
        findViewById<View>(R.id.btn_term_matrix)?.setOnClickListener { changeTerminalTheme("matrix") }
        findViewById<View>(R.id.btn_term_solarized)?.setOnClickListener { changeTerminalTheme("solarized") }
        findViewById<View>(R.id.btn_term_monokai)?.setOnClickListener { changeTerminalTheme("monokai") }
        findViewById<View>(R.id.btn_term_custom)?.setOnClickListener { showCustomTerminalDialog() }

        findViewById<View>(R.id.btn_haptic_off)?.setOnClickListener { changeHaptic(0) }
        findViewById<View>(R.id.btn_haptic_weak)?.setOnClickListener { changeHaptic(1) }
        findViewById<View>(R.id.btn_haptic_std)?.setOnClickListener { changeHaptic(2) }
        findViewById<View>(R.id.btn_haptic_strong)?.setOnClickListener { changeHaptic(3) }

        findViewById<View>(R.id.btn_lang_zh_cn)?.setOnClickListener { changeLanguage("zh-CN") }
        findViewById<View>(R.id.btn_lang_en)?.setOnClickListener { changeLanguage("en") }
        findViewById<View>(R.id.btn_lang_zh_tw)?.setOnClickListener { changeLanguage("zh-TW") }

        listOf(
            btnMount, btnUnmount, btnReboot, btnRefreshStatus,
            btnRefreshPartitions, btnUnmountAllPart, btnDeleteAllPart,
            btnSettingsSave
        ).forEach { ViewAnimUtil.addPressScaleEffect(it) }

        partitionAdapter = PartitionAdapter(
            onItemClick = { item ->
                HapticUtil.click()
                val intent = Intent(this, PartitionExplorerActivity::class.java).apply {
                    putExtra(PartitionExplorerActivity.EXTRA_PARTITION_ID, item.def.id)
                }
                startActivity(intent)
            },
            onMenuClick = { item ->
                showPartitionMenu(item)
            }
        )

        partitionRecyclerView = findViewById(R.id.partition_recycler_view)
        partitionRecyclerView.layoutManager = LinearLayoutManager(this)
        partitionRecyclerView.adapter = partitionAdapter

        txtLog = findViewById(R.id.txt_log)
        scrollLog = findViewById(R.id.scroll_log)
        btnCopyLog = findViewById(R.id.btn_copy_log)
        btnClearLog = findViewById(R.id.btn_clear_log)

        ViewAnimUtil.addPressScaleEffect(btnCopyLog)
        ViewAnimUtil.addPressScaleEffect(btnClearLog)

        btnCopyLog.setOnClickListener {
            HapticUtil.click(it)
            copyLogToClipboard()
        }
        btnClearLog.setOnClickListener {
            HapticUtil.click(it)
            clearLogs()
        }

        tabControlView = findViewById(R.id.tab_control)
        tabPartitionsView = findViewById(R.id.tab_partitions)
        tabLogsView = findViewById(R.id.tab_logs)
        tabSettingsView = findViewById(R.id.tab_settings)
        tabAboutView = findViewById(R.id.tab_about)
        liquidBottomBar = findViewById(R.id.liquid_bottom_bar)

        initAboutView()

        val rootLayout = findViewById<View>(R.id.root_layout)
        val headerContainer = findViewById<LinearLayout>(R.id.header_container)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            headerContainer.setPadding(
                dp(20),
                statusBars.top + dp(8),
                dp(20),
                dp(8)
            )

            val lp = liquidBottomBar.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = navBars.bottom + dp(12)
            liquidBottomBar.layoutParams = lp

            insets
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun updateCellTextColors(cell: View, primary: Int, secondary: Int) {
        if (cell !is ViewGroup) return
        for (i in 0 until cell.childCount) {
            val child = cell.getChildAt(i)
            if (child is TextView) {
                if (child.id == R.id.txt_overview_pct_system ||
                    child.id == R.id.txt_overview_pct_vendor ||
                    child.id == R.id.txt_overview_pct_product ||
                    child.id == R.id.txt_overview_pct_system_ext ||
                    child.id == R.id.txt_overview_pct_odm ||
                    child.id == R.id.txt_overview_pct_total ||
                    child.text.toString().startsWith("<")
                ) {
                    child.setTextColor(secondary)
                } else {
                    child.setTextColor(primary)
                }
            } else if (child is ViewGroup) {
                updateCellTextColors(child, primary, secondary)
            }
        }
    }

    private fun initAboutView() {
        val txtAboutVersion = findViewById<TextView>(R.id.txt_about_version)
        val versionName = BuildConfig.VERSION_NAME
        val gitHash = BuildConfig.GIT_COMMIT_HASH
        txtAboutVersion?.text = "v$versionName - $gitHash"

        val btnRepo = findViewById<View>(R.id.btn_about_repo)
        if (btnRepo != null) {
            ViewAnimUtil.addPressScaleEffect(btnRepo)
            btnRepo.setOnClickListener {
                HapticUtil.click(it)
                val repoUrl = getString(R.string.about_repo_url)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    AppLogger.e("UI", "打开开源链接失败：${e.message}")
                }
            }
        }
    }

    private fun initNavigation(savedInstanceState: Bundle? = null) {
        val sp = getSharedPreferences("gh0stra1n_settings", Context.MODE_PRIVATE)
        val defaultIndex = sp.getInt("last_active_tab", Tab.CONTROL.ordinal)
        val savedIndex = savedInstanceState?.getInt("active_tab_index", defaultIndex) ?: defaultIndex
        val initialTab = Tab.values().getOrElse(savedIndex) { Tab.CONTROL }

        liquidBottomBar.selectTab(initialTab.ordinal, notify = false, animate = false)
        switchTab(initialTab, animate = false)

        liquidBottomBar.onTabSelectedListener = { index ->
            val targetTab = Tab.values().getOrElse(index) { Tab.CONTROL }
            switchTab(targetTab)
        }

        liquidBottomBar.onTabDragListener = { currentPos ->
            handleTabDrag(currentPos)
        }

        liquidBottomBar.onTabDragEndListener = { fromPos, finalIndex ->
            handleTabDragEnd(fromPos, finalIndex)
        }
    }

    private var pageDragAnimator: ValueAnimator? = null

    private fun applyTabDrag(currentPos: Float) {
        val tabContainer = findViewById<View>(R.id.tab_container)
        val width = (if (tabContainer != null && tabContainer.width > 0) tabContainer.width else resources.displayMetrics.widthPixels).toFloat()
        val views = listOf(tabControlView, tabPartitionsView, tabLogsView, tabSettingsView, tabAboutView)

        val clampedPos = when {
            currentPos < 0f -> currentPos * 0.35f
            currentPos > (views.size - 1) -> (views.size - 1) + (currentPos - (views.size - 1)) * 0.35f
            else -> currentPos
        }

        views.forEachIndexed { idx, v ->
            if (v.visibility != View.VISIBLE) {
                v.visibility = View.VISIBLE
            }
            v.alpha = 1f
            v.translationY = 0f
            v.translationX = (idx - clampedPos) * width
        }
    }

    private fun handleTabDrag(currentPos: Float) {
        pageDragAnimator?.cancel()
        applyTabDrag(currentPos)
    }

    private fun handleTabDragEnd(fromPos: Float, finalIndex: Int) {
        pageDragAnimator?.cancel()
        val targetPos = finalIndex.toFloat().coerceIn(0f, 4f)

        if (kotlin.math.abs(fromPos - targetPos) < 0.005f) {
            val targetTab = Tab.values().getOrElse(finalIndex) { Tab.CONTROL }
            switchTab(targetTab, animate = false)
            return
        }

        val anim = ValueAnimator.ofFloat(fromPos, targetPos).apply {
            duration = 220
            interpolator = android.view.animation.DecelerateInterpolator(1.4f)
            addUpdateListener { va ->
                val animatedPos = va.animatedValue as Float
                applyTabDrag(animatedPos)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val targetTab = Tab.values().getOrElse(finalIndex) { Tab.CONTROL }
                    switchTab(targetTab, animate = false)
                }
            })
        }
        pageDragAnimator = anim
        anim.start()
    }

    private fun switchTab(tab: Tab, animate: Boolean = true) {
        pageDragAnimator?.cancel()
        val views = listOf(tabControlView, tabPartitionsView, tabLogsView, tabSettingsView, tabAboutView)
        val targetView = when (tab) {
            Tab.CONTROL -> tabControlView
            Tab.PARTITIONS -> tabPartitionsView
            Tab.LOGS -> tabLogsView
            Tab.SETTINGS -> tabSettingsView
            Tab.ABOUT -> tabAboutView
        }

        currentTab = tab
        getSharedPreferences("gh0stra1n_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("last_active_tab", tab.ordinal)
            .apply()

        views.forEach { v ->
            if (v == targetView) {
                if (v.visibility != View.VISIBLE) {
                    v.visibility = View.VISIBLE
                    if (animate) {
                        ViewAnimUtil.animateTabEntrance(v)
                    }
                }
                v.translationX = 0f
                if (!animate) {
                    v.alpha = 1f
                    v.translationY = 0f
                }
            } else {
                v.visibility = View.GONE
                v.translationX = 0f
                v.alpha = 1f
                v.translationY = 0f
            }
        }

        if (tab == Tab.PARTITIONS) {
            refreshRows()
        } else if (tab == Tab.LOGS) {
            scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
        } else if (tab == Tab.ABOUT) {
            (tabAboutView as? ScrollView)?.post { (tabAboutView as? ScrollView)?.scrollTo(0, 0) }
        }
    }

    private fun applyCurrentTheme() {
        val p = currentPalette
        ThemeManager.applyToActivity(this)
        liquidBottomBar.applyTheme(p)
        findViewById<View>(R.id.root_layout)?.setBackgroundColor(p.windowBg)

        btnMount.backgroundTintList = ColorStateList.valueOf(p.accent)
        btnSettingsSave.backgroundTintList = ColorStateList.valueOf(p.accent)
        btnSettingsBackup.backgroundTintList = ColorStateList.valueOf(p.accent)
        btnSettingsRestore.backgroundTintList = ColorStateList.valueOf(p.accent)

        listOf(btnReboot, btnRefreshStatus, btnRefreshPartitions).forEach { btn ->
            btn.backgroundTintList = ColorStateList.valueOf(p.cardInner)
            btn.strokeColor = ColorStateList.valueOf(p.cardBorder)
            btn.setTextColor(p.textPrimary)
            btn.iconTint = ColorStateList.valueOf(p.textPrimary)
        }

        btnUnmountAllPart.backgroundTintList = ColorStateList.valueOf(p.cardInner)
        btnUnmountAllPart.strokeColor = ColorStateList.valueOf(p.cardBorder)
        btnUnmountAllPart.setTextColor(p.textPrimary)
        btnUnmountAllPart.iconTint = ColorStateList.valueOf(p.textPrimary)

        val dangerBg = if (p.isDark) Color.parseColor("#381619") else Color.parseColor("#FFEBE9")
        val dangerBorder = if (p.isDark) Color.parseColor("#EF4444") else Color.parseColor("#CF222E")
        val dangerText = if (p.isDark) Color.parseColor("#F87171") else Color.parseColor("#CF222E")
        btnDeleteAllPart.backgroundTintList = ColorStateList.valueOf(dangerBg)
        btnDeleteAllPart.strokeColor = ColorStateList.valueOf(dangerBorder)
        btnDeleteAllPart.setTextColor(dangerText)
        btnDeleteAllPart.iconTint = ColorStateList.valueOf(dangerText)

        if (::overviewHolders.isInitialized) {
            overviewHolders.values.forEach { h ->
                h.progress.trackColor = p.cardBorderSubtle
            }
        }
        if (::totalOverviewHolder.isInitialized) {
            totalOverviewHolder.progress.trackColor = p.cardBorderSubtle
        }

        listOf(btnCopyLog, btnClearLog).forEach { btn ->
            btn.strokeColor = ColorStateList.valueOf(p.cardBorder)
            btn.setTextColor(p.textSecondary)
            btn.iconTint = ColorStateList.valueOf(p.textSecondary)
        }

        updateThemeButtonsUi()
        updateTerminalThemeButtonsUi()
        updateHapticButtonsUi()
        updateLanguageButtonsUi()

        if (::partitionAdapter.isInitialized) partitionAdapter.notifyDataSetChanged()
        findViewById<ImageView>(R.id.icon_about_github)?.imageTintList = ColorStateList.valueOf(p.textPrimary)
        findViewById<ImageView>(R.id.icon_about_history)?.imageTintList = ColorStateList.valueOf(p.textSecondary)
    }

    private fun showPartitionMenu(item: PartitionItem) {
        HapticUtil.heavyClick()
        val dialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_partition_menu, null)
        dialog.setContentView(sheetView)

        val screenHeight = resources.displayMetrics.heightPixels
        val halfHeight = (screenHeight * 0.52).toInt()

        dialog.behavior.apply {
            maxHeight = halfHeight
            peekHeight = halfHeight
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        ThemeManager.applyToBottomSheetDialog(dialog, sheetView, currentPalette, 0.52f)

        // 头部信息绑定
        val dot = sheetView.findViewById<ImageView>(R.id.menu_part_dot)
        val title = sheetView.findViewById<TextView>(R.id.menu_part_title)
        val mount = sheetView.findViewById<TextView>(R.id.menu_part_mount)
        val pill = sheetView.findViewById<TextView>(R.id.menu_part_pill)
        val stats = sheetView.findViewById<TextView>(R.id.menu_part_stats)

        title.text = item.def.id
        mount.text = item.def.mountPoint

        title.setTextColor(currentPalette.textPrimary)
        mount.setTextColor(currentPalette.textSecondary)

        val hasImage = (latestPartStats[item.def.id]?.imageCount ?: 0) > 0 || item.hasImage

        if (hasImage) {
            stats.text = item.stats.ifBlank { "ext4" }
            stats.setTextColor(currentPalette.accent)
        } else {
            stats.text = getString(R.string.part_no_image)
            stats.setTextColor(currentPalette.textSecondary)
        }

        val isLive = item.def.id in ImageManager.livePartitions()
        if (isLive) {
            dot.setColorFilter(colorGreenText)
            pill.text = "LIVE"
            ThemeManager.stylePill(pill, PillType.GREEN, currentPalette)
        } else {
            dot.setColorFilter(colorGrayText)
            pill.text = "OFFLINE"
            ThemeManager.stylePill(pill, PillType.GRAY, currentPalette)
        }

        val itemFormat = sheetView.findViewById<View>(R.id.menu_item_format)
        val itemMountUnmount = sheetView.findViewById<View>(R.id.menu_item_mount_unmount)
        val itemResize = sheetView.findViewById<View>(R.id.menu_item_resize)
        val itemCompact = sheetView.findViewById<View>(R.id.menu_item_compact)
        val itemFsck = sheetView.findViewById<View>(R.id.menu_item_fsck)
        val itemAnalysis = sheetView.findViewById<View>(R.id.menu_item_analysis)
        val itemDelete = sheetView.findViewById<View>(R.id.menu_item_delete)
        val itemCreate = sheetView.findViewById<View>(R.id.menu_item_create)

        val mountIcon = sheetView.findViewById<ImageView>(R.id.menu_mount_icon)
        val mountTitle = sheetView.findViewById<TextView>(R.id.menu_mount_title)
        val mountDesc = sheetView.findViewById<TextView>(R.id.menu_mount_desc)

        val pillFormat = sheetView.findViewById<TextView>(R.id.menu_pill_format)
        val pillResize = sheetView.findViewById<TextView>(R.id.menu_pill_resize)
        val pillCompact = sheetView.findViewById<TextView>(R.id.menu_pill_compact)
        val pillFsck = sheetView.findViewById<TextView>(R.id.menu_pill_fsck)
        val pillAnalysis = sheetView.findViewById<TextView>(R.id.menu_pill_analysis)
        val pillDelete = sheetView.findViewById<TextView>(R.id.menu_pill_delete)
        val pillCreate = sheetView.findViewById<TextView>(R.id.menu_pill_create)

        val titleDelete = sheetView.findViewById<TextView>(R.id.menu_delete_title)
        val descDelete = sheetView.findViewById<TextView>(R.id.menu_delete_desc)
        val iconDelete = sheetView.findViewById<ImageView>(R.id.menu_icon_delete)

        val titleCreate = sheetView.findViewById<TextView>(R.id.menu_create_title)
        val descCreate = sheetView.findViewById<TextView>(R.id.menu_create_desc)
        val iconCreate = sheetView.findViewById<ImageView>(R.id.menu_icon_create)

        if (isLive) {
            mountIcon.setImageResource(R.drawable.ic_unmount)
            mountIcon.setColorFilter(colorYellowText)
            mountTitle.text = getString(R.string.menu_unmount)
            mountDesc.text = getString(R.string.menu_unmount_desc)
            mountTitle.setTextColor(currentPalette.textPrimary)
            mountDesc.setTextColor(currentPalette.textSecondary)
        } else {
            mountIcon.setImageResource(R.drawable.ic_mount)
            mountIcon.setColorFilter(currentPalette.accent)
            mountTitle.text = getString(R.string.menu_mount)
            mountDesc.text = getString(R.string.menu_mount_desc)
            mountTitle.setTextColor(currentPalette.textPrimary)
            mountDesc.setTextColor(currentPalette.textSecondary)
        }

        if (!hasImage) {
            val noImageHint = getString(R.string.menu_disabled_no_image_hint)
            listOf(itemFormat, itemMountUnmount, itemResize, itemCompact, itemFsck, itemAnalysis, itemDelete).forEach { view ->
                view.alpha = 0.35f
                view.isEnabled = false
                view.setOnClickListener {
                    HapticUtil.tick()
                    Toast.makeText(this, noImageHint, Toast.LENGTH_SHORT).show()
                }
            }
            listOf(pillFormat, pillResize, pillCompact, pillFsck, pillAnalysis, pillDelete).forEach { p ->
                p?.let { ThemeManager.stylePill(it, PillType.GRAY, currentPalette) }
            }
            titleDelete?.setTextColor(currentPalette.textSecondary)
            descDelete?.setTextColor(currentPalette.textSecondary)
            iconDelete?.setColorFilter(currentPalette.textSecondary)

            itemCreate.alpha = 1.0f
            itemCreate.isEnabled = true
            titleCreate?.setTextColor(currentPalette.accent)
            descCreate?.setTextColor(currentPalette.textSecondary)
            descCreate?.text = getString(R.string.menu_create_desc)
            iconCreate?.setColorFilter(currentPalette.accent)
            pillCreate?.text = getString(R.string.tag_template)
            pillCreate?.let { ThemeManager.stylePill(it, PillType.ACCENT, currentPalette) }
            ViewAnimUtil.addPressScaleEffect(itemCreate)
            itemCreate.setOnClickListener {
                dialog.dismiss()
                HapticUtil.click()
                val intent = Intent(this, CreateImageActivity::class.java).apply {
                    putExtra(CreateImageActivity.EXTRA_PARTITION_ID, item.def.id)
                }
                startActivity(intent)
            }
        } else {
            val hasImageHint = getString(R.string.menu_disabled_has_image_hint)
            itemCreate.alpha = 0.35f
            itemCreate.isEnabled = false
            titleCreate?.setTextColor(currentPalette.textSecondary)
            descCreate?.setTextColor(currentPalette.textSecondary)
            descCreate?.text = hasImageHint
            iconCreate?.setColorFilter(currentPalette.textSecondary)
            pillCreate?.text = getString(R.string.menu_pill_exists)
            pillCreate?.let { ThemeManager.stylePill(it, PillType.GRAY, currentPalette) }
            itemCreate.setOnClickListener {
                HapticUtil.tick()
                Toast.makeText(this, hasImageHint, Toast.LENGTH_SHORT).show()
            }

            listOf(itemFormat, itemMountUnmount, itemResize, itemCompact, itemFsck, itemAnalysis, itemDelete).forEach { view ->
                view.alpha = 1.0f
                view.isEnabled = true
                ViewAnimUtil.addPressScaleEffect(view)
            }
            pillFormat?.let { ThemeManager.stylePill(it, PillType.ACCENT, currentPalette) }
            pillResize?.let { ThemeManager.stylePill(it, PillType.ACCENT, currentPalette) }
            pillCompact?.let { ThemeManager.stylePill(it, PillType.ACCENT, currentPalette) }
            pillFsck?.let { ThemeManager.stylePill(it, PillType.ACCENT, currentPalette) }
            pillAnalysis?.let { ThemeManager.stylePill(it, PillType.ACCENT, currentPalette) }
            pillDelete?.let { ThemeManager.stylePill(it, PillType.RED, currentPalette) }

            titleDelete?.setTextColor(if (currentPalette.isDark) Color.parseColor("#F87171") else Color.parseColor("#CF222E"))
            descDelete?.setTextColor(if (currentPalette.isDark) Color.parseColor("#FCA5A5") else Color.parseColor("#CF222E"))
            iconDelete?.setColorFilter(if (currentPalette.isDark) Color.parseColor("#F87171") else Color.parseColor("#CF222E"))

            itemFormat.setOnClickListener {
                dialog.dismiss()
                HapticUtil.warning()
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_format_title)
                    .setMessage(getString(R.string.dialog_format_msg_fmt, item.def.id, item.def.mountPoint))
                    .setPositiveButton(R.string.dialog_format_btn) { _, _ ->
                        HapticUtil.confirm()
                        AppLogger.i("UI", "格式化分区${item.def.id}")
                        controller.formatPartition(item.def)
                    }
                    .setNegativeButton(R.string.btn_cancel) { _, _ ->
                        HapticUtil.click()
                        AppLogger.i("UI", "取消格式化分区${item.def.id}")
                    }
                    .show()
            }

            itemMountUnmount.setOnClickListener {
                dialog.dismiss()
                HapticUtil.click()
                if (isLive) {
                    AppLogger.i("UI", "卸载单个分区${item.def.id}")
                    controller.unmountPartition(item.def)
                } else {
                    AppLogger.i("UI", "挂载单个分区${item.def.id}")
                    controller.mountPartition(item.def)
                }
            }

            itemResize.setOnClickListener {
                dialog.dismiss()
                HapticUtil.click()
                showResizeDialog(item)
            }

            itemCompact.setOnClickListener {
                dialog.dismiss()
                HapticUtil.click()
                showCompactDialog(item)
            }

            itemFsck.setOnClickListener {
                dialog.dismiss()
                HapticUtil.click()
                showFsckDialog(item)
            }

            itemAnalysis.setOnClickListener {
                dialog.dismiss()
                HapticUtil.click()
                val intent = Intent(this, ImageAnalysisActivity::class.java).apply {
                    putExtra(ImageAnalysisActivity.EXTRA_PARTITION_ID, item.def.id)
                }
                startActivity(intent)
            }

            itemDelete.setOnClickListener {
                dialog.dismiss()
                HapticUtil.warning()
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_delete_part_title)
                    .setMessage(getString(R.string.dialog_delete_part_msg_fmt, item.def.id, item.def.mountPoint, item.def.id))
                    .setPositiveButton(R.string.dialog_delete_part_btn) { _, _ ->
                        HapticUtil.confirm()
                        AppLogger.i("UI", "删除分区${item.def.id}")
                        controller.deletePartition(item.def)
                    }
                    .setNegativeButton(R.string.btn_cancel) { _, _ ->
                        HapticUtil.click()
                        AppLogger.i("UI", "取消删除分区${item.def.id}")
                    }
                    .show()
            }
        }

        val btnCancel = sheetView.findViewById<View>(R.id.btn_menu_cancel)
        ViewAnimUtil.addPressScaleEffect(btnCancel)
        btnCancel.setOnClickListener {
            HapticUtil.tick()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showResizeDialog(item: PartitionItem) {
        HapticUtil.click()
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_resize_image, null)
        dialog.setContentView(view)
        ThemeManager.applyToBottomSheetDialog(dialog, view, currentPalette)

        val partTag = view.findViewById<TextView>(R.id.resize_part_tag)
        ThemeManager.stylePill(partTag, PillType.ACCENT, currentPalette)
        val txtCurrent = view.findViewById<TextView>(R.id.txt_resize_current)
        val txtImageName = view.findViewById<TextView>(R.id.txt_resize_image_name)
        val editTarget = view.findViewById<EditText>(R.id.edit_resize_target_mib)
        val txtPreview = view.findViewById<TextView>(R.id.txt_target_gib_preview)
        val txtModeHint = view.findViewById<TextView>(R.id.txt_resize_mode_hint)
        val btnCancel = view.findViewById<View>(R.id.btn_resize_cancel)
        val btnConfirm = view.findViewById<View>(R.id.btn_resize_confirm)

        partTag.text = item.def.id

        val (imgs, _) = ImageManager.listImages(item.def)
        val upper = imgs.firstOrNull()
        if (upper == null) {
            Toast.makeText(this, getString(R.string.toast_no_image), Toast.LENGTH_SHORT).show()
            return
        }

        val (total, free, _) = ImageManager.imageStats(upper)
        val currentMiB = (total / (1024 * 1024)).coerceAtLeast(ManifestStore.MIN_SIZE_MIB)
        val usedMiB = ((total - free) / (1024 * 1024)).coerceAtLeast(0L)

        txtCurrent.text = getString(R.string.dialog_resize_cur_cap_fmt, currentMiB, usedMiB)
        txtImageName.text = upper.substringAfterLast('/')

        var targetMiB = currentMiB + 256L
        editTarget.setText(targetMiB.toString())
        txtPreview.text = "≈ %.2f GiB".format(targetMiB / 1024.0)

        editTarget.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val v = s?.toString()?.toLongOrNull() ?: 0L
                txtPreview.text = "≈ %.2f GiB".format(v / 1024.0)
            }
        })

        fun setDelta(delta: Long) {
            HapticUtil.tick()
            targetMiB = currentMiB + delta
            editTarget.setText(targetMiB.toString())
        }

        view.findViewById<View>(R.id.btn_add_256).setOnClickListener { setDelta(256) }
        view.findViewById<View>(R.id.btn_add_512).setOnClickListener { setDelta(512) }
        view.findViewById<View>(R.id.btn_add_1024).setOnClickListener { setDelta(1024) }
        view.findViewById<View>(R.id.btn_add_2048).setOnClickListener { setDelta(2048) }

        if (item.isLive) {
            txtModeHint.text = getString(R.string.dialog_resize_hint_mounted)
            txtModeHint.setTextColor(colorGreenText)
        } else {
            txtModeHint.text = getString(R.string.dialog_resize_hint_offline)
            txtModeHint.setTextColor(colorYellowText)
        }

        btnCancel.setOnClickListener {
            HapticUtil.tick()
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            HapticUtil.confirm()
            val chosenMiB = editTarget.text.toString().toLongOrNull() ?: targetMiB
            if (chosenMiB <= currentMiB) {
                Toast.makeText(this, getString(R.string.toast_resize_err_smaller, currentMiB), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()

            Thread {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(this, getString(R.string.toast_resize_in_progress, item.def.id, chosenMiB), Toast.LENGTH_SHORT).show()
                }
                val res = if (item.isLive) {
                    ImageManager.growImageOnline(item.def, upper, chosenMiB * 1024 * 1024L)
                } else {
                    ImageManager.growImage(upper, chosenMiB * 1024 * 1024L)
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (res.isSuccess) {
                        HapticUtil.success()
                        Toast.makeText(this, getString(R.string.toast_resize_success, item.def.id, chosenMiB), Toast.LENGTH_LONG).show()
                        refreshRows()
                    } else {
                        HapticUtil.error()
                        Toast.makeText(this, getString(R.string.toast_resize_failed, res.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        dialog.show()
    }

    private fun showCompactDialog(item: PartitionItem) {
        HapticUtil.click()
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_compact_image, null)
        dialog.setContentView(view)
        ThemeManager.applyToBottomSheetDialog(dialog, view, currentPalette)

        val partTag = view.findViewById<TextView>(R.id.compact_part_tag)
        ThemeManager.stylePill(partTag, PillType.ACCENT, currentPalette)
        val txtSizes = view.findViewById<TextView>(R.id.txt_compact_sizes)
        val txtImageName = view.findViewById<TextView>(R.id.txt_compact_image_name)
        val txtMinLimit = view.findViewById<TextView>(R.id.txt_compact_min_limit)
        val editShrink = view.findViewById<EditText>(R.id.edit_shrink_target_mib)
        val btnShrink = view.findViewById<View>(R.id.btn_shrink_confirm)
        val btnClose = view.findViewById<View>(R.id.btn_compact_close)

        val btnTune0 = view.findViewById<MaterialButton>(R.id.btn_tune_0)
        val btnTune1 = view.findViewById<MaterialButton>(R.id.btn_tune_1)
        val btnTune5 = view.findViewById<MaterialButton>(R.id.btn_tune_5)

        partTag.text = item.def.id

        val (imgs, _) = ImageManager.listImages(item.def)
        val upper = imgs.firstOrNull()
        if (upper == null) {
            Toast.makeText(this, getString(R.string.toast_no_image), Toast.LENGTH_SHORT).show()
            return
        }

        val (total, free, disk) = ImageManager.imageStats(upper)
        val totalMiB = total / (1024 * 1024)
        val diskMiB = disk / (1024 * 1024)
        txtSizes.text = getString(R.string.dialog_compact_cap_fmt, totalMiB, diskMiB)
        txtImageName.text = upper.substringAfterLast('/')

        Thread {
            val minBytes = ImageManager.getMinimumFsSize(upper)
            val minMiB = if (minBytes != null) (minBytes / (1024 * 1024)) + 16L else ManifestStore.MIN_SIZE_MIB
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                txtMinLimit.text = getString(R.string.dialog_compact_shrink_limit_fmt, minMiB)
                editShrink.hint = ">= $minMiB MiB"
            }
        }.start()

        fun applyTune(pct: Int) {
            HapticUtil.confirm()
            Thread {
                val r = ImageManager.tuneReservedBlocks(upper, pct)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (r.isSuccess) {
                        HapticUtil.success()
                        Toast.makeText(this, getString(R.string.toast_tune_success_fmt, pct), Toast.LENGTH_SHORT).show()
                        refreshRows()
                    } else {
                        HapticUtil.error()
                        Toast.makeText(this, getString(R.string.toast_tune_failed_fmt, r.exceptionOrNull()?.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        btnTune0.setOnClickListener { applyTune(0) }
        btnTune1.setOnClickListener { applyTune(1) }
        btnTune5.setOnClickListener { applyTune(5) }

        btnShrink.setOnClickListener {
            val targetMiB = editShrink.text.toString().toLongOrNull()
            if (targetMiB == null || targetMiB >= totalMiB) {
                Toast.makeText(this, getString(R.string.toast_shrink_err_larger, totalMiB), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HapticUtil.warning()
            dialog.dismiss()

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_shrink_title)
                .setMessage(getString(R.string.dialog_shrink_msg_fmt, item.def.id, targetMiB))
                .setPositiveButton(R.string.dialog_shrink_btn) { _, _ ->
                    HapticUtil.confirm()
                    Thread {
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            Toast.makeText(this, getString(R.string.toast_shrink_in_progress, item.def.id), Toast.LENGTH_SHORT).show()
                        }
                        val res = ImageManager.shrinkImage(item.def, upper, targetMiB * 1024 * 1024L)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (res.isSuccess) {
                                HapticUtil.success()
                                Toast.makeText(this, getString(R.string.toast_shrink_success_fmt, item.def.id, targetMiB), Toast.LENGTH_LONG).show()
                                refreshRows()
                            } else {
                                HapticUtil.error()
                                Toast.makeText(this, getString(R.string.toast_shrink_failed_fmt, res.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnClose.setOnClickListener {
            HapticUtil.tick()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFsckDialog(item: PartitionItem) {
        val isLive = item.def.id in ImageManager.livePartitions()
        if (isLive) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_fsck)
                .setMessage("当前分区${item.def.id}处于活跃挂载状态。\n\nLinux要求在离线未挂载状态下执行e2fsck检查以避免破坏元数据。\n是否立即安全卸载该分区并执行自检？")
                .setPositiveButton("卸载并自检") { _, _ ->
                    HapticUtil.confirm()
                    Thread {
                        AppLogger.i("UI", "卸载并自检分区${item.def.id}")
                        val uRes = ImageManager.unmountStack(item.def)
                        if (uRes.isFailure) {
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                Toast.makeText(this, getString(R.string.toast_unmount_failed_fmt, uRes.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                            return@Thread
                        }
                        val (ok, report) = ImageManager.runManualFsck(item.def)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            refreshRows()
                            MaterialAlertDialogBuilder(this)
                                .setTitle(if (ok) "自检通过（Clean）" else "自检发现异常")
                                .setMessage(report)
                                .setPositiveButton("重新挂载") { _, _ ->
                                    HapticUtil.confirm()
                                    controller.mountPartition(item.def)
                                }
                                .setNegativeButton("保持卸载", null)
                                .show()
                        }
                    }.start()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        } else {
            Thread {
                AppLogger.i("UI", "手动离线自检分区${item.def.id}")
                val (ok, report) = ImageManager.runManualFsck(item.def)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    MaterialAlertDialogBuilder(this)
                        .setTitle(if (ok) "自检通过（Clean）" else "自检发现异常")
                        .setMessage(report)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }.start()
        }
    }

    private fun initSettings() {
        settings = SettingsStore(this)
        setWarn.setText(settings.warnThresholdPct.toString())
        setAutoMount.isChecked = settings.bootAutoMount
        setNoatime.isChecked = settings.noatimeMount
        setAutoFsck.isChecked = settings.autoFsck
        setShowHidden.isChecked = settings.showHiddenFiles

        updateThemeButtonsUi()
        updateTerminalThemeButtonsUi()
        updateHapticButtonsUi()
        updateLanguageButtonsUi()
        applyTerminalTheme()
    }

    private fun updateThemeButtonsUi() {
        val cur = settings.uiTheme
        val p = currentPalette
        val normalText = p.textSecondary
        val normalBorder = p.cardBorder
        val accentColor = p.accent

        fun styleBtn(btnId: Int, isCurrent: Boolean) {
            val btn = findViewById<MaterialButton>(btnId) ?: return
            if (isCurrent) {
                btn.strokeColor = ColorStateList.valueOf(accentColor)
                btn.strokeWidth = 4
                btn.setTextColor(accentColor)
                btn.backgroundTintList = ColorStateList.valueOf(p.accentBg)
            } else {
                btn.strokeColor = ColorStateList.valueOf(normalBorder)
                btn.strokeWidth = 2
                btn.setTextColor(normalText)
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            }
        }

        styleBtn(R.id.btn_theme_beige, cur == "beige")
        styleBtn(R.id.btn_theme_slate, cur == "slate")
        styleBtn(R.id.btn_theme_cyber, cur == "cyber")
        styleBtn(R.id.btn_theme_matcha, cur == "matcha")
        styleBtn(R.id.btn_theme_nord, cur == "nord")
        styleBtn(R.id.btn_theme_sakura, cur == "sakura")
    }

    private fun changeUiTheme(themeName: String) {
        if (settings.uiTheme == themeName) return
        HapticUtil.confirm()
        settings.uiTheme = themeName
        getSharedPreferences("gh0stra1n_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("ui_theme", themeName)
            .putInt("last_active_tab", currentTab.ordinal)
            .apply()
        updateThemeButtonsUi()
        Toast.makeText(this, getString(R.string.toast_theme_changing), Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun updateTerminalThemeButtonsUi() {
        val cur = settings.terminalTheme
        val p = currentPalette
        val normalText = p.textSecondary
        val normalBorder = p.cardBorder
        val accentColor = p.accent

        fun styleBtn(btnId: Int, isCurrent: Boolean) {
            val btn = findViewById<MaterialButton>(btnId) ?: return
            if (isCurrent) {
                btn.strokeColor = ColorStateList.valueOf(accentColor)
                btn.strokeWidth = 4
                btn.setTextColor(accentColor)
                btn.backgroundTintList = ColorStateList.valueOf(p.accentBg)
            } else {
                btn.strokeColor = ColorStateList.valueOf(normalBorder)
                btn.strokeWidth = 2
                btn.setTextColor(normalText)
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            }
        }

        styleBtn(R.id.btn_term_parchment, cur == "parchment")
        styleBtn(R.id.btn_term_matrix, cur == "matrix")
        styleBtn(R.id.btn_term_solarized, cur == "solarized")
        styleBtn(R.id.btn_term_monokai, cur == "monokai")
        styleBtn(R.id.btn_term_custom, cur == "custom")
    }

    private fun changeTerminalTheme(termTheme: String) {
        HapticUtil.click()
        settings.terminalTheme = termTheme
        updateTerminalThemeButtonsUi()
        applyTerminalTheme()
    }

    private fun applyTerminalTheme() {
        val terminalScroll = findViewById<ScrollView>(R.id.scroll_log) ?: return
        val cardLog = terminalScroll.parent as? MaterialCardView ?: return
        when (settings.terminalTheme) {
            "matrix" -> {
                cardLog.setCardBackgroundColor(Color.parseColor("#0D1117"))
                cardLog.strokeColor = Color.parseColor("#30363D")
                txtLog.setTextColor(Color.parseColor("#38BDF8"))
            }
            "solarized" -> {
                cardLog.setCardBackgroundColor(Color.parseColor("#FDF6E3"))
                cardLog.strokeColor = Color.parseColor("#EEE8D5")
                txtLog.setTextColor(Color.parseColor("#586E75"))
            }
            "monokai" -> {
                cardLog.setCardBackgroundColor(Color.parseColor("#272822"))
                cardLog.strokeColor = Color.parseColor("#49483E")
                txtLog.setTextColor(Color.parseColor("#F8F8F2"))
            }
            "custom" -> {
                try {
                    val bg = Color.parseColor(settings.terminalBgCustom)
                    val fg = Color.parseColor(settings.terminalFgCustom)
                    cardLog.setCardBackgroundColor(bg)
                    val border = Color.argb(90, Color.red(fg), Color.green(fg), Color.blue(fg))
                    cardLog.strokeColor = border
                    txtLog.setTextColor(fg)
                } catch (e: Exception) {
                    cardLog.setCardBackgroundColor(ContextCompat.getColor(this, R.color.terminal_bg))
                    cardLog.strokeColor = ContextCompat.getColor(this, R.color.terminal_border)
                    txtLog.setTextColor(ContextCompat.getColor(this, R.color.term_text_default))
                }
            }
            else -> {
                cardLog.setCardBackgroundColor(ContextCompat.getColor(this, R.color.terminal_bg))
                cardLog.strokeColor = ContextCompat.getColor(this, R.color.terminal_border)
                txtLog.setTextColor(ContextCompat.getColor(this, R.color.term_text_default))
            }
        }
    }

    private fun showCustomTerminalDialog() {
        HapticUtil.click()
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_custom_terminal, null)
        dialog.setContentView(view)
        ThemeManager.applyToBottomSheetDialog(dialog, view, currentPalette)
        view.findViewById<TextView>(R.id.custom_term_pill)?.let { ThemeManager.stylePill(it, PillType.ACCENT, currentPalette) }

        val cardPreview = view.findViewById<MaterialCardView>(R.id.card_term_preview)
        val txtPreview = view.findViewById<TextView>(R.id.txt_term_preview)
        val swatchBg = view.findViewById<View>(R.id.preview_swatch_bg)
        val swatchFg = view.findViewById<View>(R.id.preview_swatch_fg)
        val editBg = view.findViewById<EditText>(R.id.edit_term_bg)
        val editFg = view.findViewById<EditText>(R.id.edit_term_fg)

        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel_custom_term)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_save_custom_term)

        editBg.setText(settings.terminalBgCustom)
        editFg.setText(settings.terminalFgCustom)

        fun updatePreviewColors() {
            try {
                val bg = Color.parseColor(editBg.text.toString().trim())
                cardPreview.setCardBackgroundColor(bg)
                swatchBg.setBackgroundColor(bg)
            } catch (_: Exception) {}

            try {
                val fg = Color.parseColor(editFg.text.toString().trim())
                txtPreview.setTextColor(fg)
                swatchFg.setBackgroundColor(fg)
                val border = Color.argb(90, Color.red(fg), Color.green(fg), Color.blue(fg))
                cardPreview.strokeColor = border
            } catch (_: Exception) {}
        }

        updatePreviewColors()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreviewColors()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        editBg.addTextChangedListener(watcher)
        editFg.addTextChangedListener(watcher)

        fun setPreset(bg: String, fg: String) {
            HapticUtil.tick()
            editBg.setText(bg)
            editFg.setText(fg)
            updatePreviewColors()
        }

        view.findViewById<View>(R.id.chip_preset_catppuccin)?.setOnClickListener {
            setPreset("#24273A", "#CAD3F5")
        }
        view.findViewById<View>(R.id.chip_preset_tokyo)?.setOnClickListener {
            setPreset("#1A1B26", "#C0CAF5")
        }
        view.findViewById<View>(R.id.chip_preset_dracula)?.setOnClickListener {
            setPreset("#282A36", "#F8F8F2")
        }
        view.findViewById<View>(R.id.chip_preset_nord)?.setOnClickListener {
            setPreset("#2E3440", "#ECEFF4")
        }
        view.findViewById<View>(R.id.chip_preset_gruvbox)?.setOnClickListener {
            setPreset("#282828", "#EBDBB2")
        }
        view.findViewById<View>(R.id.chip_preset_cyberpunk)?.setOnClickListener {
            setPreset("#0A0A12", "#00FFCC")
        }

        btnCancel.setOnClickListener {
            HapticUtil.tick()
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val bgStr = editBg.text.toString().trim()
            val fgStr = editFg.text.toString().trim()
            try {
                Color.parseColor(bgStr)
                Color.parseColor(fgStr)
            } catch (e: Exception) {
                HapticUtil.error()
                Toast.makeText(this, getString(R.string.toast_custom_term_invalid), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            HapticUtil.success()
            settings.terminalBgCustom = bgStr
            settings.terminalFgCustom = fgStr
            settings.terminalTheme = "custom"
            updateTerminalThemeButtonsUi()
            applyTerminalTheme()
            Toast.makeText(this, getString(R.string.toast_custom_term_applied), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateHapticButtonsUi() {
        val cur = settings.hapticStrength
        val p = currentPalette
        val normalText = p.textSecondary
        val normalBorder = p.cardBorder
        val accentColor = p.accent

        fun styleBtn(btnId: Int, isCurrent: Boolean) {
            val btn = findViewById<MaterialButton>(btnId) ?: return
            if (isCurrent) {
                btn.strokeColor = ColorStateList.valueOf(accentColor)
                btn.strokeWidth = 4
                btn.setTextColor(accentColor)
                btn.backgroundTintList = ColorStateList.valueOf(p.accentBg)
            } else {
                btn.strokeColor = ColorStateList.valueOf(normalBorder)
                btn.strokeWidth = 2
                btn.setTextColor(normalText)
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            }
        }

        styleBtn(R.id.btn_haptic_off, cur == 0)
        styleBtn(R.id.btn_haptic_weak, cur == 1)
        styleBtn(R.id.btn_haptic_std, cur == 2)
        styleBtn(R.id.btn_haptic_strong, cur == 3)
    }

    private fun changeHaptic(strength: Int) {
        settings.hapticStrength = strength
        HapticUtil.strength = strength
        HapticUtil.preview(strength)
        updateHapticButtonsUi()
    }

    private fun updateLanguageButtonsUi() {
        val cur = settings.appLanguage
        val p = currentPalette
        val normalText = p.textSecondary
        val normalBorder = p.cardBorder
        val accentColor = p.accent

        fun styleBtn(btnId: Int, isCurrent: Boolean) {
            val btn = findViewById<MaterialButton>(btnId) ?: return
            if (isCurrent) {
                btn.strokeColor = ColorStateList.valueOf(accentColor)
                btn.strokeWidth = 4
                btn.setTextColor(accentColor)
                btn.backgroundTintList = ColorStateList.valueOf(p.accentBg)
            } else {
                btn.strokeColor = ColorStateList.valueOf(normalBorder)
                btn.strokeWidth = 2
                btn.setTextColor(normalText)
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            }
        }

        styleBtn(R.id.btn_lang_zh_cn, cur == "zh-CN" || cur == "system")
        styleBtn(R.id.btn_lang_en, cur == "en")
        styleBtn(R.id.btn_lang_zh_tw, cur == "zh-TW")
    }

    private fun changeLanguage(langTag: String) {
        if (settings.appLanguage == langTag) return
        HapticUtil.confirm()
        settings.appLanguage = langTag
        val locales = if (langTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        Toast.makeText(this, getString(R.string.toast_lang_changed), Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun onBackupClick(v: View) {
        HapticUtil.click(v)
        Toast.makeText(this, getString(R.string.toast_backup_in_progress), Toast.LENGTH_SHORT).show()
        Thread {
            val res = ImageManager.backupUpperData()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (res.isSuccess) {
                    HapticUtil.success()
                    Toast.makeText(this, getString(R.string.toast_backup_success_fmt, res.getOrThrow()), Toast.LENGTH_LONG).show()
                } else {
                    HapticUtil.error()
                    Toast.makeText(this, getString(R.string.toast_backup_failed_fmt, res.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun onRestoreClick(v: View) {
        HapticUtil.click(v)
        val backups = ImageManager.listBackups()
        if (backups.isEmpty()) {
            HapticUtil.warning()
            Toast.makeText(this, getString(R.string.restore_no_backups), Toast.LENGTH_LONG).show()
            return
        }

        val itemLabels = backups.map { b ->
            "${b.filename}\n${b.sizeDisplay} ${b.dateDisplay}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_dialog_title)
            .setItems(itemLabels) { _, which ->
                val selectedBackup = backups[which]
                showRestoreConfirmDialog(selectedBackup)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showRestoreConfirmDialog(backup: ImageManager.BackupInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_confirm_title)
            .setMessage(getString(R.string.restore_confirm_msg, backup.filename, backup.sizeDisplay))
            .setPositiveButton(R.string.restore_btn_confirm) { _, _ ->
                HapticUtil.confirm()
                executeRestore(backup)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun executeRestore(backup: ImageManager.BackupInfo) {
        Toast.makeText(this, getString(R.string.restore_in_progress), Toast.LENGTH_SHORT).show()
        Thread {
            val res = ImageManager.restoreUpperData(backup.fullPath)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (res.isSuccess) {
                    HapticUtil.success()
                    refreshRows()
                    Toast.makeText(this, getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                } else {
                    HapticUtil.error()
                    val ex = res.exceptionOrNull()
                    if (ex is SecurityException) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.restore_tamper_title)
                            .setMessage(getString(R.string.restore_tamper_msg, ex.message))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } else {
                        Toast.makeText(this, getString(R.string.restore_failed_fmt, ex?.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun renderState(state: State, detail: String) {
        val color = getStateColor(state)
        txtState.text = state.name
        txtState.setTextColor(color)
        statusDot.setColorFilter(color)

        txtDetail.text = when (state) {
            State.READY -> getString(R.string.status_ready_desc)
            State.LIVE -> getString(R.string.status_live_desc)
            State.NO_ROOT -> getString(R.string.status_noroot_desc)
            State.MOUNTING -> getString(R.string.status_mounting_desc)
            State.TEARDOWN -> getString(R.string.status_teardown_desc)
            State.DETACH -> getString(R.string.status_unmounted_desc)
            State.FAILED -> if (detail.isBlank()) getString(R.string.status_failed_desc)
                            else getString(R.string.status_failed_fmt, detail)
            State.BOOT -> getString(R.string.status_init_desc)
        }

        headerStatusPill.text = state.name
        when (state) {
            State.LIVE -> {
                ThemeManager.stylePill(headerStatusPill, PillType.GREEN, currentPalette)
                statusBadgeText.text = getString(R.string.state_live_title)
                ThemeManager.stylePill(statusBadgeText, PillType.GREEN, currentPalette)
                HapticUtil.success()
            }
            State.MOUNTING, State.TEARDOWN -> {
                ThemeManager.stylePill(headerStatusPill, PillType.YELLOW, currentPalette)
                statusBadgeText.text = if (state == State.MOUNTING) getString(R.string.state_mounting_title) else getString(R.string.state_teardown_title)
                ThemeManager.stylePill(statusBadgeText, PillType.YELLOW, currentPalette)
            }
            State.NO_ROOT, State.FAILED -> {
                ThemeManager.stylePill(headerStatusPill, PillType.RED, currentPalette)
                statusBadgeText.text = if (state == State.NO_ROOT) getString(R.string.state_noroot_title) else getString(R.string.state_failed_title)
                ThemeManager.stylePill(statusBadgeText, PillType.RED, currentPalette)
                HapticUtil.error()
            }
            State.READY -> {
                ThemeManager.stylePill(headerStatusPill, PillType.GRAY, currentPalette)
                statusBadgeText.text = getString(R.string.state_ready_title)
                ThemeManager.stylePill(statusBadgeText, PillType.GRAY, currentPalette)
            }
            State.DETACH -> {
                ThemeManager.stylePill(headerStatusPill, PillType.GRAY, currentPalette)
                statusBadgeText.text = getString(R.string.state_unmounted_title)
                ThemeManager.stylePill(statusBadgeText, PillType.GRAY, currentPalette)
                HapticUtil.success()
            }
            else -> {
                ThemeManager.stylePill(headerStatusPill, PillType.GRAY, currentPalette)
                statusBadgeText.text = state.name
                ThemeManager.stylePill(statusBadgeText, PillType.GRAY, currentPalette)
            }
        }

        ViewAnimUtil.pulse(statusDot)
        ViewAnimUtil.pulse(headerStatusPill)

        val busy = state == State.MOUNTING || state == State.TEARDOWN
        if (state == State.LIVE) {
            btnMount.visibility = View.GONE
            btnUnmount.visibility = View.VISIBLE
            btnUnmount.isEnabled = !busy
            btnUnmount.backgroundTintList = ColorStateList.valueOf(colorRed)
            btnUnmount.setTextColor(Color.WHITE)
        } else {
            btnMount.visibility = View.VISIBLE
            btnUnmount.visibility = View.GONE
            btnMount.isEnabled = !busy && (state == State.NO_ROOT || state == State.READY || state == State.DETACH || state == State.FAILED || state == State.BOOT)
            btnMount.backgroundTintList = ColorStateList.valueOf(if (btnMount.isEnabled) currentPalette.accent else colorDark)
            btnMount.setTextColor(if (btnMount.isEnabled) Color.WHITE else colorGrayText)
        }
        btnReboot.isEnabled = !busy

        if (!busy) {
            refreshRows()
        }
    }

    data class PartitionImageStat(
        val partId: String,
        val imageCount: Int,
        val usedBytes: Long,
        val totalBytes: Long,
        val pct: Double
    )

    private fun refreshRows() {
        if (isRefreshingRows.get()) {
            pendingRefreshRows.set(true)
            return
        }
        isRefreshingRows.set(true)
        Thread {
            try {
                do {
                    pendingRefreshRows.set(false)
                    val live = ImageManager.livePartitions().toSet()
                    val stats = mutableMapOf<String, String>()
                    val partStats = mutableMapOf<String, PartitionImageStat>()

                    val batchCmd = buildString {
                        append("for p in system vendor product system_ext odm; do ")
                        append("n=\$(ls /data/local/gh0stra1n-overlayfs/\${p}-*.img 2>/dev/null | wc -l); ")
                        append("img=\$(ls -t /data/local/gh0stra1n-overlayfs/\${p}-*.img 2>/dev/null | head -1); ")
                        append("if [ -n \"\$img\" ]; then ")
                        append("/system/bin/tune2fs -l \"\$img\" 2>/dev/null | awk -v p=\"\$p\" -v n=\"\$n\" '")
                        append("/Block size:/ {bs=\$3} /Block count:/ {bc=\$3} /Free blocks:/ {fb=\$3} ")
                        append("END {printf \"%s:%s:%s:%s:%s\\n\", p, n, bs, bc, fb}'; ")
                        append("else echo \"\$p:0:0:0:0\"; fi; done")
                    }

                    val r = SuChannel.run(batchCmd, 15, logCmd = false)
                    for (line in r.out.lines()) {
                        val parts = line.trim().split(":")
                        if (parts.size >= 5) {
                            val p = parts[0]
                            val n = parts[1].toIntOrNull() ?: 0
                            val bs = parts[2].toLongOrNull() ?: 0L
                            val bc = parts[3].toLongOrNull() ?: 0L
                            val fb = parts[4].toLongOrNull() ?: 0L
                            val total = bc * bs
                            val free = fb * bs
                            val used = (total - free).coerceAtLeast(0L)
                            val pct = if (total > 0L) (used.toDouble() / total * 100.0) else 0.0

                            partStats[p] = PartitionImageStat(p, n, used, total, pct)
                            stats[p] = if (n > 0) fmtMiB(total) else getString(R.string.part_no_image)
                        }
                    }

                    val partitionItems = PartitionTable.ALL.map { p ->
                        val stat = partStats[p.id]
                        val hasImg = (stat?.imageCount ?: 0) > 0
                        val totalBytes = stat?.totalBytes ?: 0L
                        val usedBytes = stat?.usedBytes ?: 0L
                        PartitionItem(
                            def = p,
                            isLive = p.id in live,
                            stats = stats[p.id] ?: getString(R.string.part_no_image),
                            hasImage = hasImg,
                            totalBytes = totalBytes,
                            usedBytes = usedBytes
                        )
                    }

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        partitionAdapter.submitList(partitionItems)
                        val liveCount = PartitionTable.ALL.count { it.id in live }
                        txtQuickSummary.text = if (liveCount > 0) getString(R.string.overview_mount_count_fmt, liveCount) else getString(R.string.overview_mount_count_none)

                        latestPartStats.clear()
                        latestPartStats.putAll(partStats)

                        for ((partId, holder) in overviewHolders) {
                            val stat = partStats[partId]
                            val isLive = partId in live
                            if (isLive) {
                                holder.cell.tag = "live_cell"
                                if (currentPalette.isDark) {
                                    val liveBg = GradientDrawable().apply {
                                        cornerRadius = dp(12).toFloat()
                                        setColor(Color.parseColor("#143823"))
                                        setStroke(dp(1), Color.parseColor("#2EA043"))
                                    }
                                    holder.cell.background = liveBg
                                    updateCellTextColors(holder.cell, Color.parseColor("#7EE787"), Color.parseColor("#A3E635"))
                                } else {
                                    holder.cell.setBackgroundResource(R.drawable.bg_overview_cell_live)
                                    updateCellTextColors(holder.cell, Color.parseColor("#14532D"), Color.parseColor("#166534"))
                                }
                            } else {
                                holder.cell.tag = null
                                val normalBg = GradientDrawable().apply {
                                    cornerRadius = dp(12).toFloat()
                                    setColor(currentPalette.cardInner)
                                    setStroke(dp(1), currentPalette.cardBorder)
                                }
                                holder.cell.background = normalBg
                                updateCellTextColors(holder.cell, currentPalette.textPrimary, currentPalette.textSecondary)
                            }

                            if (stat != null && stat.totalBytes > 0L) {
                                holder.txtSize.text = "<${fmtCompact(stat.usedBytes)}/${fmtCompact(stat.totalBytes)}>"
                                val progressVal = (stat.pct * 10).toInt().coerceIn(0, 1000)
                                holder.progress.setProgressCompat(progressVal, true)
                                holder.txtPct.text = "%.1f%%".format(stat.pct)
                                if (isLive) {
                                    holder.dot.setColorFilter(colorGreenText)
                                    holder.progress.setIndicatorColor(colorGreenText)
                                } else {
                                    holder.dot.setColorFilter(colorGrayText)
                                    holder.progress.setIndicatorColor(colorAccent)
                                }
                            } else {
                                holder.txtSize.text = getString(R.string.part_no_image)
                                holder.progress.setProgressCompat(0, false)
                                holder.txtPct.text = "--"
                                holder.dot.setColorFilter(colorGrayText)
                                holder.progress.setIndicatorColor(colorGrayText)
                            }
                        }

                        val sumUsed = partStats.values.sumOf { it.usedBytes }
                        val sumTotal = partStats.values.sumOf { it.totalBytes }
                        val totalPct = if (sumTotal > 0L) (sumUsed.toDouble() / sumTotal * 100.0) else 0.0
                        if (sumTotal > 0L) {
                            totalOverviewHolder.txtSize.text = "<${fmtCompact(sumUsed)}/${fmtCompact(sumTotal)}>"
                            totalOverviewHolder.progress.setProgressCompat((totalPct * 10).toInt().coerceIn(0, 1000), true)
                            totalOverviewHolder.txtPct.text = "%.1f%%".format(totalPct)
                        } else {
                            totalOverviewHolder.txtSize.text = getString(R.string.part_no_image)
                            totalOverviewHolder.progress.setProgressCompat(0, false)
                            totalOverviewHolder.txtPct.text = "--"
                        }
                        if (liveCount == 5) {
                            totalOverviewHolder.cell.tag = "live_cell"
                            if (currentPalette.isDark) {
                                val liveBg = GradientDrawable().apply {
                                    cornerRadius = dp(12).toFloat()
                                    setColor(Color.parseColor("#143823"))
                                    setStroke(dp(1), Color.parseColor("#2EA043"))
                                }
                                totalOverviewHolder.cell.background = liveBg
                                updateCellTextColors(totalOverviewHolder.cell, Color.parseColor("#7EE787"), Color.parseColor("#A3E635"))
                            } else {
                                totalOverviewHolder.cell.setBackgroundResource(R.drawable.bg_overview_cell_live)
                                updateCellTextColors(totalOverviewHolder.cell, Color.parseColor("#14532D"), Color.parseColor("#166534"))
                            }
                            totalOverviewHolder.dot.setColorFilter(colorGreenText)
                            totalOverviewHolder.progress.setIndicatorColor(colorGreenText)
                        } else {
                            totalOverviewHolder.cell.tag = null
                            val normalBg = GradientDrawable().apply {
                                cornerRadius = dp(12).toFloat()
                                setColor(currentPalette.cardInner)
                                setStroke(dp(1), currentPalette.cardBorder)
                            }
                            totalOverviewHolder.cell.background = normalBg
                            updateCellTextColors(totalOverviewHolder.cell, currentPalette.textPrimary, currentPalette.textSecondary)
                            if (liveCount > 0) {
                                totalOverviewHolder.dot.setColorFilter(colorYellowText)
                                totalOverviewHolder.progress.setIndicatorColor(colorAccent)
                            } else {
                                totalOverviewHolder.dot.setColorFilter(colorAccent)
                                totalOverviewHolder.progress.setIndicatorColor(colorAccent)
                            }
                        }
                    }
                } while (pendingRefreshRows.get())
            } finally {
                isRefreshingRows.set(false)
            }
        }.start()
    }

    private fun showPartitionDetailDialog(partId: String) {
        val part = PartitionTable.byId[partId] ?: return
        val isLive = part.id in ImageManager.livePartitions()
        val stat = latestPartStats[part.id]
        val (imgs, _) = ImageManager.listImages(part)
        val imgName = if (imgs.isNotEmpty()) imgs.first().substringAfterLast('/') else getString(R.string.detail_no_img)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_partition_detail, null)
        ThemeManager.applyToView(dialogView, currentPalette)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtTitle = dialogView.findViewById<TextView>(R.id.txt_dialog_title)
        val txtStatus = dialogView.findViewById<TextView>(R.id.txt_dialog_status)
        val txtMount = dialogView.findViewById<TextView>(R.id.txt_dialog_mount)
        val txtQuota = dialogView.findViewById<TextView>(R.id.txt_dialog_quota)
        val txtSpace = dialogView.findViewById<TextView>(R.id.txt_dialog_space)
        val txtImage = dialogView.findViewById<TextView>(R.id.txt_dialog_image)
        val txtFs = dialogView.findViewById<TextView>(R.id.txt_dialog_fs)
        val txtUpper = dialogView.findViewById<TextView>(R.id.txt_dialog_upper)
        val txtWork = dialogView.findViewById<TextView>(R.id.txt_dialog_work)
        val txtLower = dialogView.findViewById<TextView>(R.id.txt_dialog_lower)
        val btnBrowse = dialogView.findViewById<Button>(R.id.btn_detail_browse)
        val btnAnalysis = dialogView.findViewById<Button>(R.id.btn_detail_analysis)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_detail_close)

        txtTitle.text = getString(R.string.detail_title_fmt, part.id, part.mountPoint)
        if (isLive) {
            txtStatus.text = getString(R.string.state_live_active)
            ThemeManager.stylePill(txtStatus, PillType.GREEN, currentPalette)
        } else {
            txtStatus.text = getString(R.string.state_offline_unmounted)
            ThemeManager.stylePill(txtStatus, PillType.GRAY, currentPalette)
        }

        txtMount.text = part.mountPoint

        if (stat != null && stat.totalBytes > 0L) {
            val totalMiB = (stat.totalBytes / (1024 * 1024)).coerceAtLeast(1L)
            val usedMb = "%.1f MiB".format(stat.usedBytes / (1024.0 * 1024.0))
            val totalMb = "%.1f MiB".format(stat.totalBytes / (1024.0 * 1024.0))
            txtQuota.text = "$totalMiB MiB"
            txtSpace.text = "$usedMb / $totalMb (%.1f%%)".format(stat.pct)
        } else {
            txtQuota.text = getString(R.string.part_no_image)
            txtSpace.text = getString(R.string.part_no_image)
        }

        txtImage.text = imgName
        txtFs.text = getString(R.string.detail_fs_desc)

        val baseDir = PartitionTable.BASE_DIR
        txtUpper.text = "Upper：$baseDir/mnt_${part.id}/u"
        txtWork.text = "Work：$baseDir/mnt_${part.id}/w"
        txtLower.text = getString(R.string.detail_lower_desc_fmt, part.mountPoint)

        btnBrowse.backgroundTintList = ColorStateList.valueOf(currentPalette.accent)
        btnBrowse.setTextColor(Color.WHITE)
        btnAnalysis.backgroundTintList = ColorStateList.valueOf(currentPalette.cardInner)
        btnAnalysis.setTextColor(currentPalette.textPrimary)
        btnClose.backgroundTintList = ColorStateList.valueOf(currentPalette.cardInner)
        btnClose.setTextColor(currentPalette.textPrimary)

        ViewAnimUtil.addPressScaleEffect(btnBrowse)
        ViewAnimUtil.addPressScaleEffect(btnAnalysis)
        ViewAnimUtil.addPressScaleEffect(btnClose)

        btnBrowse.setOnClickListener {
            HapticUtil.click(it)
            dialog.dismiss()
            val intent = Intent(this, PartitionExplorerActivity::class.java).apply {
                putExtra(PartitionExplorerActivity.EXTRA_PARTITION_ID, part.id)
            }
            startActivity(intent)
        }

        btnAnalysis.setOnClickListener {
            HapticUtil.click(it)
            dialog.dismiss()
            val intent = Intent(this, ImageAnalysisActivity::class.java).apply {
                putExtra(ImageAnalysisActivity.EXTRA_PARTITION_ID, part.id)
            }
            startActivity(intent)
        }

        btnClose.setOnClickListener {
            HapticUtil.click(it)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun fmtCompact(bytes: Long): String {
        if (bytes <= 0L) return "0M"
        val mib = bytes / (1024.0 * 1024.0)
        return when {
            mib >= 1024.0 -> {
                val gib = mib / 1024.0
                if ((gib * 10).toLong() % 10 == 0L) "%.0fG".format(gib) else "%.1fG".format(gib)
            }
            mib >= 10.0 -> "%.0fM".format(mib)
            mib >= 0.1 -> "%.1fM".format(mib)
            else -> "${(bytes / 1024.0).toInt()}K"
        }
    }

    private fun fmtMiB(b: Long): String = when {
        b >= 1024L * 1024 * 1024 -> "%.1fGiB".format(b / (1024.0 * 1024 * 1024))
        b >= 1024L * 1024 -> "${b / (1024 * 1024)}MiB"
        else -> "${b / 1024}KiB"
    }

    fun onMountClick(v: View) {
        HapticUtil.click(v)
        AppLogger.i("UI", "挂载OverlayFS")

        val hasAny = PartitionTable.ALL.any { (latestPartStats[it.id]?.imageCount ?: 0) > 0 }
            || ImageManager.hasAnyImage()
        if (!hasAny) {
            HapticUtil.warning()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_no_image_title)
                .setMessage(R.string.dialog_no_image_msg)
                .setPositiveButton(R.string.dialog_no_image_btn_create) { _, _ ->
                    HapticUtil.click()
                    liquidBottomBar.selectTab(Tab.PARTITIONS.ordinal, notify = true, animate = true)
                    Toast.makeText(this, R.string.toast_select_partition_to_create, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.btn_cancel) { _, _ ->
                    HapticUtil.click()
                }
                .show()
            return
        }

        controller.mountAll()
    }

    fun onUnmountClick(v: View) {
        HapticUtil.click(v)
        AppLogger.i("UI", "卸载OverlayFS")
        controller.unmountAll()
    }

    fun onRebootClick(v: View) {
        HapticUtil.warning()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_reboot_title)
            .setMessage(R.string.dialog_reboot_msg)
            .setPositiveButton(R.string.dialog_reboot_btn) { _, _ ->
                HapticUtil.confirm()
                AppLogger.i("UI", "安全重启")
                controller.rebootWithTeardown()
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                HapticUtil.click()
                AppLogger.i("UI", "取消安全重启")
            }
            .show()
    }

    fun onDeleteAllPartitionsClick(v: View) {
        HapticUtil.warning()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_all_title)
            .setMessage(R.string.dialog_delete_all_msg)
            .setPositiveButton(R.string.dialog_delete_all_btn) { _, _ ->
                HapticUtil.confirm()
                AppLogger.i("UI", "删除所有分区镜像数据")
                controller.deleteAllPartitions()
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                HapticUtil.click()
                AppLogger.i("UI", "取消删除所有分区镜像")
            }
            .show()
    }

    fun onRefreshClick(v: View) {
        HapticUtil.click(v)
        AppLogger.i("UI", "刷新状态")
        if (controller.state == State.NO_ROOT) {
            controller.boot()
        }
        refreshRows()
        Toast.makeText(this, getString(R.string.toast_status_refreshed), Toast.LENGTH_SHORT).show()
    }

    fun onSettingsSaveClick(v: View) {
        HapticUtil.click(v)
        val w = setWarn.text.toString().toIntOrNull()?.coerceIn(5, 90) ?: 15
        val autoMountVal = setAutoMount.isChecked
        val noatimeVal = setNoatime.isChecked
        val autoFsckVal = setAutoFsck.isChecked
        val showHiddenVal = setShowHidden.isChecked

        setWarn.setText(w.toString())

        Thread {
        settings.warnThresholdPct = w
        settings.bootAutoMount = autoMountVal
        settings.noatimeMount = noatimeVal
        settings.autoFsck = autoFsckVal
        settings.showHiddenFiles = showHiddenVal
            val remountCount = ImageManager.remountLivePartitions(noatimeVal)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val remountMsg = if (remountCount > 0) "(已即时同步${remountCount}个运行中挂载点)" else ""
                AppLogger.i("SETTINGS", "设置已保存$remountMsg：空间告警=$w%，启动恢复=$autoMountVal，noatime=$noatimeVal，自动fsck=$autoFsckVal，隐藏文件=$showHiddenVal")
                Toast.makeText(this, getString(R.string.toast_settings_saved) + remountMsg, Toast.LENGTH_SHORT).show()
                refreshRows()
            }
        }.start()
    }

    private fun copyLogToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Gh0stra1n Logs", txtLog.text.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.toast_log_copied), Toast.LENGTH_SHORT).show()
    }

    private fun clearLogs() {
        AppLogger.clear()
        logSpannableList.clear()
        val ssb = SpannableStringBuilder("[Gh0stra1n] ${getString(R.string.toast_log_cleared)}\n")
        ssb.setSpan(ForegroundColorSpan(Color.rgb(100, 116, 139)), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        txtLog.setText(ssb, TextView.BufferType.SPANNABLE)
        Toast.makeText(this, getString(R.string.toast_log_cleared), Toast.LENGTH_SHORT).show()
    }

    data class PartitionItem(
        val def: PartitionDef,
        val isLive: Boolean,
        val stats: String,
        val hasImage: Boolean = false,
        val totalBytes: Long = 0L,
        val usedBytes: Long = 0L
    )

    class PartitionAdapter(
        private val onItemClick: (PartitionItem) -> Unit,
        private val onMenuClick: (PartitionItem) -> Unit
    ) : RecyclerView.Adapter<PartitionAdapter.ViewHolder>() {
        private var items: List<PartitionItem> = emptyList()

        fun submitList(newItems: List<PartitionItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val rootCard: View = view.findViewById(R.id.part_card_root)
            val dot: ImageView = view.findViewById(R.id.part_status_dot)
            val id: TextView = view.findViewById(R.id.part_id)
            val mount: TextView = view.findViewById(R.id.part_mount)
            val statusPill: TextView = view.findViewById(R.id.part_status_pill)
            val layerInfo: TextView = view.findViewById(R.id.part_layer_info)
            val stats: TextView = view.findViewById(R.id.part_stats)
            val btnMore: ImageView = view.findViewById(R.id.btn_part_more)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_partition, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val p = ThemeManager.getCurrentPalette(holder.itemView.context)
            (holder.rootCard as? MaterialCardView)?.let { card ->
                card.setCardBackgroundColor(p.cardBg)
                card.strokeColor = p.cardBorder
            }
            holder.id.setTextColor(p.textPrimary)
            holder.mount.setTextColor(p.textSecondary)
            holder.layerInfo.setTextColor(p.textSecondary)
            holder.stats.setTextColor(p.accent)
            holder.btnMore.imageTintList = ColorStateList.valueOf(p.textSecondary)

            holder.id.text = item.def.id
            holder.mount.text = item.def.mountPoint

            if (item.hasImage && item.totalBytes > 0L) {
                val quotaMiB = (item.totalBytes / (1024 * 1024)).coerceAtLeast(1L)
                holder.layerInfo.visibility = View.VISIBLE
                holder.layerInfo.text = holder.itemView.context.getString(R.string.part_quota_fmt, quotaMiB)
                holder.stats.visibility = View.VISIBLE
                holder.stats.text = item.stats
            } else {
                holder.layerInfo.visibility = View.GONE
                holder.stats.visibility = View.VISIBLE
                holder.stats.text = holder.itemView.context.getString(R.string.part_no_image)
            }

            val colorGreen = Color.rgb(26, 127, 55)
            val colorGray = Color.rgb(87, 96, 106)

            if (item.isLive) {
                holder.dot.setColorFilter(if (p.isDark) Color.parseColor("#4ADE80") else Color.rgb(26, 127, 55))
                holder.statusPill.text = "LIVE"
                ThemeManager.stylePill(holder.statusPill, PillType.GREEN, p)
            } else {
                holder.dot.setColorFilter(if (p.isDark) Color.parseColor("#94A3B8") else Color.rgb(87, 96, 106))
                holder.statusPill.text = "OFFLINE"
                ThemeManager.stylePill(holder.statusPill, PillType.GRAY, p)
            }

            ViewAnimUtil.addPressScaleEffect(holder.rootCard)
            ViewAnimUtil.addPressScaleEffect(holder.btnMore)

            holder.rootCard.setOnClickListener {
                onItemClick(item)
            }
            holder.rootCard.setOnLongClickListener {
                onMenuClick(item)
                true
            }
            holder.btnMore.setOnClickListener {
                onMenuClick(item)
            }
        }
    }
}
