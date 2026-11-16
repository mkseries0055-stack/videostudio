package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UI {
    val bg = Color.parseColor("#0F1115")
    val card = Color.parseColor("#1B1E25")
    val textSub = Color.parseColor("#9CA3AF")
    val orange = Color.parseColor("#FB923C")
    val pink = Color.parseColor("#F43F5E")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun round(c: Context, color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
        }

    fun gradient(c: Context, c1: Int, c2: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(c, radiusDp).toFloat()
        }
}
