package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.TextView

fun buildOutput(ctx: Context): View {
    return TextView(ctx).apply {
        text = "📁\nOutput Files\n\nকনভার্ট করা ফাইল এখানে দেখা যাবে"
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }
}
