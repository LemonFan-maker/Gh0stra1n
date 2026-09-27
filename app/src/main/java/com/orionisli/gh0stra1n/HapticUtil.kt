package com.orionisli.gh0stra1n

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtil {
    private var vibrator: Vibrator? = null

    var strength: Int = 2

    fun init(context: Context) {
        val appContext = context.applicationContext
        val sp = appContext.getSharedPreferences("gh0stra1n_settings", Context.MODE_PRIVATE)
        strength = sp.getInt("haptic_strength", 2)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun preview(targetStrength: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        if (targetStrength <= 0) return

        when (targetStrength) {
            1 -> {
                // 微弱
                vibrateWithCustomScale(
                    ms = 8,
                    amplitude = 40,
                    primitive = VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    scale = 0.22f
                )
            }
            2 -> {
                // 标准
                vibrateWithCustomScale(
                    ms = 18,
                    amplitude = 135,
                    primitive = VibrationEffect.Composition.PRIMITIVE_CLICK,
                    scale = 0.65f
                )
            }
            3 -> {
                // 强力
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_CLICK
                    )
                ) {
                    try {
                        val comp = VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.75f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 12)
                        vib.vibrate(comp.compose())
                        return
                    } catch (_: Exception) {}
                }
                vibrateWithCustomScale(
                    ms = 40,
                    amplitude = 255,
                    primitive = VibrationEffect.Composition.PRIMITIVE_THUD,
                    scale = 1.0f
                )
            }
        }
    }


    fun detentTick() {
        if (strength == 0) return
        val amp = when (strength) {
            1 -> 15
            3 -> 65
            else -> 32
        }
        val scale = when (strength) {
            1 -> 0.06f
            3 -> 0.28f
            else -> 0.14f
        }
        vibrateWithCustomScale(
            ms = 3,
            amplitude = amp,
            primitive = VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            scale = scale
        )
    }

    fun tick(view: View? = null) {
        if (strength == 0) return
        val amp = when (strength) {
            1 -> 40
            3 -> 160
            else -> 90
        }
        val scale = when (strength) {
            1 -> 0.22f
            3 -> 0.75f
            else -> 0.45f
        }
        vibrateWithCustomScale(
            ms = 10,
            amplitude = amp,
            primitive = VibrationEffect.Composition.PRIMITIVE_TICK,
            scale = scale
        )
    }

    fun click(view: View? = null) {
        if (strength == 0) return
        val amp = when (strength) {
            1 -> 60
            3 -> 220
            else -> 135
        }
        val scale = when (strength) {
            1 -> 0.35f
            3 -> 0.90f
            else -> 0.65f
        }
        vibrateWithCustomScale(
            ms = 16,
            amplitude = amp,
            primitive = VibrationEffect.Composition.PRIMITIVE_CLICK,
            scale = scale
        )
    }

    fun heavyClick(view: View? = null) {
        if (strength == 0) return
        val amp = when (strength) {
            1 -> 85
            3 -> 255
            else -> 180
        }
        val scale = when (strength) {
            1 -> 0.45f
            3 -> 1.0f
            else -> 0.80f
        }
        vibrateWithCustomScale(
            ms = 30,
            amplitude = amp,
            primitive = VibrationEffect.Composition.PRIMITIVE_THUD,
            scale = scale
        )
    }

    fun confirm() {
        if (strength == 0) return
        val amp = when (strength) { 1 -> 80; 3 -> 240; else -> 160 }
        vibrateOneShot(24, amp)
    }

    fun warning() {
        if (strength == 0) return
        val amp = when (strength) { 1 -> 100; 3 -> 255; else -> 200 }
        vibrateOneShot(35, amp)
    }

    fun success() {
        if (strength == 0) return
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val a1 = when (strength) { 1 -> 70; 3 -> 220; else -> 140 }
            val a2 = when (strength) { 1 -> 110; 3 -> 255; else -> 200 }
            val timings = longArrayOf(0, 16, 45, 22)
            val amplitudes = intArrayOf(0, a1, 0, a2)
            try {
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                return
            } catch (_: Exception) {}
        }
        vibrateOneShot(25, 180)
    }

    fun error() {
        if (strength == 0) return
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val a = when (strength) { 1 -> 120; 3 -> 255; else -> 220 }
            val timings = longArrayOf(0, 30, 50, 40)
            val amplitudes = intArrayOf(0, a, 0, a)
            try {
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                return
            } catch (_: Exception) {}
        }
        vibrateOneShot(45, 255)
    }

    private fun vibrateWithCustomScale(ms: Long, amplitude: Int, primitive: Int, scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vib.areAllPrimitivesSupported(primitive)) {
            try {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(primitive, scale.coerceIn(0.05f, 1.0f))
                    .compose()
                vib.vibrate(effect)
                return
            } catch (_: Exception) {}
        }

        vibrateOneShot(ms, amplitude)
    }

    private fun vibrateOneShot(ms: Long, amplitude: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (vib.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                vib.vibrate(VibrationEffect.createOneShot(ms.coerceAtLeast(1L), amp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        } catch (_: Exception) {}
    }
}
