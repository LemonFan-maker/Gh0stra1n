package com.orionisli.gh0stra1n

import android.content.Context
import android.view.View

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun View.dp(v: Int): Int = context.dp(v)
