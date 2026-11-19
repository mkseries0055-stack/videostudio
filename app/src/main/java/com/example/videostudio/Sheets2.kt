package com.example.videostudio

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object Sheets2 {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // অডিও / সাবটাইটেল ট্র্যাক: + ADD EXTERNAL, চেকবক্স, NONE, DONE
    fun tracks(
        ctx: Context,
        title: String,
        tracks: List<TrackInfo>,
        selected: Set<Int>,
        emptyMsg: String,
        addLabel: String?,
        onAdd: () -> Unit,
        onDone: (Set<Int>) -> Unit
    ) {
        val d = { v: Int -> UI.dp(ctx, v) }
        val dlg = Dialog(ctx)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sel = LinkedHashSet<Int>(selected)

        val r = d(22).toFloat()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(UI.card)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(0, d(16), 0, d(10))
        }

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(22), 0, d(16), d(8))
        }
        head.addView(TextView(ctx).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.blue)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(TextView(ctx).apply {
            text = "DONE"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = UI.round(ctx, UI.blue, 8)
            setPadding(d(18), d(8), d(18), d(8))
            isClickable = true
            setOnClickListener {
                dlg.dismiss()
                onDone(sel.toSet())
            }
        })
        box.addView(head)

        if (addLabel != null) {
            val add = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(d(22), d(10), d(16), d(10))
                isClickable = true
                setOnClickListener {
                    dlg.dismiss()
                    onDone(sel.toSet())
                    onAdd()
                }
            }
            add.addView(lineIcon(ctx, LineIcons.ADD_FOLDER, UI.textMain, 22))
            add.addView(TextView(ctx).apply {
                text = addLabel
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UI.textMain)
                setPadding(d(12), 0, 0, 0)
            })
            box.addView(add)
        }

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        if (tracks.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = emptyMsg
                textSize = 14f
                setTextColor(UI.textSub)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(d(22), d(28), d(22), d(28))
            })
        } else {
            val boxes = ArrayList<CheckBox>()
            var noneBox: CheckBox? = null

            fun sync() {
                boxes.forEachIndexed { i, b -> b.isChecked = sel.contains(i) }
                noneBox?.isChecked = sel.isEmpty()
            }

            fun makeRow(label: String, sub: String, onClick: () -> Unit): Pair<LinearLayout, CheckBox> {
                val cb = CheckBox(ctx).apply {
                    isClickable = false
                    isFocusable = false
                    buttonTintList = ColorStateList.valueOf(UI.blue)
                }
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(d(22), d(10), d(16), d(10))
                    isClickable = true
                    setOnClickListener { onClick() }
                }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.addView(TextView(ctx).apply {
                    text = label
                    textSize = 16f
                    setTextColor(UI.textMain)
                })
                if (sub.isNotEmpty()) {
                    col.addView(TextView(ctx).apply {
                        text = sub
                        textSize = 12f
                        setTextColor(UI.textSub)
                    })
                }
                row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(cb)
                return Pair(row, cb)
            }

            tracks.forEachIndexed { i, t ->
                val (row, cb) = makeRow(t.short, t.detail) {
                    if (!sel.remove(i)) sel.add(i)
                    sync()
                }
                boxes.add(cb)
                list.addView(row)
            }
            val (nRow, nCb) = makeRow("NONE", "") {
                sel.clear()
                sync()
            }
            noneBox = nCb
            list.addView(nRow)
            sync()
        }

        val maxH = (ctx.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val scroll = ScrollView(ctx).apply { addView(list) }
        box.addView(
            scroll,
            LinearLayout.LayoutParams(MATCH, if (tracks.size > 5) maxH else WRAP)
        )

        dlg.setOnCancelListener { onDone(sel.toSet()) }
        dlg.setContentView(box)
        dlg.window?.apply {
            setLayout(MATCH, WRAP)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }

    // ADD CUSTOM RESOLUTION: Width, Height, MAINTAIN ASPECT RATIO
    fun customResolution(ctx: Context, initW: Int, initH: Int, onOk: (Int, Int) -> Unit) {
        val d = { v: Int -> UI.dp(ctx, v) }
        val dlg = Dialog(ctx)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = UI.round(ctx, UI.card, 18)
            setPadding(d(22), d(20), d(22), d(16))
        }
        box.addView(TextView(ctx).apply {
            text = "ADD CUSTOM RESOLUTION"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.blue)
        })

        fun field(init: Int) = EditText(ctx).apply {
            setText(init.toString())
            setTextColor(UI.textMain)
            textSize = 16f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(UI.blue)
        }
        val etW = field(initW)
        val etH = field(initH)

        fun label(t: String) = TextView(ctx).apply {
            text = t
            textSize = 12f
            setTextColor(UI.textSub)
            gravity = Gravity.CENTER
        }

        val labels = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        labels.addView(label("Width"), LinearLayout.LayoutParams(0, WRAP, 1f))
        labels.addView(label(""), LinearLayout.LayoutParams(d(34), WRAP))
        labels.addView(label("Height"), LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(labels, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(12) })

        val inputs = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputs.addView(etW, LinearLayout.LayoutParams(0, WRAP, 1f))
        inputs.addView(TextView(ctx).apply {
            text = "×"
            textSize = 20f
            setTextColor(UI.textMain)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(d(34), WRAP))
        inputs.addView(etH, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(inputs, LinearLayout.LayoutParams(MATCH, WRAP))

        val cb = CheckBox(ctx).apply {
            text = "MAINTAIN ASPECT RATIO"
            isChecked = true
            textSize = 13f
            setTextColor(UI.textMain)
            buttonTintList = ColorStateList.valueOf(UI.blue)
        }
        box.addView(cb, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(10) })

        val ratio = if (initH > 0) initW.toFloat() / initH.toFloat() else 1f
        var busy = false
        fun watch(src: EditText, dst: EditText, fromW: Boolean) {
            src.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) {
                    if (busy || !cb.isChecked) return
                    val v = e?.toString()?.toIntOrNull() ?: return
                    busy = true
                    val other = if (fromW) Math.round(v / ratio) else Math.round(v * ratio)
                    dst.setText(Math.max(1, other).toString())
                    busy = false
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        watch(etW, etH, true)
        watch(etH, etW, false)

        val cancel = TextView(ctx).apply {
            text = "CANCEL"
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
            setOnClickListener { dlg.dismiss() }
        }
        val ok = TextView(ctx).apply {
            text = "OK"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = UI.round(ctx, UI.blue, 10)
            setPadding(d(8), d(12), d(8), d(12))
            isClickable = true
            setOnClickListener {
                val w = etW.text.toString().toIntOrNull() ?: 0
                val h = etH.text.toString().toIntOrNull() ?: 0
                if (w < 16 || h < 16 || w > 4096 || h > 4096) {
                    Toast.makeText(ctx, "১৬ থেকে ৪০৯৬ এর মধ্যে মাপ দিন", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dlg.dismiss()
                onOk(w / 2 * 2, h / 2 * 2)
            }
        }
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(cancel, LinearLayout.LayoutParams(0, WRAP, 1f))
        btnRow.addView(ok, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = d(12) })
        box.addView(btnRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(16) })

        dlg.setContentView(box)
        dlg.window?.apply {
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.88f).toInt(), WRAP)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }
}
