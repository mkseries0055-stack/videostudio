package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

// mode: 0 = লাইট, 1 = ডার্ক, 2 = অটো
fun buildThemeControl(ctx: Context, mode: Int, onSet: (Int) -> Unit): View {
    val d = { v: Int -> UI.dp(ctx, v) }
    val handler = Handler(Looper.getMainLooper())
    val isDark = UI.dark

    val holder = FrameLayout(ctx)

    val single = ImageView(ctx).apply {
        setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)
        setPadding(d(9), d(9), d(9), d(9))
    }
    holder.addView(
        single,
        FrameLayout.LayoutParams(d(44), d(44), Gravity.END or Gravity.CENTER_VERTICAL)
    )

    val pill = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = UI.cardBg(ctx, UI.card, 26)
        setPadding(d(6), d(4), d(6), d(4))
        visibility = View.GONE
    }

    fun chip(icon: Int, m: Int): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E5E7EB"))
            if (mode == m) setStroke(d(2), UI.blue)
        }
        return ImageView(ctx).apply {
            setImageResource(icon)
            background = bg
            setPadding(d(8), d(8), d(8), d(8))
            setOnClickListener { onSet(m) }
        }
    }

    pill.addView(chip(R.drawable.ic_sun, 0), LinearLayout.LayoutParams(d(40), d(40)))
    pill.addView(
        chip(R.drawable.ic_moon, 1),
        LinearLayout.LayoutParams(d(40), d(40)).apply { leftMargin = d(6) }
    )
    pill.addView(
        chip(R.drawable.ic_auto, 2),
        LinearLayout.LayoutParams(d(40), d(40)).apply { leftMargin = d(6) }
    )
    holder.addView(
        pill,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        )
    )

    var longFired = false
    val longRun = Runnable {
        longFired = true
        single.visibility = View.GONE
        pill.visibility = View.VISIBLE
        pill.post {
            pill.pivotX = pill.width.toFloat()
            pill.scaleX = 0.3f
            pill.animate().scaleX(1f).setDuration(180).start()
        }
        handler.postDelayed({
            pill.visibility = View.GONE
            single.visibility = View.VISIBLE
        }, 5000)
    }

    single.setOnTouchListener { _, ev ->
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                longFired = false
                handler.postDelayed(longRun, 2000)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longRun)
                if (!longFired) onSet(if (isDark) 0 else 1)
            }
            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longRun)
        }
        true
    }

    return holder
}
