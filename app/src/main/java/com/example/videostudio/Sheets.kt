package com.example.videostudio

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Sheets {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // নিচ থেকে উঠে আসা তালিকা (Format, Resolution ইত্যাদির জন্য)
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
            col.addView(TextView(ctx).apply {
                text = o.label
                textSize = 16f
                setTextColor(UI.textMain)
            })
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

    // CONVERT চাপলে ফাইলের নাম ও ADD TO QUEUE / START NOW
    fun nameDialog(
        ctx: Context,
        multi: Boolean,
        s: ConvertSettings,
        defaultName: String,
        onResult: (Boolean) -> Unit
    ) {
        val d = { v: Int -> UI.dp(ctx, v) }
        val dlg = Dialog(ctx)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = UI.round(ctx, UI.card, 18)
            setPadding(d(22), d(20), d(22), d(16))
        }
        box.addView(TextView(ctx).apply {
            text = if (multi) "Set Prefix Name" else "Output File Name"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.blue)
        })

        val et = EditText(ctx).apply {
            setText(if (multi) s.prefix else defaultName)
            setTextColor(UI.textMain)
            textSize = 15f
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(UI.blue)
        }
        box.addView(et, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(10) })

        var cbOrig: CheckBox? = null
        if (multi) {
            val cb = CheckBox(ctx).apply {
                text = "Use original file name"
                isChecked = s.useOriginalName
                setTextColor(UI.textMain)
                buttonTintList = ColorStateList.valueOf(UI.blue)
            }
            et.isEnabled = !cb.isChecked
            et.alpha = if (cb.isChecked) 0.5f else 1f
            cb.setOnCheckedChangeListener { _, c ->
                et.isEnabled = !c
                et.alpha = if (c) 0.5f else 1f
            }
            box.addView(cb)
            cbOrig = cb
        }

        val cbMeta = CheckBox(ctx).apply {
            text = "Keep Metadata"
            isChecked = s.keepMetadata
            setTextColor(UI.textMain)
            buttonTintList = ColorStateList.valueOf(UI.blue)
        }
        box.addView(cbMeta)

        box.addView(TextView(ctx).apply {
            text = "Hardware Acceleration — শীঘ্রই আসছে"
            textSize = 13f
            setTextColor(UI.textSub)
            setPadding(d(4), d(8), 0, d(8))
        })

        fun commit(start: Boolean) {
            s.keepMetadata = cbMeta.isChecked
            val typed = et.text.toString().trim()
            if (multi) {
                s.useOriginalName = cbOrig?.isChecked ?: true
                s.prefix = if (typed.isEmpty()) "video_converter_" else typed
            } else {
                s.outputName = if (typed.isEmpty()) defaultName else typed
            }
            dlg.dismiss()
            onResult(start)
        }

        val addBtn = TextView(ctx).apply {
            text = "ADD TO QUEUE"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.blue)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = d(10).toFloat()
                setStroke(d(2), UI.blue)
            }
            setPadding(d(8), d(12), d(8), d(12))
            isClickable = true
            setOnClickListener { commit(false) }
        }
        val startBtn = TextView(ctx).apply {
            text = "START NOW"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = UI.round(ctx, UI.blue, 10)
            setPadding(d(8), d(12), d(8), d(12))
            isClickable = true
            setOnClickListener { commit(true) }
        }
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(addBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        btnRow.addView(startBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = d(12) })
        box.addView(btnRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(8) })

        dlg.setContentView(box)
        dlg.window?.apply {
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.9f).toInt(), WRAP)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }
}
