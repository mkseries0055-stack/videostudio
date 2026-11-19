package com.example.videostudio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

private object Ic {
    const val CLOSE = "M6,6l12,12M18,6L6,18"
    const val PLAY = "M8,5l11,7l-11,7z"
    const val SHARE =
        "M15.5,5a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0M3.5,12a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0M15.5,19a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0M8.2,10.8l7.6,-4.4M8.2,13.2l7.6,4.4"
    const val TRASH = "M4,7h16M9,7V4h6v3M6,7l1,13h10l1,-13M10,11v6M14,11v6"
}

class ProgressScreen(
    private val ctx: Context,
    private val onClose: () -> Unit
) {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val d = { v: Int -> UI.dp(ctx, v) }
    private val main = Handler(Looper.getMainLooper())
    private val green = Color.parseColor("#16A34A")
    private val red = Color.parseColor("#DC2626")

    private class Card(
        val job: Job,
        val root: LinearLayout,
        val x: ImageView,
        val info: TextView,
        val bar: ProgressBar,
        val pct: TextView,
        val status: TextView,
        val result: LinearLayout,
        val resultText: TextView,
        val actions: LinearLayout
    )

    private val cards = LinkedHashMap<Long, Card>()
    private val listener: () -> Unit = { main.post { refresh() } }

    private lateinit var title: TextView
    private lateinit var headerAction: TextView
    private lateinit var overallBar: ProgressBar
    private lateinit var overallPct: TextView
    private lateinit var list: LinearLayout

    val root: View = build()

    init {
        QueueManager.addListener(listener)
        refresh()
    }

    fun close() {
        QueueManager.removeListener(listener)
        setKeepOn(false)
    }

    private fun setKeepOn(on: Boolean) {
        val w = (ctx as? Activity)?.window ?: return
        if (on) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun barOf(): ProgressBar =
        ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        }

    private fun build(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UI.bg)
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(4), d(8), d(14), d(8))
        }
        top.addView(ImageView(ctx).apply {
            setImageDrawable(PathIcon(LineIcons.BACK, UI.textMain))
            setPadding(d(11), d(11), d(11), d(11))
            layoutParams = LinearLayout.LayoutParams(d(44), d(44))
            setOnClickListener {
                close()
                onClose()
            }
        })
        title = TextView(ctx).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            setPadding(d(4), 0, 0, 0)
        }
        top.addView(title, lp(0, WRAP, 1f))
        headerAction = TextView(ctx).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(d(10), d(8), d(10), d(8))
            setOnClickListener {
                if (QueueManager.hasActive()) {
                    QueueManager.cancelAll()
                } else {
                    close()
                    onClose()
                }
            }
        }
        top.addView(headerAction)
        col.addView(top)

        val overall = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), 0, d(16), d(8))
        }
        overallPct = TextView(ctx).apply {
            textSize = 13f
            setTextColor(UI.textSub)
        }
        overall.addView(overallPct)
        overallBar = barOf().apply {
            progressTintList = ColorStateList.valueOf(UI.blue)
        }
        overall.addView(overallBar, lp(MATCH, d(8)).apply { topMargin = d(6) })
        col.addView(overall)

        list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(4), d(16), d(24))
        }
        val scroll = ScrollView(ctx).apply { addView(list) }
        col.addView(scroll, lp(MATCH, 0, 1f))

        return col
    }

    private fun makeCard(job: Job): Card {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(12), d(12), d(12), d(12))
        }

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val thumbBox = FrameLayout(ctx).apply {
            background = UI.round(ctx, Color.parseColor("#374151"), 10)
            clipToOutline = true
        }
        val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        thumbBox.addView(thumb, FrameLayout.LayoutParams(MATCH, MATCH))
        head.addView(thumbBox, lp(d(72), d(54)))
        head.addView(TextView(ctx).apply {
            text = job.item.name
            textSize = 13f
            setTextColor(UI.textMain)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(d(10), 0, d(6), 0)
        }, lp(0, WRAP, 1f))
        val x = ImageView(ctx).apply {
            setImageDrawable(PathIcon(Ic.CLOSE, UI.textSub))
            setPadding(d(8), d(8), d(8), d(8))
            layoutParams = LinearLayout.LayoutParams(d(36), d(36))
            setOnClickListener { QueueManager.cancelJob(job) }
        }
        head.addView(x)
        card.addView(head)
        Thumbs.load(ctx, job.item) { bmp -> if (bmp != null) thumb.setImageBitmap(bmp) }

        val info = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
            setPadding(0, d(10), 0, 0)
        }
        card.addView(info)

        val barRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bar = barOf()
        barRow.addView(bar, lp(0, d(6), 1f))
        val pct = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
            gravity = Gravity.END
            minWidth = d(54)
        }
        barRow.addView(pct)
        card.addView(barRow, lp(MATCH, WRAP).apply { topMargin = d(8) })

        val status = TextView(ctx).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, d(6), 0, 0)
        }
        card.addView(status)

        val result = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, d(8), 0, 0)
        }
        val resultText = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
        }
        result.addView(resultText)

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, d(6), 0, 0)
        }
        fun act(path: String, onClick: () -> Unit): ImageView = ImageView(ctx).apply {
            setImageDrawable(PathIcon(path, UI.blue))
            setPadding(d(9), d(9), d(9), d(9))
            layoutParams = LinearLayout.LayoutParams(d(44), d(44)).apply { rightMargin = d(8) }
            background = UI.cardBg(ctx, UI.bg, 12)
            setOnClickListener { onClick() }
        }
        actions.addView(act(Ic.PLAY) {
            val u = job.outUri
            if (u != null) {
                try {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(u, "video/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                } catch (_: Throwable) {
                    Toast.makeText(ctx, "প্লে করার অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
        })
        actions.addView(act(Ic.SHARE) {
            val u = job.outUri
            if (u != null) {
                try {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("video/*")
                        .putExtra(Intent.EXTRA_STREAM, u)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share"))
                } catch (_: Throwable) {
                }
            }
        })
        actions.addView(act(Ic.TRASH) {
            val u = job.outUri
            if (u != null) {
                try { ctx.contentResolver.delete(u, null, null) } catch (_: Throwable) {}
                job.outDeleted = true
                job.outUri = null
                refresh()
            }
        })
        result.addView(actions)
        card.addView(result)

        return Card(job, card, x, info, bar, pct, status, result, resultText, actions)
    }

    private fun update(c: Card) {
        val j = c.job
        val s = j.state
        c.info.text = if (j.info.isNotEmpty()) j.info else {
            j.spec.format + "  •  " + j.spec.resolution.replace("CUSTOM:", "").replace('x', '×') +
                (if (j.spec.compress) "  •  Compress" else "")
        }

        val barColor = when (s) {
            QueueManager.DONE -> green
            QueueManager.FAILED -> red
            else -> UI.blue
        }
        c.bar.progressTintList = ColorStateList.valueOf(barColor)
        c.bar.progress = when (s) {
            QueueManager.DONE, QueueManager.FAILED -> 1000
            QueueManager.RUNNING -> (j.progress * 1000).toInt()
            else -> 0
        }
        c.pct.text = when (s) {
            QueueManager.RUNNING -> String.format(Locale.US, "%.1f%%", j.progress * 100)
            QueueManager.DONE -> "100%"
            else -> ""
        }

        val statusText: String
        val statusColor: Int
        when (s) {
            QueueManager.RUNNING -> {
                statusText = "Status: Running"
                statusColor = UI.blue
            }
            QueueManager.DONE -> {
                statusText = "Status: Successful"
                statusColor = green
            }
            QueueManager.FAILED -> {
                statusText = "Status: Failed  !"
                statusColor = red
            }
            QueueManager.CANCELLED -> {
                statusText = "Status: Cancelled"
                statusColor = UI.textSub
            }
            else -> {
                statusText = "Status: Waiting"
                statusColor = UI.textSub
            }
        }
        c.status.text = statusText
        c.status.setTextColor(statusColor)

        c.x.visibility =
            if (s == QueueManager.WAITING || s == QueueManager.RUNNING) View.VISIBLE else View.GONE

        if (s == QueueManager.DONE) {
            c.result.visibility = View.VISIBLE
            if (j.outDeleted) {
                c.resultText.text = "ফাইলটি মুছে ফেলা হয়েছে"
                c.actions.visibility = View.GONE
            } else {
                var t = "Original " + fmtSize(j.item.size) + "  →  Output " + fmtSize(j.outSize)
                if (j.outRes.isNotEmpty()) t += "  •  " + j.outRes
                t += "\nMovies/VideoStudio/" + j.outName
                if (j.note.isNotEmpty()) t += "\n" + j.note
                c.resultText.text = t
                c.resultText.setTextColor(UI.textSub)
                c.actions.visibility = View.VISIBLE
            }
        } else if (s == QueueManager.FAILED) {
            c.result.visibility = View.VISIBLE
            c.resultText.text = j.error.ifEmpty { "কনভার্ট ব্যর্থ হয়েছে" }
            c.resultText.setTextColor(red)
            c.actions.visibility = View.GONE
        } else {
            c.result.visibility = View.GONE
        }
    }

    private fun refresh() {
        val jobs = QueueManager.jobs.toList()

        for (j in jobs) {
            if (!cards.containsKey(j.id)) {
                val c = makeCard(j)
                cards[j.id] = c
                list.addView(c.root, lp(MATCH, WRAP).apply { bottomMargin = d(10) })
            }
        }
        val ids = jobs.map { it.id }.toSet()
        val stale = cards.keys.filter { !ids.contains(it) }
        for (id in stale) {
            val c = cards[id]
            if (c != null) list.removeView(c.root)
            cards.remove(id)
        }
        for (c in cards.values) update(c)

        val total = jobs.size
        val done = jobs.count { it.state == QueueManager.DONE }
        val failed = jobs.count { it.state == QueueManager.FAILED }
        val finished = jobs.count {
            it.state != QueueManager.WAITING && it.state != QueueManager.RUNNING
        }
        val active = finished < total

        title.text = if (active) {
            "RUNNING " + minOf(finished + 1, total) + "/" + total
        } else {
            "SUCCESSFUL: $done    FAILED: $failed"
        }
        headerAction.text = if (active) "CANCEL" else "OK"
        headerAction.setTextColor(if (active) red else UI.blue)

        val sum = jobs.sumOf {
            when (it.state) {
                QueueManager.WAITING -> 0.0
                QueueManager.RUNNING -> it.progress.toDouble()
                else -> 1.0
            }
        }
        val frac = if (total > 0) sum / total else 0.0
        overallBar.progress = (frac * 1000).toInt()
        overallPct.text = String.format(Locale.US, "%.1f%%", frac * 100)

        setKeepOn(active)
    }
}
