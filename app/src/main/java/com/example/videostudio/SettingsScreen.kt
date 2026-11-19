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
    private var probed = false
    private var srcW = 0
    private var srcH = 0
    private var updating = false

    private lateinit var vProfile: TextView
    private lateinit var vFormat: TextView
    private lateinit var vRes: TextView
    private lateinit var vSub: TextView
    private lateinit var vAudio: TextView
    private lateinit var vFps: TextView
    private lateinit var vAbr: TextView
    private lateinit var compressSwitch: Switch
    private lateinit var compressNote: TextView
    private lateinit var note: TextView
    private lateinit var advBox: LinearLayout
    private lateinit var advArrow: TextView

    val root: View = build()

    init {
        if (items.size > 1) {
            val pseudo = listOf(TrackInfo("DEFAULT", "প্রতিটি ভিডিওর প্রথম ট্র্যাক"))
            audioTracks = pseudo
            subTracks = pseudo
            probed = true
        }
        s.audioSel = setOf(0)
        s.subSel = setOf(0)
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

        val (rSub, vs) = optionRow("💬", "Subtitle tracks") { pickSubtitle() }
        vSub = vs
        addTo(col, rSub)

        val (rAudio, va) = optionRow("🔊", "Audio tracks") { pickAudio() }
        vAudio = va
        addTo(col, rAudio)

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

        compressNote = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
            setPadding(d(4), d(8), d(4), 0)
        }
        col.addView(compressNote)

        note = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
            setPadding(d(4), d(10), d(4), 0)
        }
        col.addView(note)

        scroll.addView(col)
        page.addView(scroll, lp(MATCH, 0, 1f))
        frame.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))

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

    private fun resLabel(): String = when {
        s.resolution == "ORIGINAL" -> if (srcW > 0) "${srcW}×${srcH}" else "ORIGINAL"
        s.resolution.startsWith("CUSTOM:") -> s.resolution.substring(7).replace('x', '×')
        else -> s.resolution
    }

    private fun trackLabel(tracks: List<TrackInfo>, sel: Set<Int>): String {
        if (!probed) return "..."
        if (tracks.isEmpty() || sel.isEmpty()) return "NONE"
        if (sel.size == 1) return tracks.getOrNull(sel.first())?.short ?: "DEFAULT"
        return "${sel.size} TRACKS"
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
        vRes.text = resLabel()
        vSub.text = trackLabel(subTracks, s.subSel)
        vAudio.text = trackLabel(audioTracks, s.audioSel)
        vFps.text = s.frameRate
        vAbr.text = s.audioBitrate
        updating = true
        compressSwitch.isChecked = s.compress
        updating = false
        compressNote.text = if (s.compress) {
            "🗜️ চালু: ভিডিওর বিটরেট কমিয়ে ফাইল ছোট করা হবে (আনুমানিক ৩০–৫০% ছোট), অডিও ৯৬K-তে নামবে। কোয়ালিটি সামান্য কমবে, সময় একটু বেশি লাগতে পারে।"
        } else {
            "বন্ধ: কোয়ালিটি মূলের কাছাকাছি থাকবে, ফাইল বড় হতে পারে।"
        }
        val n = formatNote()
        note.text = n
        note.visibility = if (n.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun probeTracks() {
        Thread {
            val dims = TrackProbe.dims(ctx, items[0].uri)
            val r = TrackProbe.probe(ctx, items[0].uri)
            main.post {
                if (dims != null) {
                    srcW = dims.first
                    srcH = dims.second
                }
                if (r != null) {
                    audioTracks = r.audio
                    subTracks = r.subs
                    s.audioSel = if (r.audio.isEmpty()) emptySet() else setOf(0)
                    s.subSel = if (r.subs.isEmpty()) emptySet() else setOf(0)
                }
                probed = true
                refresh()
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

    private fun scaledSub(heightP: Int): String {
        if (srcW <= 0 || srcH <= 0) return ""
        val w: Int
        val h: Int
        if (srcW >= srcH) {
            h = heightP
            w = Math.round(srcW.toFloat() * heightP / srcH) / 2 * 2
        } else {
            w = heightP
            h = Math.round(srcH.toFloat() * heightP / srcW) / 2 * 2
        }
        return "${w}×${h}"
    }

    private fun pickResolution() {
        if (s.format == "3GP") {
            val list = Options.keypadRes.map { Opt(it, "H.263 সাপোর্টেড সাইজ") }
            val sel = list.indexOfFirst { it.label == s.resolution }
            Sheets.choice(ctx, "RESOLUTION", list, sel) { i ->
                s.resolution = list[i].label
                s.profile = "NONE"
                refresh()
            }
            return
        }

        val opts = ArrayList<Opt>()
        val values = ArrayList<String>()
        opts.add(Opt("CUSTOM RESOLUTION", "নিজের মাপ লিখুন"))
        values.add("CUSTOM")
        opts.add(Opt(if (srcW > 0) "${srcW}×${srcH}" else "ORIGINAL", "ORIGINAL"))
        values.add("ORIGINAL")
        val shortSide = if (srcW > 0) minOf(srcW, srcH) else Int.MAX_VALUE
        for (hp in Options.presetHeights) {
            if (hp < shortSide) {
                opts.add(Opt("${hp}P", scaledSub(hp)))
                values.add("${hp}P")
            }
        }
        val sel = values.indexOf(s.resolution)
        Sheets.choice(ctx, "RESOLUTION", opts, sel) { i ->
            val v = values[i]
            if (v == "CUSTOM") {
                val iw = if (srcW > 0) srcW else 1280
                val ih = if (srcH > 0) srcH else 720
                Sheets2.customResolution(ctx, iw, ih) { w, h ->
                    s.resolution = "CUSTOM:${w}x${h}"
                    s.profile = "NONE"
                    refresh()
                }
            } else {
                s.resolution = v
                s.profile = "NONE"
                refresh()
            }
        }
    }

    private fun pickSubtitle() {
        Sheets2.tracks(
            ctx, "SUBTITLE TRACKS", subTracks, s.subSel,
            "এই ভিডিওতে কোনো সাবটাইটেল নেই"
        ) { sel ->
            s.subSel = sel
            refresh()
        }
    }

    private fun pickAudio() {
        Sheets2.tracks(
            ctx, "AUDIO TRACKS", audioTracks, s.audioSel,
            "এই ভিডিওতে কোনো অডিও নেই"
        ) { sel ->
            s.audioSel = sel
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
                "• Audio/Subtitle tracks: কোন ট্র্যাক রাখবেন, টিক দিয়ে বাছুন\n" +
                "• Compress: ফাইল ছোট করে\n" +
                "• CONVERT চেপে নাম দিয়ে শুরু করুন বা কিউতে রাখুন"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
