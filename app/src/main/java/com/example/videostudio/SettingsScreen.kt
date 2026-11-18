package com.example.videostudio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class SettingsScreen(
    private val ctx: Context,
    private val items: List<VideoItem>,
    private val s: ConvertSettings,
    private val onBack: () -> Unit,
    private val onDone: (Boolean) -> Unit
) {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val d = { v: Int -> UI.dp(ctx, v) }
    private val main = Handler(Looper.getMainLooper())
    private val teal = Color.parseColor("#14B8A6")

    private var audioTracks: List<TrackInfo> = emptyList()
    private var subTracks: List<TrackInfo> = emptyList()
    private var updating = false

    private lateinit var vProfile: TextView
    private lateinit var vFormat: TextView
    private lateinit var vRes: TextView
    private lateinit var vSub: TextView
    private lateinit var vAudio: TextView
    private lateinit var vFps: TextView
    private lateinit var vAbr: TextView
    private lateinit var compressSwitch: Switch
    private lateinit var note: TextView
    private lateinit var advBox: LinearLayout
    private lateinit var advArrow: TextView

    val root: View = build()

    init {
        refresh()
        if (items.size == 1) probeTracks()
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun addTo(parent: LinearLayout, v: View) {
        parent.addView(v, lp(MATCH, WRAP).apply { topMargin = d(10) })
    }

    private fun iconText(t: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = t
        textSize = 20f
        setTextColor(UI.textMain)
        gravity = Gravity.CENTER
        setPadding(d(12), d(6), d(12), d(6))
        setOnClickListener { onClick() }
    }

    private fun optionRow(emoji: String, label: String, onClick: () -> Unit): Pair<View, TextView> {
        val value = TextView(ctx).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.blue)
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(16), d(16), d(14), d(16))
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(ctx).apply {
            text = emoji
            textSize = 20f
        })
        row.addView(TextView(ctx).apply {
            text = label
            textSize = 15f
            setTextColor(UI.textMain)
            setPadding(d(12), 0, d(10), 0)
        })
        row.addView(value, lp(0, WRAP, 1f))
        row.addView(TextView(ctx).apply {
            text = "›"
            textSize = 20f
            setTextColor(UI.textSub)
            setPadding(d(8), 0, 0, 0)
        })
        return Pair(row, value)
    }

    private fun build(): View {
        val frame = FrameLayout(ctx).apply { setBackgroundColor(UI.bg) }
        val page = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(4), d(8), d(8), d(8))
        }
        top.addView(iconText("←") { onBack() })
        top.addView(TextView(ctx).apply {
            text = "Video Converter"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
        }, lp(0, WRAP, 1f))
        top.addView(iconText("?") { showHelp() })
        page.addView(top)

        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(4), d(16), d(130))
        }

        // ফাইলের তথ্য
        val total = items.sumOf { it.size }
        val infoText = if (items.size == 1) {
            "🎬  " + items[0].name + "\n" + fmtSize(items[0].size)
        } else {
            "🎬  ${items.size} টি ভিডিও বাছা হয়েছে\nমোট " + fmtSize(total)
        }
        val info = TextView(ctx).apply {
            text = infoText
            textSize = 13f
            setTextColor(UI.textSub)
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(16), d(12), d(16), d(12))
        }
        addTo(col, info)

        val (rProfile, vp) = optionRow("🎯", "Conversion Profile") { pickProfile() }
        vProfile = vp
        addTo(col, rProfile)

        val (rFormat, vf) = optionRow("🎞️", "Format") { pickFormat() }
        vFormat = vf
        addTo(col, rFormat)

        val (rRes, vr) = optionRow("📐", "Resolution") { pickResolution() }
        vRes = vr
        addTo(col, rRes)

        val (rSub, vs) = optionRow("💬", "Subtitle tracks") {
            pickTrack("SUBTITLE TRACKS", subTracks, s.subtitleMode) { s.subtitleMode = it }
        }
        vSub = vs
        addTo(col, rSub)

        val (rAudio, va) = optionRow("🔊", "Audio tracks") {
            pickTrack("AUDIO TRACKS", audioTracks, s.audioMode) { s.audioMode = it }
        }
        vAudio = va
        addTo(col, rAudio)

        // Advanced
        val adv = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(16), d(16), d(14), d(16))
            isClickable = true
        }
        adv.addView(TextView(ctx).apply {
            text = "🛠️"
            textSize = 20f
        })
        adv.addView(TextView(ctx).apply {
            text = "Advanced"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(teal)
            setPadding(d(12), 0, 0, 0)
        }, lp(0, WRAP, 1f))
        advArrow = TextView(ctx).apply {
            text = "⌄"
            textSize = 20f
            setTextColor(teal)
        }
        adv.addView(advArrow)
        addTo(col, adv)

        advBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val (rFps, vfp) = optionRow("🎬", "Frame rate") { pickFrameRate() }
        vFps = vfp
        addTo(advBox, rFps)
        val (rAbr, vab) = optionRow("🎚️", "Audio bitrate") { pickAudioBitrate() }
        vAbr = vab
        addTo(advBox, rAbr)
        val hw = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(16), d(16), d(14), d(16))
            alpha = 0.5f
        }
        hw.addView(TextView(ctx).apply {
            text = "⚡"
            textSize = 20f
        })
        hw.addView(TextView(ctx).apply {
            text = "Hardware Acceleration"
            textSize = 15f
            setTextColor(UI.textMain)
            setPadding(d(12), 0, 0, 0)
        }, lp(0, WRAP, 1f))
        hw.addView(TextView(ctx).apply {
            text = "শীঘ্রই আসছে"
            textSize = 12f
            setTextColor(UI.textSub)
        })
        addTo(advBox, hw)
        col.addView(advBox)

        adv.setOnClickListener {
            val show = advBox.visibility != View.VISIBLE
            advBox.visibility = if (show) View.VISIBLE else View.GONE
            advArrow.text = if (show) "⌃" else "⌄"
        }

        // Compress
        val comp = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(16), d(12), d(14), d(12))
        }
        comp.addView(TextView(ctx).apply {
            text = "🗜️"
            textSize = 20f
        })
        comp.addView(TextView(ctx).apply {
            text = "Compress"
            textSize = 15f
            setTextColor(UI.textMain)
            setPadding(d(12), 0, 0, 0)
        }, lp(0, WRAP, 1f))
        compressSwitch = Switch(ctx).apply {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = ColorStateList(states, intArrayOf(UI.blue, Color.parseColor("#9CA3AF")))
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
            setOnCheckedChangeListener { _, c ->
                if (!updating) {
                    s.compress = c
                    s.profile = "NONE"
                    refresh()
                }
            }
        }
        comp.addView(compressSwitch)
        addTo(col, comp)

        note = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
            setPadding(d(4), d(12), d(4), 0)
        }
        col.addView(note)

        scroll.addView(col)
        page.addView(scroll, lp(MATCH, 0, 1f))
        frame.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))

        // গোল CONVERT বাটন
        val fab = TextView(ctx).apply {
            text = "CONVERT"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(UI.lightBlue, UI.blue)
            ).apply { shape = GradientDrawable.OVAL }
            elevation = d(8).toFloat()
            isClickable = true
            setOnClickListener {
                val base = items[0].name.substringBeforeLast('.', items[0].name)
                Sheets.nameDialog(ctx, items.size > 1, s, base) { startNow -> onDone(startNow) }
            }
        }
        val fabLp = FrameLayout.LayoutParams(d(84), d(84), Gravity.BOTTOM or Gravity.END)
        fabLp.setMargins(0, 0, d(20), d(24))
        frame.addView(fab, fabLp)

        return frame
    }

    private fun modeLabel(mode: String, tracks: List<TrackInfo>, kind: String): String {
        if (mode.startsWith("TRACK:")) {
            val i = mode.substring(6).toIntOrNull() ?: 0
            return tracks.getOrNull(i)?.short ?: (kind + " " + (i + 1))
        }
        return mode
    }

    private fun formatNote(): String = when (s.format) {
        "3GP" -> "ℹ️ 3GP: H.263 কোডেক শুধু 176×144, 352×288 ও 128×96 সাইজ সাপোর্ট করে।"
        "MP4", "MKV", "MOV", "M4V", "AVI" ->
            "ℹ️ এই ফরম্যাটে MPEG-4 কোডেক ব্যবহার হবে (H.264 এনকোডার নেই), ফাইল একটু বড় হতে পারে।"
        "WEBM" -> "ℹ️ WEBM: VP9 এনকোডিং ধীর হতে পারে।"
        else -> ""
    }

    private fun refresh() {
        vProfile.text = s.profile.uppercase()
        vFormat.text = s.format
        vRes.text = s.resolution
        vSub.text = modeLabel(s.subtitleMode, subTracks, "SUBTITLE")
        vAudio.text = modeLabel(s.audioMode, audioTracks, "AUDIO")
        vFps.text = s.frameRate
        vAbr.text = s.audioBitrate
        updating = true
        compressSwitch.isChecked = s.compress
        updating = false
        val n = formatNote()
        note.text = n
        note.visibility = if (n.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun probeTracks() {
        Thread {
            val r = TrackProbe.probe(ctx, items[0].uri)
            if (r != null) {
                main.post {
                    audioTracks = r.audio
                    subTracks = r.subs
                    refresh()
                }
            }
        }.start()
    }

    private fun pickProfile() {
        val opts = Options.profiles
        val sel = opts.indexOfFirst { it.label == s.profile }.coerceAtLeast(0)
        Sheets.choice(ctx, "CONVERSION PROFILE", opts, sel) { i ->
            Options.applyProfile(s, opts[i].label)
            refresh()
        }
    }

    private fun pickFormat() {
        val opts = Options.formats
        val sel = opts.indexOfFirst { it.label == s.format }.coerceAtLeast(0)
        Sheets.choice(ctx, "FORMAT", opts, sel) { i ->
            Options.setFormat(s, opts[i].label)
            refresh()
        }
    }

    private fun pickResolution() {
        val sub = if (s.format == "3GP") "H.263 সাপোর্টেড সাইজ" else ""
        val list = Options.resolutionsFor(s.format).map { Opt(it, sub) }
        val sel = list.indexOfFirst { it.label == s.resolution }.coerceAtLeast(0)
        Sheets.choice(ctx, "RESOLUTION", list, sel) { i ->
            s.resolution = list[i].label
            s.profile = "NONE"
            refresh()
        }
    }

    private fun pickTrack(
        title: String,
        tracks: List<TrackInfo>,
        current: String,
        onSet: (String) -> Unit
    ) {
        val opts = ArrayList<Opt>(Options.basicModes)
        val values = ArrayList<String>(listOf("DEFAULT", "ALL", "NONE"))
        tracks.forEachIndexed { i, t ->
            opts.add(Opt(t.short, t.detail))
            values.add("TRACK:$i")
        }
        val sel = values.indexOf(current).coerceAtLeast(0)
        Sheets.choice(ctx, title, opts, sel) { i ->
            onSet(values[i])
            refresh()
        }
    }

    private fun pickFrameRate() {
        val list = Options.frameRates.map { Opt(it) }
        val sel = list.indexOfFirst { it.label == s.frameRate }.coerceAtLeast(0)
        Sheets.choice(ctx, "FRAME RATE", list, sel) { i ->
            s.frameRate = list[i].label
            refresh()
        }
    }

    private fun pickAudioBitrate() {
        val list = Options.audioBitrates.map { Opt(it) }
        val sel = list.indexOfFirst { it.label == s.audioBitrate }.coerceAtLeast(0)
        Sheets.choice(ctx, "AUDIO BITRATE", list, sel) { i ->
            s.audioBitrate = list[i].label
            refresh()
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(ctx)
            .setTitle("Video Converter")
            .setMessage(
                "• Profile: তৈরি সেটিং এক চাপে বসায়\n" +
                "• Format ও Resolution: আউটপুটের ধরন ও সাইজ\n" +
                "• Audio/Subtitle tracks: কোন ট্র্যাক রাখবেন\n" +
                "• Compress: ফাইল ছোট করে\n" +
                "• CONVERT চেপে নাম দিয়ে শুরু করুন বা কিউতে রাখুন"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
