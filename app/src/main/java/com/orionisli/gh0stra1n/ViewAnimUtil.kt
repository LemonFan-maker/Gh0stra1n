package com.orionisli.gh0stra1n

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object ViewAnimUtil {

    @SuppressLint("ClickableViewAccessibility")
    fun addPressScaleEffect(view: View, targetScale: Float = 0.96f) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(160)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()
                }
            }
            false
        }
    }

    fun animateTabEntrance(view: View) {
        view.alpha = 0f
        view.translationY = 25f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun pulse(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.28f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.28f, 1.0f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 320
            interpolator = OvershootInterpolator(1.6f)
            start()
        }
    }
}
