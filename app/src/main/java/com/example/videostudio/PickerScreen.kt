package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class PickerScreen(
    private val ctx: Context,
    private val onBack: () -> Unit,
    private val onNext: (List<VideoItem>) -> Unit,
    private val onBrowse: () -> Unit
) {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val d = { v: Int -> UI.dp(ctx, v) }
    private val main = Handler(Looper.getMainLooper())
    private val cell = (ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 32) - UI.dp(ctx, 16)) / 3

    private var all: List<VideoItem> = emptyList()
    private var loading = true
    private val selected = LinkedHashSet<Long>()
    private val invalid = HashSet<Long>()
    private var tab = 0
    private var openFolder: String? = null
    private var query = ""
    private var grid = false
    private var sortMode = 0
    private var extId = 0L
    private var shown: List<VideoItem> = emptyList()
    private var folderNames: List<String> = emptyList()

    private lateinit var titleView: TextView
    private lateinit var searchBox: EditText
    private lateinit var gridBtn: ImageView
    private lateinit var tabMedia: TextView
    private lateinit var tabFolders: TextView
    private lateinit var indMedia: View
    private lateinit var indFolders: View
    private lateinit var banner: LinearLayout
    private lateinit var videoGrid: GridView
    private lateinit var folderList: ListView
    private lateinit var msg: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var countView: TextView

    val root: View = build()

    init {
        refresh()
        Thread {
            val v = loadVideos(ctx)
            main.post {
                all = v
                loading = false
                refresh()
            }
        }.start()
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun iconBtn(path: String, onClick: () -> Unit): ImageView {
        val px = d(44)
        return ImageView(ctx).apply {
            setImageDrawable(PathIcon(path, UI.textMain))
            setPadding(d(11), d(11), d(11), d(11))
            layoutParams = LinearLayout.LayoutParams(px, px)
            setOnClickListener { onClick() }
        }
    }

    private fun build(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UI.bg)
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(4), d(8), d(8), d(8))
        }
        titleView = TextView(ctx).apply {
            text = "Select files"
            textSize = 18f
            setTextColor(UI.textMain)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(d(4), 0, 0, 0)
        }
        gridBtn = iconBtn(LineIcons.GRID) {
            grid = !grid
            refresh()
        }
        top.addView(iconBtn(LineIcons.BACK) { if (!handleBack()) onBack() })
        top.addView(titleView, lp(0, WRAP, 1f))
        top.addView(iconBtn(LineIcons.SEARCH) { toggleSearch() })
        top.addView(gridBtn)
        top.addView(iconBtn(LineIcons.SORT) { showSort() })
        col.addView(top)

        searchBox = EditText(ctx).apply {
            hint = "Search"
            setHintTextColor(UI.textSub)
            setTextColor(UI.textMain)
            textSize = 15f
            setSingleLine(true)
            background = UI.cardBg(ctx, UI.card, 12)
            setPadding(d(14), d(10), d(14), d(10))
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    refresh()
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        col.addView(searchBox, lp(MATCH, WRAP).apply { setMargins(d(16), 0, d(16), d(8)) })

        val tabs = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        tabMedia = TextView(ctx).apply {
            text = "Media"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, d(10), 0, d(10))
            setOnClickListener {
                tab = 0
                openFolder = null
                refresh()
            }
        }
        indMedia = View(ctx)
        val c1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c1.addView(tabMedia, lp(MATCH, WRAP))
        c1.addView(indMedia, lp(MATCH, d(3)))
        tabFolders = TextView(ctx).apply {
            text = "Folders"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, d(10), 0, d(10))
            setOnClickListener {
                tab = 1
                openFolder = null
                refresh()
            }
        }
        indFolders = View(ctx)
        val c2 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c2.addView(tabFolders, lp(MATCH, WRAP))
        c2.addView(indFolders, lp(MATCH, d(3)))
        tabs.addView(c1, lp(0, WRAP, 1f))
        tabs.addView(c2, lp(0, WRAP, 1f))
        col.addView(tabs)

        banner = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(if (UI.dark) Color.parseColor("#14233F") else Color.parseColor("#EAF1FF"))
                cornerRadius = d(14).toFloat()
                setStroke(d(2), UI.blue)
            }
            setPadding(d(16), d(8), d(16), d(8))
            isClickable = true
            setOnClickListener { onBrowse() }
        }
        banner.addView(
            ImageView(ctx).apply { setImageResource(R.drawable.ic_folder) },
            lp(d(40), d(40))
        )
        banner.addView(TextView(ctx).apply {
            text = "File Browser"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UI.textMain)
            gravity = Gravity.CENTER
        }, lp(0, WRAP, 1f))
        col.addView(banner, lp(MATCH, d(60)).apply { setMargins(d(16), d(8), d(16), d(4)) })

        val frame = FrameLayout(ctx)
        videoGrid = GridView(ctx).apply {
            setPadding(d(16), d(8), d(16), d(8))
            clipToPadding = false
            horizontalSpacing = d(8)
            verticalSpacing = d(8)
            setSelector(ColorDrawable(Color.TRANSPARENT))
            setOnItemClickListener { _, _, pos, _ ->
                if (pos in shown.indices) onItemTap(shown[pos])
            }
        }
        folderList = ListView(ctx).apply {
            divider = null
            dividerHeight = 0
            setPadding(d(16), d(8), d(16), d(8))
            clipToPadding = false
            setSelector(ColorDrawable(Color.TRANSPARENT))
            visibility = View.GONE
            setOnItemClickListener { _, _, pos, _ ->
                val f = folderNames.getOrNull(pos)
                if (f != null) {
                    openFolder = f
                    refresh()
                }
            }
        }
        msg = TextView(ctx).apply {
            textSize = 15f
            setTextColor(UI.textSub)
            gravity = Gravity.CENTER
        }
        frame.addView(videoGrid, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(folderList, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(msg, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        col.addView(frame, lp(MATCH, 0, 1f))

        bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UI.card)
            setPadding(d(16), d(10), d(16), d(10))
            visibility = View.GONE
        }
        countView = TextView(ctx).apply {
            textSize = 15f
            setTextColor(UI.textMain)
            typeface = Typeface.DEFAULT_BOLD
        }
        val next = TextView(ctx).apply {
            text = "NEXT  →"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = UI.gradient(ctx, UI.lightBlue, UI.blue, 22)
            setPadding(d(24), d(10), d(24), d(10))
            setOnClickListener { checkAndNext() }
        }
        bottomBar.addView(countView, lp(0, WRAP, 1f))
        bottomBar.addView(next)
        col.addView(bottomBar)

        return col
    }

    fun handleBack(): Boolean {
        if (searchBox.visibility == View.VISIBLE) {
            toggleSearch()
            return true
        }
        if (openFolder != null) {
            openFolder = null
            refresh()
            return true
        }
        return false
    }

    // NEXT চাপলে: Files থেকে বাছা ফাইল আসলেই ভিডিও কি না যাচাই
    private fun checkAndNext() {
        val byId = all.associateBy { it.id }
        val items = selected.mapNotNull { byId[it] }
        if (items.isEmpty()) return
        val toCheck = items.filter { it.id < 0 }
        if (toCheck.isEmpty()) {
            onNext(items)
            return
        }

        val wait = showWait()
        Thread {
            val bad = HashSet<Long>()
            for (item in toCheck) {
                if (!VideoCheck.isVideo(ctx, item.uri)) bad.add(item.id)
            }
            main.post {
                try { wait.dismiss() } catch (_: Throwable) {}
                if (bad.isEmpty()) {
                    onNext(items)
                } else {
                    invalid.addAll(bad)
                    selected.removeAll(bad)
                    val good = items.filter { !bad.contains(it.id) }
                    refresh()
                    if (good.isEmpty()) {
                        showInvalid(items.size > 1)
                    } else {
                        onNext(good)
                    }
                }
            }
        }.start()
    }

    private fun showWait(): AlertDialog {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(24), d(20), d(24), d(20))
        }
        row.addView(ProgressBar(ctx), lp(d(36), d(36)))
        row.addView(TextView(ctx).apply {
            text = "Please wait...\nExtracting file info"
            textSize = 15f
            setPadding(d(16), 0, 0, 0)
        })
        val dlg = AlertDialog.Builder(ctx).setView(row).setCancelable(false).create()
        dlg.show()
        return dlg
    }

    private fun showInvalid(multi: Boolean) {
        AlertDialog.Builder(ctx)
            .setTitle("Invalid file")
            .setMessage(
                if (multi) "বাছা ফাইলগুলোর কোনোটিই ভিডিও ফাইল নয়।"
                else "এই ফাইলটি ভিডিও ফাইল নয়।"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    // Files অ্যাপ থেকে বাছা ফাইল যোগ করা
    fun addExternal(uris: List<Uri>) {
        if (uris.isEmpty()) return
        Thread {
            val existing = all.map { it.uri }.toSet()
            val created = ArrayList<VideoItem>()
            for (u in uris) {
                if (u in existing) continue
                var name = "file"
                var size = 0L
                try {
                    ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                        val iN = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val iS = c.getColumnIndex(OpenableColumns.SIZE)
                        if (c.moveToFirst()) {
                            if (iN >= 0) name = c.getString(iN) ?: name
                            if (iS >= 0 && !c.isNull(iS)) size = c.getLong(iS)
                        }
                    }
                } catch (_: Throwable) {
                }
                var dur = 0L
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(ctx, u)
                    dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    r.release()
                } catch (_: Throwable) {
                }
                extId -= 1
                created.add(
                    VideoItem(extId, u, name, dur, size, "Browsed files", System.currentTimeMillis() / 1000)
                )
            }
            main.post {
                if (created.isNotEmpty()) {
                    all = created + all
                    created.forEach { selected.add(it.id) }
                }
                tab = 0
                openFolder = null
                refresh()
            }
        }.start()
    }

    private fun toggleSearch() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val show = searchBox.visibility != View.VISIBLE
        if (show) {
            searchBox.visibility = View.VISIBLE
            searchBox.requestFocus()
            imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
        } else {
            searchBox.setText("")
            searchBox.visibility = View.GONE
            imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        }
    }

    private fun showSort() {
        val opts = arrayOf(
            "Newest first", "Oldest first", "Name A → Z",
            "Name Z → A", "Largest size", "Longest duration"
        )
        AlertDialog.Builder(ctx)
            .setTitle("Sort by")
            .setItems(opts) { _, which ->
                sortMode = which
                refresh()
            }
            .show()
    }

    // "!" চিহ্নিত ফাইলে চাপলে সেটি তালিকা থেকে সরে যায়
    private fun onItemTap(item: VideoItem) {
        if (invalid.contains(item.id)) {
            invalid.remove(item.id)
            selected.remove(item.id)
            all = all.filter { it.id != item.id }
            refresh()
        } else {
            toggle(item)
        }
    }

    private fun toggle(item: VideoItem) {
        if (!selected.remove(item.id)) selected.add(item.id)
        (videoGrid.adapter as? BaseAdapter)?.notifyDataSetChanged()
        updateBottom()
    }

    private fun updateBottom() {
        bottomBar.visibility = if (selected.isNotEmpty()) View.VISIBLE else View.GONE
        countView.text = "${selected.size} selected"
    }

    private fun visibleVideos(): List<VideoItem> {
        var list = all
        val f = openFolder
        if (f != null) list = list.filter { it.folder == f }
        if (query.isNotEmpty()) list = list.filter { it.name.contains(query, ignoreCase = true) }
        return when (sortMode) {
            0 -> list.sortedByDescending { it.dateAdded }
            1 -> list.sortedBy { it.dateAdded }
            2 -> list.sortedBy { it.name.lowercase() }
            3 -> list.sortedByDescending { it.name.lowercase() }
            4 -> list.sortedByDescending { it.size }
            else -> list.sortedByDescending { it.durationMs }
        }
    }

    private fun refresh() {
        tabMedia.setTextColor(if (tab == 0) UI.blue else UI.textSub)
        tabFolders.setTextColor(if (tab == 1) UI.blue else UI.textSub)
        indMedia.setBackgroundColor(if (tab == 0) UI.blue else Color.TRANSPARENT)
        indFolders.setBackgroundColor(if (tab == 1) UI.blue else Color.TRANSPARENT)
        titleView.text = openFolder ?: "Select files"
        gridBtn.setImageDrawable(PathIcon(if (grid) LineIcons.LIST else LineIcons.GRID, UI.textMain))
        banner.visibility =
            if (tab == 0 && openFolder == null && query.isEmpty()) View.VISIBLE else View.GONE

        if (loading) {
            msg.text = "লোড হচ্ছে..."
            msg.visibility = View.VISIBLE
            videoGrid.visibility = View.GONE
            folderList.visibility = View.GONE
            updateBottom()
            return
        }

        if (tab == 1 && openFolder == null) showFolders() else showVideos()
        updateBottom()
    }

    private fun showVideos() {
        folderList.visibility = View.GONE
        shown = visibleVideos()
        if (shown.isEmpty()) {
            msg.text = if (all.isEmpty()) "কোনো ভিডিও পাওয়া যায়নি" else "কিছু পাওয়া যায়নি"
            msg.visibility = View.VISIBLE
            videoGrid.visibility = View.GONE
            return
        }
        msg.visibility = View.GONE
        videoGrid.visibility = View.VISIBLE
        videoGrid.numColumns = if (grid) 3 else 1
        if (grid) videoGrid.columnWidth = cell
        videoGrid.adapter = VideoAdapter(
            ctx, shown, grid, cell,
            { id -> selected.contains(id) },
            { id -> invalid.contains(id) }
        )
    }

    private fun showFolders() {
        videoGrid.visibility = View.GONE
        val groups = all.filter { !invalid.contains(it.id) }
            .groupBy { it.folder }
            .filter { query.isEmpty() || it.key.contains(query, ignoreCase = true) }
            .map { Pair(it.key, it.value.size) }
            .sortedBy { it.first.lowercase() }
        folderNames = groups.map { it.first }
        if (groups.isEmpty()) {
            msg.text = if (all.isEmpty()) "কোনো ভিডিও পাওয়া যায়নি" else "কিছু পাওয়া যায়নি"
            msg.visibility = View.VISIBLE
            folderList.visibility = View.GONE
            return
        }
        msg.visibility = View.GONE
        folderList.visibility = View.VISIBLE
        folderList.adapter = FolderAdapter(ctx, groups)
    }
}
