package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UI {
    var dark = false
    var bg = Color.parseColor("#F3F4F6")
    var card = Color.WHITE
    var border = Color.parseColor("#E5E7EB")
    var textMain = Color.parseColor("#111827")
    var textSub = Color.parseColor("#6B7280")
    val blue = Color.parseColor("#2563EB")
    val lightBlue = Color.parseColor("#38BDF8")

    fun applyTheme(isDark: Boolean) {
        dark = isDark
        if (isDark) {
            bg = Color.parseColor("#0A0A0C")
            card = Color.parseColor("#1F2126")
            border = Color.parseColor("#2C2F36")
            textMain = Color.WHITE
            textSub = Color.parseColor("#9CA3AF")
        } else {
            bg = Color.parseColor("#F3F4F6")
            card = Color.WHITE
            border = Color.parseColor("#E5E7EB")
            textMain = Color.parseColor("#111827")
            textSub = Color.parseColor("#6B7280")
        }
    }

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
