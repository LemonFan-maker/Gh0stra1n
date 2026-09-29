package com.orionisli.gh0stra1n

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class CreateImageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTITION_ID = "extra_partition_id"
    }

    private lateinit var btnBack: ImageView
    private lateinit var btnResetPreset: TextView
    private lateinit var chipGroupPartition: ChipGroup
    private lateinit var editSizeMiB: EditText
    private lateinit var txtSizeReadable: TextView

    private lateinit var btnSize256: MaterialButton
    private lateinit var btnSize512: MaterialButton
    private lateinit var btnSize1024: MaterialButton
    private lateinit var btnSize2048: MaterialButton

    private lateinit var rgBlockSize: RadioGroup
    private lateinit var rbBs1024: RadioButton
    private lateinit var rbBs2048: RadioButton
    private lateinit var rbBs4096: RadioButton

    private lateinit var rgInodeRatio: RadioGroup
    private lateinit var rbInode4096: RadioButton
    private lateinit var rbInode8192: RadioButton
    private lateinit var rbInode16384: RadioButton

    private lateinit var rgReservedRatio: RadioGroup
    private lateinit var rbRes0: RadioButton
    private lateinit var rbRes1: RadioButton
    private lateinit var rbRes5: RadioButton

    private lateinit var swDirIndex: MaterialSwitch
    private lateinit var swFiletype: MaterialSwitch
    private lateinit var swSparseSuper: MaterialSwitch
    private lateinit var swSavePreset: MaterialSwitch

    private lateinit var btnCreateImage: MaterialButton

    private var targetPartId: String = "system"
    private val isCreateRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyStyleTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_image)
        ThemeManager.applyToActivity(this)

        HapticUtil.init(this)
        initViews()
        setupInsets()
        loadInitialPreset()
        setupListeners()
    }

    override fun onDestroy() {
        isCreateRunning.set(false)
        super.onDestroy()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        btnResetPreset = findViewById(R.id.btn_reset_preset)
        chipGroupPartition = findViewById(R.id.chip_group_partition)
        editSizeMiB = findViewById(R.id.edit_size_mib)
        txtSizeReadable = findViewById(R.id.txt_size_readable)

        btnSize256 = findViewById(R.id.btn_size_256)
        btnSize512 = findViewById(R.id.btn_size_512)
        btnSize1024 = findViewById(R.id.btn_size_1024)
        btnSize2048 = findViewById(R.id.btn_size_2048)

        rgBlockSize = findViewById(R.id.rg_block_size)
        rbBs1024 = findViewById(R.id.rb_bs_1024)
        rbBs2048 = findViewById(R.id.rb_bs_2048)
        rbBs4096 = findViewById(R.id.rb_bs_4096)

        rgInodeRatio = findViewById(R.id.rg_inode_ratio)
        rbInode4096 = findViewById(R.id.rb_inode_4096)
        rbInode8192 = findViewById(R.id.rb_inode_8192)
        rbInode16384 = findViewById(R.id.rb_inode_16384)

        rgReservedRatio = findViewById(R.id.rg_reserved_ratio)
        rbRes0 = findViewById(R.id.rb_res_0)
        rbRes1 = findViewById(R.id.rb_res_1)
        rbRes5 = findViewById(R.id.rb_res_5)

        swDirIndex = findViewById(R.id.sw_dir_index)
        swFiletype = findViewById(R.id.sw_filetype)
        swSparseSuper = findViewById(R.id.sw_sparse_super)
        swSavePreset = findViewById(R.id.sw_save_preset)

        btnCreateImage = findViewById(R.id.btn_create_image)

        val mapleTypeface = ResourcesCompat.getFont(this, R.font.maple_mono_regular)
        val chipIds = intArrayOf(
            R.id.chip_system,
            R.id.chip_vendor,
            R.id.chip_product,
            R.id.chip_system_ext,
            R.id.chip_odm
        )
        for (cid in chipIds) {
            findViewById<Chip>(cid)?.let { chip ->
                chip.setTextAppearanceResource(R.style.Gh0stra1n_TextAppearance_Chip)
                if (mapleTypeface != null) {
                    chip.typeface = mapleTypeface
                }
            }
        }

        ViewAnimUtil.addPressScaleEffect(btnBack)
        ViewAnimUtil.addPressScaleEffect(btnCreateImage)
    }

    private fun setupInsets() {
        val root = findViewById<View>(R.id.root_create_image)
        val header = findViewById<View>(R.id.header_bar)
        val bottomCard = findViewById<View>(R.id.bottom_action_card)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            header.setPadding(dp(20), statusBars.top + dp(6), dp(20), dp(6))

            val lp = bottomCard.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = navBars.bottom + dp(12)
            bottomCard.layoutParams = lp

            insets
        }
    }

    private fun canTouchUi(): Boolean = !isFinishing && !isDestroyed

    private fun loadInitialPreset() {
        val requestedPart = intent.getStringExtra(EXTRA_PARTITION_ID) ?: "system"
        targetPartId = requestedPart

        when (requestedPart) {
            "system" -> findViewById<Chip>(R.id.chip_system).isChecked = true
            "vendor" -> findViewById<Chip>(R.id.chip_vendor).isChecked = true
            "product" -> findViewById<Chip>(R.id.chip_product).isChecked = true
            "system_ext" -> findViewById<Chip>(R.id.chip_system_ext).isChecked = true
            "odm" -> findViewById<Chip>(R.id.chip_odm).isChecked = true
        }

        Thread {
            val preset = ManifestStore.loadPreset()
            val partDef = PartitionTable.byId[requestedPart]
            val initialSize = if (preset.defaultSizeMiB == 1024L && partDef != null) {
                partDef.defaultSizeMiB
            } else {
                preset.defaultSizeMiB
            }
            val resolved = preset.copy(defaultSizeMiB = initialSize)
            runOnUiThread {
                if (!canTouchUi()) return@runOnUiThread
                applyPresetToUi(resolved)
            }
        }.start()

        Thread {
            for (p in PartitionTable.ALL) {
                val hasImg = ImageManager.listImages(p).first.isNotEmpty()
                runOnUiThread {
                    if (!canTouchUi()) return@runOnUiThread
                    val chipId = when (p.id) {
                        "system" -> R.id.chip_system
                        "vendor" -> R.id.chip_vendor
                        "product" -> R.id.chip_product
                        "system_ext" -> R.id.chip_system_ext
                        "odm" -> R.id.chip_odm
                        else -> 0
                    }
                    if (chipId != 0) {
                        findViewById<Chip>(chipId)?.let { chip ->
                            if (hasImg) {
                                chip.text = "${p.id} (${getString(R.string.menu_pill_exists)})"
                            }
                        }
                    }
                }
            }
        }.start()
    }

    private fun applyPresetToUi(preset: ManifestStore.ImagePresetConfig) {
        editSizeMiB.setText(preset.defaultSizeMiB.toString())
        updateReadableSize(preset.defaultSizeMiB)

        when (preset.blockSize) {
            1024 -> rbBs1024.isChecked = true
            2048 -> rbBs2048.isChecked = true
            else -> rbBs4096.isChecked = true
        }

        when (preset.bytesPerInode) {
            4096 -> rbInode4096.isChecked = true
            16384 -> rbInode16384.isChecked = true
            else -> rbInode8192.isChecked = true
        }

        when (preset.reservedRatioPct) {
            0 -> rbRes0.isChecked = true
            5 -> rbRes5.isChecked = true
            else -> rbRes1.isChecked = true
        }

        swDirIndex.isChecked = preset.enableDirIndex
        swFiletype.isChecked = preset.enableFiletype
        swSparseSuper.isChecked = preset.enableSparseSuper
    }

    private fun updateReadableSize(mib: Long) {
        if (mib >= 1024) {
            val gib = mib.toDouble() / 1024.0
            txtSizeReadable.text = "≈ %.2f GiB".format(gib)
        } else {
            txtSizeReadable.text = "$mib MiB"
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            HapticUtil.click(it)
            finish()
        }

        btnResetPreset.setOnClickListener {
            HapticUtil.click(it)
            applyPresetToUi(ManifestStore.ImagePresetConfig())
            Toast.makeText(this, getString(R.string.toast_params_reset), Toast.LENGTH_SHORT).show()
        }

        chipGroupPartition.setOnCheckedStateChangeListener { _, checkedIds ->
            HapticUtil.tick()
            if (checkedIds.isNotEmpty()) {
                val newPartId = when (checkedIds[0]) {
                    R.id.chip_system -> "system"
                    R.id.chip_vendor -> "vendor"
                    R.id.chip_product -> "product"
                    R.id.chip_system_ext -> "system_ext"
                    R.id.chip_odm -> "odm"
                    else -> "system"
                }
                targetPartId = newPartId
            }
        }

        editSizeMiB.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val mib = s?.toString()?.toLongOrNull() ?: 0L
                updateReadableSize(mib)
            }
        })

        btnSize256.setOnClickListener {
            HapticUtil.click(it)
            editSizeMiB.setText("256")
        }
        btnSize512.setOnClickListener {
            HapticUtil.click(it)
            editSizeMiB.setText("512")
        }
        btnSize1024.setOnClickListener {
            HapticUtil.click(it)
            editSizeMiB.setText("1024")
        }
        btnSize2048.setOnClickListener {
            HapticUtil.click(it)
            editSizeMiB.setText("2048")
        }

        btnCreateImage.setOnClickListener {
            HapticUtil.heavyClick(it)
            onConfirmCreate()
        }
    }

    private fun getCurrentConfig(): Pair<Long, ManifestStore.ImagePresetConfig> {
        val sizeMiB = editSizeMiB.text.toString().toLongOrNull() ?: 1024L
        val blockSize = when {
            rbBs1024.isChecked -> 1024
            rbBs2048.isChecked -> 2048
            else -> 4096
        }
        val bytesPerInode = when {
            rbInode4096.isChecked -> 4096
            rbInode16384.isChecked -> 16384
            else -> 8192
        }
        val reservedRatio = when {
            rbRes0.isChecked -> 0
            rbRes5.isChecked -> 5
            else -> 1
        }

        val preset = ManifestStore.ImagePresetConfig(
            fsType = "ext4",
            defaultSizeMiB = sizeMiB,
            blockSize = blockSize,
            bytesPerInode = bytesPerInode,
            reservedRatioPct = reservedRatio,
            enableDirIndex = swDirIndex.isChecked,
            enableFiletype = swFiletype.isChecked,
            enableSparseSuper = swSparseSuper.isChecked
        )
        return sizeMiB to preset
    }

    private fun onConfirmCreate() {
        val (sizeMiB, preset) = getCurrentConfig()

        if (sizeMiB < 16) {
            Toast.makeText(this, getString(R.string.toast_create_err_min), Toast.LENGTH_SHORT).show()
            return
        }
        if (sizeMiB > 16384) {
            Toast.makeText(this, getString(R.string.toast_create_err_max), Toast.LENGTH_SHORT).show()
            return
        }

        val partId = targetPartId
        val part = PartitionTable.byId[partId]
        val sizeText = txtSizeReadable.text

        Thread {
            val exists = part != null && ImageManager.listImages(part).first.isNotEmpty()
            runOnUiThread {
                if (!canTouchUi()) return@runOnUiThread
                if (exists) {
                    HapticUtil.warning()
                    Toast.makeText(this, getString(R.string.create_image_already_exists, partId), Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                showCreateConfirmDialog(partId, sizeMiB, preset, sizeText)
            }
        }.start()
    }

    private fun showCreateConfirmDialog(
        partId: String,
        sizeMiB: Long,
        preset: ManifestStore.ImagePresetConfig,
        sizeText: CharSequence
    ) {
        val msg = getString(
            R.string.create_image_confirm_msg_fmt,
            partId,
            sizeMiB,
            sizeText,
            preset.blockSize,
            preset.bytesPerInode,
            preset.reservedRatioPct
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_image_confirm_title)
            .setMessage(msg)
            .setPositiveButton(R.string.create_image_btn_confirm) { _, _ ->
                HapticUtil.heavyClick(btnCreateImage)
                executeCreate(sizeMiB, preset)
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                HapticUtil.click(btnCreateImage)
            }
            .show()
    }

    private fun executeCreate(sizeMiB: Long, preset: ManifestStore.ImagePresetConfig) {
        if (!isCreateRunning.compareAndSet(false, true)) return

        btnCreateImage.isEnabled = false
        btnCreateImage.text = getString(R.string.create_image_in_progress)

        if (swSavePreset.isChecked) {
            ManifestStore.savePreset(preset)
        }

        val partId = targetPartId
        Thread {
            val res = ImageManager.createImageWithPreset(partId, sizeMiB, preset)
            runOnUiThread {
                if (!canTouchUi()) return@runOnUiThread
                isCreateRunning.set(false)
                btnCreateImage.isEnabled = true
                btnCreateImage.text = getString(R.string.create_image_btn_submit)

                if (res.isSuccess) {
                    HapticUtil.heavyClick(btnCreateImage)
                    Toast.makeText(this, getString(R.string.toast_create_success_fmt, res.getOrNull()?.substringAfterLast('/')), Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    HapticUtil.error()
                    Toast.makeText(this, getString(R.string.toast_create_failed_fmt, res.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
