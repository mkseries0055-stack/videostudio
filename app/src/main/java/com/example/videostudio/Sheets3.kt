package com.example.videostudio

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Sheets3 {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun hasBengali(s: String): Boolean = s.any { it in '\u0980'..'\u09FF' }

    // বাংলা অংশ বাদ দিয়ে শুধু ইংরেজি সাব-টেক্সট রাখা; নামের সাথে একই হলে বাদ
    private fun cleanSub(label: String, sub: String): String {
        if (sub.isEmpty()) return ""
        val parts = sub.split("•").map { it.trim() }.filter { it.isNotEmpty() && !hasBengali(it) }
        val joined = parts.joinToString("  •  ")
        return if (joined.equals(label, ignoreCase = true)) "" else joined
    }

    // নিচ থেকে উঠে আসা তালিকা: সারির মাঝে লাইন, নামের পাশে নীল ট্যাগ (যেমন ORIGINAL)
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
        val lineColor = if (UI.dark) Color.parseColor("#3A3D45") else Color.parseColor("#D1D5DB")

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
            setPadding(d(22), 0, d(22), d(10))
        })

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        options.forEachIndexed { i, o ->
            val sub = cleanSub(o.label, o.sub)

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(d(22), d(15), d(22), d(15))
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

            if (sub.isNotEmpty()) {
                col.addView(TextView(ctx).apply {
                    text = sub
                    textSize = 12f
                    setTextColor(UI.textSub)
                    setPadding(0, d(2), 0, 0)
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

            if (i < options.size - 1) {
                val line = View(ctx)
                line.setBackgroundColor(lineColor)
                list.addView(line, LinearLayout.LayoutParams(MATCH, d(1)))
            }
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
