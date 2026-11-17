package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UI {
    val bg = Color.parseColor("#F3F4F6")
    val card = Color.WHITE
    val border = Color.parseColor("#E5E7EB")
    val textMain = Color.parseColor("#111827")
    val textSub = Color.parseColor("#6B7280")
    val blue = Color.parseColor("#2563EB")
    val lightBlue = Color.parseColor("#38BDF8")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun cardBg(c: Context, color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
            setStroke(dp(c, 1), border)
        }

    fun round(c: Context, color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
        }

    fun gradient(c: Context, c1: Int, c2: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(c, radiusDp).toFloat()
        }
}
