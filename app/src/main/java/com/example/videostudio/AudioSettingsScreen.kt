package com.example.videostudio

import android.content.Context
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class AudioSettingsScreen(
    private val ctx: Context,
    private val items: List<VideoItem>,
    private val onBack: () -> Unit,
    private val onDone: (AudioSettings, ConvertSettings, Boolean) -> Unit
) {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val d = { v: Int -> UI.dp(ctx, v) }
    private val main = Handler(Looper.getMainLooper())
    private val single = items.size == 1

    private val a = AudioSettings()
    private val names = ConvertSettings()
    private var tracks: List<TrackInfo> = emptyList()
    private var probed = !single

    private lateinit var vFormat: TextView
    private lateinit var vTrack: TextView
    private lateinit var vBitrate: TextView
    private lateinit var vSample: TextView
    private lateinit var vChannels: TextView
    private lateinit var rowBitrate: View
    private lateinit var note: TextView

    val root: View = build()

    init {
        refresh()
        if (single) probeTracks()
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun addTo(parent: LinearLayout, v: View) {
        parent.addView(v, lp(MATCH, WRAP).apply { topMargin = d(10) })
    }

    private fun toast(t: String) {
        Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
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

    private fun optionRow(iconPath: String, label: String, onClick: () -> Unit): Pair<View, TextView> {
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
            setPadding(d(16), d(16), d(12), d(16))
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(lineIcon(ctx, iconPath, UI.textSub, 22))
        row.addView(TextView(ctx).apply {
            text = label
            textSize = 15f
            setTextColor(UI.textMain)
            setPadding(d(14), 0, d(10), 0)
        })
        row.addView(value, lp(0, WRAP, 1f))
        row.addView(lineIcon(ctx, LineIcons.CHEVRON, UI.textSub, 18))
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
        top.addView(iconBtn(LineIcons.BACK) { onBack() })
        top.addView(TextView(ctx).apply {
            text = "Video To Audio"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            setPadding(d(4), 0, 0, 0)
        }, lp(0, WRAP, 1f))
        top.addView(iconBtn(LineIcons.HELP) { showHelp() })
        page.addView(top)

        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(4), d(16), d(130))
        }

        val total = items.sumOf { it.size }
        val infoText = if (single) {
            items[0].name + "\n" + fmtSize(items[0].size)
        } else {
            "${items.size} টি ভিডিও বাছা হয়েছে\nমোট " + fmtSize(total)
        }
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(16), d(12), d(16), d(12))
        }
        info.addView(lineIcon(ctx, LineIcons.FORMAT, UI.textSub, 22))
        info.addView(TextView(ctx).apply {
            text = infoText
            textSize = 13f
            setTextColor(UI.textSub)
            setPadding(d(14), 0, 0, 0)
        }, lp(0, WRAP, 1f))
        addTo(col, info)

        val (rFormat, vf) = optionRow(LineIcons.AUDIO, "Format") { pickFormat() }
        vFormat = vf
        addTo(col, rFormat)

        val (rTrack, vt) = optionRow(LineIcons.PROFILE, "Audio track") { pickTrack() }
        vTrack = vt
        addTo(col, rTrack)

        val (rBr, vb) = optionRow(LineIcons.ADVANCED, "Bitrate") { pickBitrate() }
        vBitrate = vb
        rowBitrate = rBr
        addTo(col, rBr)

        val (rSr, vs) = optionRow(LineIcons.COMPRESS, "Sample rate") { pickSample() }
        vSample = vs
        addTo(col, rSr)

        val (rCh, vc) = optionRow(LineIcons.LIST, "Channels") { pickChannels() }
        vChannels = vc
        addTo(col, rCh)

        note = TextView(ctx).apply {
            textSize = 12f
            setTextColor(UI.textSub)
            setPadding(d(4), d(12), d(4), 0)
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
                Sheets.nameDialog(ctx, items.size > 1, names, base) { startNow ->
                    onDone(a, names, startNow)
                }
            }
        }
        val fabLp = FrameLayout.LayoutParams(d(84), d(84), Gravity.BOTTOM or Gravity.END)
        fabLp.setMargins(0, 0, d(20), d(24))
        frame.addView(fab, fabLp)

        return frame
    }

    private fun refresh() {
        val lossless = AudioOptions.lossless(a.format)
        vFormat.text = a.format
        vTrack.text = when {
            !single -> "DEFAULT"
            !probed -> "..."
            tracks.isEmpty() -> "NONE"
            else -> tracks.getOrNull(a.track)?.short ?: "AUDIO 1"
        }
        vBitrate.text = if (lossless) "—" else a.bitrate
        rowBitrate.alpha = if (lossless) 0.5f else 1f
        vSample.text = a.sampleRate
        vChannels.text = a.channels
        note.text = if (lossless) {
            "ℹ️ ${a.format} লসলেস ফরম্যাট, ফাইল বড় হবে আর বিটরেট লাগে না।"
        } else {
            "ℹ️ MP3 এখনো বানানো যায় না (এই লাইব্রেরিতে এনকোডার নেই)। M4A প্রায় সব ডিভাইসে চলে।"
        }
    }

    private fun probeTracks() {
        Thread {
            val r = TrackProbe.probe(ctx, items[0].uri)
            main.post {
                if (r != null) tracks = r.audio
                probed = true
                refresh()
            }
        }.start()
    }

    private fun pickFormat() {
        val opts = AudioOptions.formats
        val sel = opts.indexOfFirst { it.label == a.format }.coerceAtLeast(0)
        Sheets3.choice(ctx, "FORMAT", opts, sel) { i ->
            a.format = opts[i].label
            refresh()
        }
    }

    private fun pickTrack() {
        if (!single) {
            toast("প্রতিটি ভিডিওর প্রথম অডিও ট্র্যাক নেওয়া হবে")
            return
        }
        if (!probed) return
        if (tracks.isEmpty()) {
            toast("এই ভিডিওতে কোনো অডিও নেই")
            return
        }
        val opts = tracks.map { Opt(it.short, it.detail) }
        Sheets3.choice(ctx, "AUDIO TRACK", opts, a.track) { i ->
            a.track = i
            refresh()
        }
    }

    private fun pickBitrate() {
        if (AudioOptions.lossless(a.format)) {
            toast("${a.format}-এ বিটরেট লাগে না")
            return
        }
        val opts = AudioOptions.bitrates.map { Opt(it) }
        val sel = opts.indexOfFirst { it.label == a.bitrate }.coerceAtLeast(0)
        Sheets3.choice(ctx, "BITRATE", opts, sel) { i ->
            a.bitrate = opts[i].label
            refresh()
        }
    }

    private fun pickSample() {
        val opts = AudioOptions.sampleRates.map { Opt(it) }
        val sel = opts.indexOfFirst { it.label == a.sampleRate }.coerceAtLeast(0)
        Sheets3.choice(ctx, "SAMPLE RATE", opts, sel) { i ->
            a.sampleRate = opts[i].label
            refresh()
        }
    }

    private fun pickChannels() {
        val opts = AudioOptions.channelOpts.map { Opt(it) }
        val sel = opts.indexOfFirst { it.label == a.channels }.coerceAtLeast(0)
        Sheets3.choice(ctx, "CHANNELS", opts, sel) { i ->
            a.channels = opts[i].label
            refresh()
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(ctx)
            .setTitle("Video To Audio")
            .setMessage(
                "• Format: কোন ধরনের অডিও ফাইল হবে\n" +
                "• Audio track: ভিডিওর কোন অডিও রাখবেন\n" +
                "• Bitrate: বেশি হলে কোয়ালিটি ও সাইজ দুটোই বাড়ে\n" +
                "• Sample rate ও Channels: না বদলালে মূল অনুযায়ী থাকবে\n" +
                "• CONVERT চেপে নাম দিয়ে শুরু করুন বা কিউতে রাখুন"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
