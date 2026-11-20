package com.example.videostudio

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object OutIc {
    const val SHARE =
        "M15.5,5a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0M3.5,12a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0M15.5,19a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0M8.2,10.8l7.6,-4.4M8.2,13.2l7.6,4.4"
    const val TRASH = "M4,7h16M9,7V4h6v3M6,7l1,13h10l1,-13M10,11v6M14,11v6"
}

fun buildOutput(ctx: Context): View = OutputScreen(ctx).root

class OutputScreen(private val ctx: Context) {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val d = { v: Int -> UI.dp(ctx, v) }
    private val main = Handler(Looper.getMainLooper())
    private val red = Color.parseColor("#DC2626")
    private val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)

    private var files: List<VideoItem> = emptyList()
    private var lastDone = -1
    private var loaded = false

    private lateinit var countText: TextView
    private lateinit var list: LinearLayout
    private lateinit var emptyText: TextView

    private val listener: () -> Unit = {
        val n = QueueManager.jobs.count { it.state == QueueManager.DONE }
        if (n != lastDone) {
            lastDone = n
            main.post { reload() }
        }
    }

    val root: View = build()

    init {
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                lastDone = QueueManager.jobs.count { it.state == QueueManager.DONE }
                QueueManager.addListener(listener)
                reload()
            }

            override fun onViewDetachedFromWindow(v: View) {
                QueueManager.removeListener(listener)
            }
        })
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun toast(t: String) {
        Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    }

    private fun build(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UI.bg)
        }

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(18), d(20), d(10))
        }
        head.addView(TextView(ctx).apply {
            text = "Output Files"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
        })
        countText = TextView(ctx).apply {
            textSize = 13f
            setTextColor(UI.textSub)
            setPadding(0, d(4), 0, 0)
        }
        head.addView(countText)
        col.addView(head)

        val body = FrameLayout(ctx)
        list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(4), d(16), d(16))
        }
        body.addView(ScrollView(ctx).apply { addView(list) }, FrameLayout.LayoutParams(MATCH, MATCH))
        emptyText = TextView(ctx).apply {
            text = "এখনো কোনো আউটপুট ফাইল নেই\nকনভার্ট করা ভিডিও এখানে দেখা যাবে"
            textSize = 15f
            setTextColor(UI.textSub)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        body.addView(emptyText, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        col.addView(body, lp(MATCH, 0, 1f))

        return col
    }

    private fun query(): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )
        try {
            ctx.contentResolver.query(
                base,
                proj,
                MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?",
                arrayOf("Movies/VideoStudio%"),
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out.add(
                        VideoItem(
                            id,
                            ContentUris.withAppendedId(base, id),
                            c.getString(iName) ?: "video",
                            c.getLong(iDur),
                            c.getLong(iSize),
                            "Output",
                            c.getLong(iDate)
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    private fun reload() {
        Thread {
            val f = query()
            main.post {
                files = f
                loaded = true
                render()
            }
        }.start()
    }

    private fun render() {
        list.removeAllViews()
        val total = files.sumOf { it.size }
        countText.text = if (files.isEmpty()) "" else "${files.size} ফাইল  •  " + fmtSize(total)
        emptyText.visibility = if (loaded && files.isEmpty()) View.VISIBLE else View.GONE
        for (f in files) list.addView(makeRow(f), lp(MATCH, WRAP).apply { bottomMargin = d(10) })
    }

    private fun play(f: VideoItem) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(f.uri, "video/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Throwable) {
            toast("প্লে করার অ্যাপ পাওয়া যায়নি")
        }
    }

    private fun share(f: VideoItem) {
        try {
            val send = Intent(Intent.ACTION_SEND)
                .setType("video/*")
                .putExtra(Intent.EXTRA_STREAM, f.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "Share"))
        } catch (_: Throwable) {
        }
    }

    private fun confirmDelete(f: VideoItem) {
        AlertDialog.Builder(ctx)
            .setTitle("Delete file?")
            .setMessage(f.name + "\n\nমুছে গেলে আর ফেরত পাবেন না।")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteFile(f) }
            .show()
    }

    private fun deleteFile(f: VideoItem) {
        try {
            val n = ctx.contentResolver.delete(f.uri, null, null)
            if (n > 0) {
                reload()
                return
            }
        } catch (_: Throwable) {
        }
        requestDelete(f)
    }

    private fun requestDelete(f: VideoItem) {
        val act = ctx as? ComponentActivity
        if (Build.VERSION.SDK_INT < 30 || act == null) {
            toast("ফাইল মোছা যায়নি")
            return
        }
        try {
            val sender = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(f.uri)).intentSender
            var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
            launcher = act.activityResultRegistry.register(
                "outdel_" + System.nanoTime(),
                ActivityResultContracts.StartIntentSenderForResult()
            ) { res ->
                if (res.resultCode == Activity.RESULT_OK) reload()
                launcher?.unregister()
            }
            launcher!!.launch(IntentSenderRequest.Builder(sender).build())
        } catch (_: Throwable) {
            toast("ফাইল মোছা যায়নি")
        }
    }

    private fun makeRow(f: VideoItem): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 14)
            setPadding(d(10), d(10), d(6), d(10))
            isClickable = true
            setOnClickListener { play(f) }
        }

        val thumbBox = FrameLayout(ctx).apply {
            background = UI.round(ctx, Color.parseColor("#374151"), 10)
            clipToOutline = true
        }
        val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        thumbBox.addView(thumb, FrameLayout.LayoutParams(MATCH, MATCH))
        row.addView(thumbBox, lp(d(96), d(64)))
        Thumbs.load(ctx, f) { bmp -> if (bmp != null) thumb.setImageBitmap(bmp) }

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(12), 0, d(6), 0)
        }
        col.addView(TextView(ctx).apply {
            text = f.name
            textSize = 13f
            setTextColor(UI.textMain)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
        })
        col.addView(TextView(ctx).apply {
            text = fmtDuration(f.durationMs) + "  •  " + fmtSize(f.size)
            textSize = 12f
            setTextColor(UI.textSub)
            setPadding(0, d(4), 0, 0)
        })
        col.addView(TextView(ctx).apply {
            text = dateFmt.format(Date(f.dateAdded * 1000))
            textSize = 11f
            setTextColor(UI.textSub)
        })
        row.addView(col, lp(0, WRAP, 1f))

        row.addView(ImageView(ctx).apply {
            setImageDrawable(PathIcon(OutIc.SHARE, UI.blue))
            setPadding(d(10), d(10), d(10), d(10))
            layoutParams = LinearLayout.LayoutParams(d(42), d(42))
            setOnClickListener { share(f) }
        })
        row.addView(ImageView(ctx).apply {
            setImageDrawable(PathIcon(OutIc.TRASH, red))
            setPadding(d(10), d(10), d(10), d(10))
            layoutParams = LinearLayout.LayoutParams(d(42), d(42))
            setOnClickListener { confirmDelete(f) }
        })
        return row
    }
}
