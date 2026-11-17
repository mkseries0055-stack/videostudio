package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private fun tile(ctx: Context, icon: Int, label: String, ai: Boolean, onClick: () -> Unit): View {
    val d = { v: Int -> UI.dp(ctx, v) }
    val box = FrameLayout(ctx).apply {
        background = UI.cardBg(ctx, UI.card, 16)
        isClickable = true
        setOnClickListener { onClick() }
    }
    val inner = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(d(8), d(10), d(8), d(10))
    }
    inner.addView(
        ImageView(ctx).apply { setImageResource(icon) },
        LinearLayout.LayoutParams(d(44), d(44))
    )
    inner.addView(TextView(ctx).apply {
        text = label
        textSize = 13f
        setTextColor(UI.textMain)
        gravity = Gravity.CENTER
        setPadding(0, d(8), 0, 0)
    })
    box.addView(
        inner,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    )
    if (ai) {
        val badgeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        badgeParams.gravity = Gravity.TOP or Gravity.START
        badgeParams.setMargins(d(8), d(8), 0, 0)
        box.addView(TextView(ctx).apply {
            text = "✨ AI"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = UI.round(ctx, Color.parseColor("#7C3AED"), 8)
            setPadding(d(6), d(2), d(6), d(2))
        }, badgeParams)
    }
    return box
}

private fun row(ctx: Context, tiles: List<View>): LinearLayout {
    val r = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    tiles.forEachIndexed { i, t ->
        val p = LinearLayout.LayoutParams(0, UI.dp(ctx, 96), 1f)
        if (i > 0) p.leftMargin = UI.dp(ctx, 10)
        r.addView(t, p)
    }
    return r
}

fun buildHome(ctx: Context, onTool: (String) -> Unit): View {
    val d = { v: Int -> UI.dp(ctx, v) }
    val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(d(16), d(16), d(16), d(24))
    }

    val top = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    top.addView(TextView(ctx).apply {
        text = "Video Studio"
        textSize = 20f
        setTextColor(UI.textMain)
        typeface = Typeface.DEFAULT_BOLD
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    top.addView(TextView(ctx).apply {
        text = "⚙️"
        textSize = 20f
        setPadding(d(8), d(8), d(8), d(8))
        setOnClickListener { onTool("Settings") }
    })
    col.addView(top)

    val big = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = UI.gradient(ctx, UI.lightBlue, UI.blue, 20)
        isClickable = true
        setOnClickListener { onTool("Convert or Compress") }
    }
    big.addView(
        ImageView(ctx).apply { setImageResource(R.drawable.ic_convert) },
        LinearLayout.LayoutParams(d(64), d(64))
    )
    big.addView(TextView(ctx).apply {
        text = "Convert"
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(0, d(4), 0, 0)
    })
    val bigParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(128))
    bigParams.topMargin = d(16)
    col.addView(big, bigParams)

    fun mk(icon: Int, label: String, ai: Boolean = false): View =
        tile(ctx, icon, label, ai) { onTool(label) }

    fun addRow(tiles: List<View>) {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.topMargin = d(12)
        col.addView(row(ctx, tiles), p)
    }

    addRow(listOf(
        mk(R.drawable.ic_remove_noise, "Remove Noise", true),
        mk(R.drawable.ic_split, "Split Music & Vocal", true)
    ))
    addRow(listOf(
        mk(R.drawable.ic_video_cutter, "Video Cutter"),
        mk(R.drawable.ic_video_merger, "Video Merger"),
        mk(R.drawable.ic_video_to_audio, "Video To Audio")
    ))
    addRow(listOf(
        mk(R.drawable.ic_audio_converter, "Audio Converter"),
        mk(R.drawable.ic_audio_cutter, "Audio Cutter"),
        mk(R.drawable.ic_audio_merger, "Audio Merger")
    ))

    col.addView(TextView(ctx).apply {
        text = "More Tools"
        textSize = 15f
        setTextColor(UI.textMain)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, d(24), 0, d(6))
    })
    col.addView(TextView(ctx).apply {
        text = "আরও টুল শীঘ্রই যোগ হবে"
        textSize = 13f
        setTextColor(UI.textSub)
    })

    return ScrollView(ctx).apply { addView(col) }
}
