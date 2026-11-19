package com.example.videostudio

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Sheets3 {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // নিচ থেকে উঠে আসা তালিকা, নামের পাশে নীল ট্যাগ (যেমন ORIGINAL) সহ
    fun choice(
        ctx: Context,
        title: String,
        options: List<Opt>,
        selected: Int,
        onPick: (Int) -> Unit
    ) {
        val d = { v: Int -> UI.dp(ctx, v) }
        val dlg = Dialog(ctx)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val r = d(22).toFloat()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(UI.card)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(0, d(18), 0, d(10))
        }
        box.addView(TextView(ctx).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.blue)
            setPadding(d(22), 0, d(22), d(8))
        })

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        options.forEachIndexed { i, o ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(d(22), d(13), d(22), d(13))
                isClickable = true
                setOnClickListener {
                    dlg.dismiss()
                    onPick(i)
                }
            }
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

            val labelRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            labelRow.addView(TextView(ctx).apply {
                text = o.label
                textSize = 16f
                setTextColor(UI.textMain)
            })
            if (o.tag.isNotEmpty()) {
                labelRow.addView(TextView(ctx).apply {
                    text = o.tag
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(UI.blue)
                    setPadding(d(8), d(3), 0, 0)
                })
            }
            col.addView(labelRow)

            if (o.sub.isNotEmpty()) {
                col.addView(TextView(ctx).apply {
                    text = o.sub
                    textSize = 12f
                    setTextColor(UI.textSub)
                })
            }
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (i == selected) {
                row.addView(TextView(ctx).apply {
                    text = "✓"
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(UI.blue)
                })
            }
            list.addView(row)
        }

        val maxH = (ctx.resources.displayMetrics.heightPixels * 0.62f).toInt()
        val scroll = ScrollView(ctx).apply { addView(list) }
        box.addView(scroll, LinearLayout.LayoutParams(MATCH, if (options.size > 7) maxH else WRAP))

        dlg.setContentView(box)
        dlg.window?.apply {
            setLayout(MATCH, WRAP)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }
}
