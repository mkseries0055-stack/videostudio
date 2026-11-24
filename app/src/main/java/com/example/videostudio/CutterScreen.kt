package com.example.videostudio

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import java.util.Locale

class CutterScreen(
    private val ctx: Context,
    private val item: VideoItem,
    private val onBack: () -> Unit,
    private val onSave: (JobSpec, VideoItem, Boolean) -> Unit
) {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val d = { v: Int -> UI.dp(ctx, v) }
    private val main = Handler(Looper.getMainLooper())
    private val PENCIL = "M4,20h4L19,9l-4,-4L4,16zM13,7l4,4"
    private val SAVE = "M5,4h12l3,3v13H5zM8,4v5h8V4M8,20v-6h8v6"

    private var duration = item.durationMs
    private var startMs = 0L
    private var endMs = 0L
    private var posMs = 0L
    private var playing = false
    private var prepared = false
    private var errorShown = false
    private var updating = false
    private var srcW = 0
    private var srcH = 0
    private var format = "ORIGINAL"
    private var resolution = "ORIGINAL"
    private var removeSel = false
    private var fastCut = false
    private val names = ConvertSettings()
    private val srcFmt = Containers.fromName(item.name)

    private lateinit var video: VideoView
    private lateinit var seek: SeekBar
    private lateinit var tvPos: TextView
    private lateinit var tvTotal: TextView
    private lateinit var btnPlay: TextView
    private lateinit var chipStart: TextView
    private lateinit var chipEnd: TextView
    private lateinit var tvOut: TextView
    private lateinit var timeline: CutTimeline
    private lateinit var boxFormat: LinearLayout
    private lateinit var boxRes: LinearLayout
    private lateinit var vFormat: TextView
    private lateinit var capFormat: TextView
    private lateinit var vRes: TextView
    private lateinit var capRes: TextView
    private lateinit var swRemove: Switch
    private lateinit var swFast: Switch
    private lateinit var hint: TextView

    val root: View = build()

    private val ticker = object : Runnable {
        override fun run() {
            if (playing) {
                posMs = video.currentPosition.toLong()
                if (posMs >= endMs) {
                    video.pause()
                    playing = false
                    posMs = endMs
                    video.seekTo(endMs.toInt())
                    btnPlay.text = "▶"
                }
                refreshAll()
                if (playing) main.postDelayed(this, 60)
            }
        }
    }

    init {
        refreshAll()
        loadMeta()
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun toast(t: String) {
        Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    }

    private fun fmtMs(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return String.format(
            Locale.US, "%02d:%02d:%02d.%03d",
            t / 3600000, (t % 3600000) / 60000, (t % 60000) / 1000, t % 1000
        )
    }

    private fun fmtSec(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d:%02d", t / 3600000, (t % 3600000) / 60000, (t % 60000) / 1000)
    }

    private fun iconBtn(path: String, onClick: () -> Unit): ImageView {
        val px = d(44)
        return ImageView(ctx).apply {
            setImageDrawable(PathIcon(path, UI.textMain))
            setPadding(d(11), d(11), d(11), d(11))
            layoutParams = LinearLayout.LayoutParams(px, px)
            setOnClickListener { onClick() }
        }
    }

    private fun ctrlBtn(label: String, onClick: () -> Unit): TextView = TextView(ctx).apply {
        text = label
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(UI.blue)
        gravity = Gravity.CENTER
        background = UI.cardBg(ctx, UI.bg, 14)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun timeChip(onClick: () -> Unit): TextView = TextView(ctx).apply {
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(UI.textMain)
        gravity = Gravity.CENTER
        background = UI.cardBg(ctx, UI.bg, 12)
        setPadding(d(8), d(10), d(8), d(10))
        isClickable = true
        setOnClickListener { onClick() }
        val ic = PathIcon(PENCIL, UI.blue)
        ic.setBounds(0, 0, d(14), d(14))
        setCompoundDrawables(null, null, ic, null)
        compoundDrawablePadding = d(6)
    }

    private fun dropBox(onClick: () -> Unit): Triple<LinearLayout, TextView, TextView> {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(16), d(10), d(12), d(10))
            isClickable = true
            setOnClickListener { onClick() }
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val value = TextView(ctx).apply {
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
        }
        val cap = TextView(ctx).apply {
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.blue)
        }
        texts.addView(value)
        texts.addView(cap)
        box.addView(texts, lp(0, WRAP, 1f))
        val arrows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        arrows.addView(lineIcon(ctx, LineIcons.UP, UI.blue, 16))
        arrows.addView(lineIcon(ctx, LineIcons.DOWN, UI.blue, 16))
        box.addView(arrows)
        return Triple(box, value, cap)
    }

    private fun switchOf(onChange: (Boolean) -> Unit): Switch = Switch(ctx).apply {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        trackTintList = ColorStateList(states, intArrayOf(UI.blue, Color.parseColor("#9CA3AF")))
        thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
        setOnCheckedChangeListener { _, c -> if (!updating) onChange(c) }
    }

    private fun build(): View {
        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UI.bg)
        }
        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val r = d(24).toFloat()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(UI.card)
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
            setPadding(d(14), d(6), d(14), d(14))
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(iconBtn(LineIcons.BACK) {
            release()
            onBack()
        })
        top.addView(TextView(ctx).apply {
            text = item.name
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(d(4), 0, d(4), 0)
        }, lp(0, WRAP, 1f))
        top.addView(iconBtn(LineIcons.HELP) { showHelp() })
        card.addView(top)

        val preview = FrameLayout(ctx).apply {
            background = UI.round(ctx, Color.BLACK, 12)
            clipToOutline = true
        }
        video = VideoView(ctx)
        preview.addView(video, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        card.addView(preview, lp(MATCH, d(210)).apply { topMargin = d(6) })

        val timesRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        tvPos = TextView(ctx).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
        }
        tvTotal = TextView(ctx).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            gravity = Gravity.END
        }
        timesRow.addView(tvPos, lp(0, WRAP, 1f))
        timesRow.addView(tvTotal, lp(0, WRAP, 1f))
        card.addView(timesRow, lp(MATCH, WRAP).apply { topMargin = d(10) })

        seek = SeekBar(ctx).apply {
            progressTintList = ColorStateList.valueOf(UI.blue)
            thumbTintList = ColorStateList.valueOf(UI.blue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) moveTo(p.toLong())
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        card.addView(seek, lp(MATCH, WRAP))

        val ctrl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnPlay = ctrlBtn("▶") { togglePlay() }
        ctrl.addView(ctrlBtn("◀◀") { moveTo(posMs - 5000) }, lp(0, d(48), 1f).apply { rightMargin = d(8) })
        ctrl.addView(btnPlay, lp(0, d(48), 1f).apply { rightMargin = d(8) })
        ctrl.addView(ctrlBtn("▶▶") { moveTo(posMs + 5000) }, lp(0, d(48), 1f))
        card.addView(ctrl, lp(MATCH, WRAP).apply { topMargin = d(8) })

        val chips = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chipStart = timeChip { editStart() }
        chipEnd = timeChip { editEnd() }
        val mid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        mid.addView(TextView(ctx).apply {
            text = "OUTPUT DURATION"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textSub)
            gravity = Gravity.CENTER
        })
        tvOut = TextView(ctx).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            gravity = Gravity.CENTER
        }
        mid.addView(tvOut)
        chips.addView(chipStart, lp(0, WRAP, 1.1f))
        chips.addView(mid, lp(0, WRAP, 1f).apply { leftMargin = d(4); rightMargin = d(4) })
        chips.addView(chipEnd, lp(0, WRAP, 1.1f))
        card.addView(chips, lp(MATCH, WRAP).apply { topMargin = d(12) })

        timeline = CutTimeline(ctx)
        timeline.onChange = { s, e, p, _ ->
            startMs = s
            endMs = e
            posMs = p
            pauseIfPlaying()
            if (prepared) video.seekTo(p.toInt())
            refreshAll()
        }
        card.addView(timeline, lp(MATCH, d(78)).apply { topMargin = d(12) })

        col.addView(card)

        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val colF = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        colF.addView(TextView(ctx).apply {
            text = "CONVERT TO"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, d(8))
        })
        val f = dropBox { pickFormat() }
        boxFormat = f.first
        vFormat = f.second
        capFormat = f.third
        colF.addView(boxFormat)
        val colR = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        colR.addView(TextView(ctx).apply {
            text = "RESOLUTION"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, d(8))
        })
        val rs = dropBox { pickResolution() }
        boxRes = rs.first
        vRes = rs.second
        capRes = rs.third
        colR.addView(boxRes)
        row1.addView(colF, lp(0, WRAP, 1f).apply { rightMargin = d(8) })
        row1.addView(colR, lp(0, WRAP, 1f).apply { leftMargin = d(8) })
        col.addView(row1, lp(MATCH, WRAP).apply { setMargins(d(16), d(20), d(16), 0) })

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun toggleRow(label: String, sw: Switch): LinearLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, d(8), 0, d(8))
            addView(TextView(ctx).apply {
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UI.textMain)
            }, lp(0, WRAP, 1f))
            addView(sw)
        }
        swRemove = switchOf { c ->
            removeSel = c
            if (c && fastCut) {
                fastCut = false
                toast("REMOVE SELECTED-এ FAST CUT বন্ধ থাকে")
            }
            refreshAll()
        }
        swFast = switchOf { c ->
            fastCut = c
            if (c && removeSel) {
                removeSel = false
                toast("FAST CUT-এ REMOVE SELECTED বন্ধ থাকে")
            }
            refreshAll()
        }
        left.addView(toggleRow("REMOVE\nSELECTED", swRemove))
        left.addView(toggleRow("FAST CUT", swFast))

        val saveCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { save() }
        }
        val saveBtn = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = UI.cardBg(ctx, UI.card, 24)
        }
        saveBtn.addView(lineIcon(ctx, SAVE, UI.blue, 26))
        saveCol.addView(saveBtn, LinearLayout.LayoutParams(d(110), d(54)))
        saveCol.addView(TextView(ctx).apply {
            text = "SAVE VIDEO"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            setPadding(0, d(8), 0, 0)
        })
        row2.addView(left, lp(0, WRAP, 1f).apply { rightMargin = d(16) })
        row2.addView(saveCol, lp(0, WRAP, 1f))
        col.addView(row2, lp(MATCH, WRAP).apply { setMargins(d(24), d(18), d(24), 0) })

        hint = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
            setPadding(d(24), d(16), d(24), d(30))
        }
        col.addView(hint)

        scroll.addView(col)
        page.addView(scroll, lp(MATCH, MATCH))

        video.setOnPreparedListener {
            prepared = true
            if (duration <= 0L) {
                duration = video.duration.toLong()
                if (endMs <= 0L) endMs = duration
            }
            video.seekTo(1)
            refreshAll()
        }
        video.setOnCompletionListener {
            playing = false
            btnPlay.text = "▶"
        }
        video.setOnErrorListener { _, _, _ ->
            if (!errorShown) {
                errorShown = true
                toast("এই ভিডিওর প্রিভিউ চালানো যাচ্ছে না, তবে কাটা যাবে")
            }
            true
        }
        video.setVideoURI(item.uri)

        return page
    }

    private fun loadMeta() {
        Thread {
            var dur = duration
            val thumbs = ArrayList<android.graphics.Bitmap>()
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, item.uri)
                if (dur <= 0L) {
                    dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                }
                if (dur > 0L) {
                    val n = 10
                    for (i in 0 until n) {
                        var t = dur * 1000L * i / n + 1000L
                        val maxT = (dur * 1000L - 1000L).coerceAtLeast(0L)
                        if (t > maxT) t = maxT
                        val b = try {
                            r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 200, 112)
                        } catch (_: Throwable) {
                            null
                        }
                        if (b != null) thumbs.add(b)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
            val dims = TrackProbe.dims(ctx, item.uri)
            main.post {
                if (dur > 0L) {
                    duration = dur
                    if (endMs <= 0L || endMs > dur) endMs = dur
                }
                if (dims != null) {
                    srcW = dims.first
                    srcH = dims.second
                }
                timeline.setThumbs(thumbs)
                refreshAll()
            }
        }.start()
    }

    private fun refreshAll() {
        val dur = duration
        seek.max = maxOf(1, dur.toInt())
        seek.progress = posMs.toInt().coerceIn(0, seek.max)
        tvPos.text = fmtSec(posMs)
        tvTotal.text = fmtSec(dur)
        chipStart.text = fmtMs(startMs)
        chipEnd.text = fmtMs(endMs)
        val sel = (endMs - startMs).coerceAtLeast(0L)
        val out = if (removeSel) (dur - sel).coerceAtLeast(0L) else sel
        tvOut.text = fmtMs(out)

        timeline.durationMs = maxOf(1L, dur)
        timeline.startMs = startMs
        timeline.endMs = endMs
        timeline.posMs = posMs
        timeline.invalidate()

        vFormat.text = if (format == "ORIGINAL") srcFmt else format
        capFormat.text = if (format == "ORIGINAL") "ORIGINAL" else ""
        val short = if (srcW > 0 && srcH > 0) minOf(srcW, srcH) else 0
        vRes.text = if (resolution == "ORIGINAL") {
            if (short > 0) "${short}P" else "ORIGINAL"
        } else {
            resolution
        }
        capRes.text = if (resolution == "ORIGINAL") "ORIGINAL" else ""
        boxFormat.alpha = if (fastCut) 0.4f else 1f
        boxRes.alpha = if (fastCut) 0.4f else 1f

        updating = true
        swRemove.isChecked = removeSel
        swFast.isChecked = fastCut
        updating = false

        hint.text = when {
            fastCut -> "ℹ️ FAST CUT: ভিডিও আবার এনকোড হয় না, কোয়ালিটি মূলের মতো থাকে। কাটা কীফ্রেম ঘেঁষে হয়, তাই কয়েক সেকেন্ডের হেরফের হতে পারে।"
            removeSel -> "ℹ️ বাছা অংশ বাদ দিয়ে বাকি দুই অংশ জোড়া লাগবে। এতে ভিডিও আবার এনকোড হয়, সময় বেশি লাগবে।"
            else -> "ℹ️ H.264 এনকোডার নেই, তাই FAST CUT বন্ধ থাকলে ভিডিও MPEG-4-এ এনকোড হয় (ফাইল বড় হতে পারে)।"
        }
    }

    private fun pauseIfPlaying() {
        if (playing) {
            video.pause()
            playing = false
            btnPlay.text = "▶"
        }
    }

    private fun moveTo(ms: Long) {
        posMs = ms.coerceIn(0L, maxOf(duration, 0L))
        pauseIfPlaying()
        if (prepared) video.seekTo(posMs.toInt())
        refreshAll()
    }

    private fun togglePlay() {
        if (!prepared) {
            toast("প্রিভিউ এখনো তৈরি হয়নি")
            return
        }
        if (playing) {
            video.pause()
            playing = false
        } else {
            if (posMs < startMs || posMs >= endMs - 50) {
                posMs = startMs
                video.seekTo(posMs.toInt())
            }
            video.start()
            playing = true
            main.post(ticker)
        }
        btnPlay.text = if (playing) "❚❚" else "▶"
    }

    fun pause() {
        pauseIfPlaying()
    }

    fun release() {
        main.removeCallbacks(ticker)
        playing = false
        try { video.stopPlayback() } catch (_: Throwable) {}
    }

    private fun editStart() {
        timeDialog("Select Start Time", startMs) { ms ->
            if (ms < 0 || ms > endMs - 100) {
                toast("শুরুর সময় শেষের সময়ের আগে হতে হবে")
                false
            } else {
                startMs = ms
                posMs = ms
                if (prepared) video.seekTo(ms.toInt())
                refreshAll()
                true
            }
        }
    }

    private fun editEnd() {
        timeDialog("Select End Time", endMs) { ms ->
            if (ms <= startMs + 100 || ms > duration) {
                toast("শেষের সময় শুরুর পরে ও ভিডিওর দৈর্ঘ্যের মধ্যে হতে হবে")
                false
            } else {
                endMs = ms
                posMs = ms
                if (prepared) video.seekTo(ms.toInt())
                refreshAll()
                true
            }
        }
    }

    private fun timeDialog(title: String, initMs: Long, onOk: (Long) -> Boolean) {
        val dlg = Dialog(ctx)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = UI.round(ctx, UI.card, 16)
            setPadding(d(20), d(18), d(20), d(14))
        }
        box.addView(TextView(ctx).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            gravity = Gravity.CENTER
        }, lp(MATCH, WRAP))

        val t = initMs.coerceAtLeast(0L)
        fun field(v: Long): EditText = EditText(ctx).apply {
            setText(v.toString())
            setTextColor(UI.textMain)
            textSize = 15f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setSelectAllOnFocus(true)
            backgroundTintList = ColorStateList.valueOf(UI.blue)
        }
        val etH = field(t / 3600000)
        val etM = field((t % 3600000) / 60000)
        val etS = field((t % 60000) / 1000)
        val etX = field(t % 1000)

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(label: String, et: EditText) {
            val c = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            c.addView(TextView(ctx).apply {
                text = label
                textSize = 11f
                setTextColor(UI.textSub)
                gravity = Gravity.CENTER
            })
            c.addView(et)
            row.addView(c, lp(0, WRAP, 1f).apply { leftMargin = d(4); rightMargin = d(4) })
        }
        add("hh", etH)
        add("mm", etM)
        add("ss", etS)
        add("msec", etX)
        box.addView(row, lp(MATCH, WRAP).apply { topMargin = d(14) })

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
                val hh = etH.text.toString().toLongOrNull() ?: 0L
                val mm = etM.text.toString().toLongOrNull() ?: 0L
                val ss = etS.text.toString().toLongOrNull() ?: 0L
                val xx = etX.text.toString().toLongOrNull() ?: 0L
                val ms = ((hh * 60 + mm) * 60 + ss) * 1000 + xx
                if (onOk(ms)) dlg.dismiss()
            }
        }
        val btns = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(cancel, lp(0, WRAP, 1f))
        btns.addView(ok, lp(0, WRAP, 1f).apply { leftMargin = d(12) })
        box.addView(btns, lp(MATCH, WRAP).apply { topMargin = d(16) })

        dlg.setContentView(box)
        dlg.window?.apply {
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.9f).toInt(), WRAP)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }

    private fun pickFormat() {
        if (fastCut) {
            toast("FAST CUT-এ ফরম্যাট বদলানো যায় না")
            return
        }
        val opts = ArrayList<Opt>()
        val values = ArrayList<String>()
        opts.add(Opt(srcFmt, Options.formats.firstOrNull { it.label == srcFmt }?.sub ?: "", "ORIGINAL"))
        values.add("ORIGINAL")
        for (o in Options.formats) {
            if (o.label == "ORIGINAL" || o.label == srcFmt) continue
            opts.add(o)
            values.add(o.label)
        }
        val sel = if (format == "ORIGINAL" || format == srcFmt) 0 else values.indexOf(format)
        Sheets3.choice(ctx, "CONVERT TO", opts, sel) { i ->
            format = values[i]
            if (format == "3GP") {
                if (!Options.keypadRes.contains(resolution)) resolution = "176×144"
            } else if (Options.keypadRes.contains(resolution)) {
                resolution = "ORIGINAL"
            }
            refreshAll()
        }
    }

    private fun scaledSub(hp: Int): String {
        if (srcW <= 0 || srcH <= 0) return ""
        val w: Int
        val h: Int
        if (srcW >= srcH) {
            h = hp
            w = Math.round(srcW.toFloat() * hp / srcH) / 2 * 2
        } else {
            w = hp
            h = Math.round(srcH.toFloat() * hp / srcW) / 2 * 2
        }
        return "${w}×${h}"
    }

    private fun pickResolution() {
        if (fastCut) {
            toast("FAST CUT-এ রেজোলিউশন বদলানো যায় না")
            return
        }
        if (format == "3GP") {
            val list = Options.keypadRes.map { Opt(it) }
            val sel = list.indexOfFirst { it.label == resolution }
            Sheets3.choice(ctx, "RESOLUTION", list, sel) { i ->
                resolution = list[i].label
                refreshAll()
            }
            return
        }
        val short = if (srcW > 0 && srcH > 0) minOf(srcW, srcH) else 0
        val opts = ArrayList<Opt>()
        val values = ArrayList<String>()
        opts.add(Opt(if (short > 0) "${short}P" else "ORIGINAL", if (short > 0) "${srcW}×${srcH}" else "", "ORIGINAL"))
        values.add("ORIGINAL")
        for (hp in Options.presetHeights) {
            if (short > 0 && hp >= short) continue
            opts.add(Opt("${hp}P", scaledSub(hp)))
            values.add("${hp}P")
        }
        val sel = values.indexOf(resolution).coerceAtLeast(0)
        Sheets3.choice(ctx, "RESOLUTION", opts, sel) { i ->
            resolution = values[i]
            refreshAll()
        }
    }

    private fun save() {
        if (duration <= 0L || endMs <= startMs) {
            toast("ভিডিওর তথ্য পাওয়া যায়নি বা কাটার সীমা ঠিক নেই")
            return
        }
        val selLen = endMs - startMs
        val outDur = if (removeSel) duration - selLen else selLen
        if (outDur < 200L) {
            toast("আউটপুট খুব ছোট হয়ে যাবে")
            return
        }
        pauseIfPlaying()
        val base = item.name.substringBeforeLast('.', item.name) + "_cut"
        Sheets.nameDialog(ctx, false, names, base) { startNow ->
            val name = if (names.outputName.isBlank()) base else names.outputName
            val spec = JobSpec(
                single = true,
                format = if (fastCut) "ORIGINAL" else format,
                resolution = if (fastCut) "ORIGINAL" else resolution,
                frameRate = "ORIGINAL",
                audioBitrate = "AUTO",
                compress = false,
                keepMetadata = names.keepMetadata,
                audioSel = setOf(0),
                subSel = emptySet(),
                extAudio = emptyList(),
                extSubs = emptyList(),
                outName = name,
                cutStartMs = startMs,
                cutEndMs = endMs,
                removeSelected = removeSel,
                fastCut = fastCut,
                totalMs = duration
            )
            onSave(spec, item.copy(durationMs = outDur), startNow)
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(ctx)
            .setTitle("Video Cutter")
            .setMessage(
                "• টাইমলাইনের নীল হ্যান্ডেল টেনে শুরু ও শেষ ঠিক করুন, হলুদ দাগ বর্তমান অবস্থান\n" +
                "• সময় চিপ চেপে নির্দিষ্ট সময় লিখতে পারেন\n" +
                "• REMOVE SELECTED: বাছা অংশ বাদ দিয়ে বাকিটা রাখে\n" +
                "• FAST CUT: দ্রুত ও কোয়ালিটি অক্ষত, কাটা কীফ্রেম ঘেঁষে হয়\n" +
                "• SAVE VIDEO চেপে নাম দিয়ে শুরু করুন"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
