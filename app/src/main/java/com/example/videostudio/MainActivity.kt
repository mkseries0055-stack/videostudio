package com.example.videostudio

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private inner class NavItem(path: String, label: String) {
        private val icon = PathIcon(path, UI.textSub)
        private val tv = TextView(this@MainActivity).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, UI.dp(this@MainActivity, 2), 0, 0)
        }
        val root = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, UI.dp(this@MainActivity, 8), 0, UI.dp(this@MainActivity, 8))
            val iv = ImageView(this@MainActivity).apply { setImageDrawable(icon) }
            addView(
                iv,
                LinearLayout.LayoutParams(UI.dp(this@MainActivity, 24), UI.dp(this@MainActivity, 24))
            )
            addView(tv)
        }

        fun setActive(on: Boolean) {
            val c = if (on) UI.blue else UI.textSub
            icon.setColor(c)
            tv.setTextColor(c)
        }
    }

    private lateinit var content: FrameLayout
    private lateinit var homeNav: NavItem
    private lateinit var outNav: NavItem
    private lateinit var homeView: View
    private lateinit var outView: View
    private var queueBar: TextView? = null

    private var tab = 0
    private var mode = 0 // 0 = লাইট, 1 = ডার্ক, 2 = অটো

    private var picker: PickerScreen? = null
    private var settings: SettingsScreen? = null
    private var audioScreen: AudioSettingsScreen? = null
    private var progress: ProgressScreen? = null
    private var audioMode = false
    private val convertSettings = ConvertSettings()
    private var selectedVideos: List<VideoItem> = emptyList()
    private var pendingExternal: ((Uri?) -> Unit)? = null
    private var wasActive = false

    private val queueListener: () -> Unit = { runOnUiThread { updateQueueBar() } }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openPicker()
            else Toast.makeText(this, "ভিডিও দেখাতে অনুমতি দরকার", Toast.LENGTH_SHORT).show()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val browseLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!uris.isNullOrEmpty()) picker?.addExternal(uris)
        }

    private val externalLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val cb = pendingExternal
            pendingExternal = null
            cb?.invoke(uri)
        }

    private fun videoPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startPicker(audio: Boolean) {
        audioMode = audio
        if (checkSelfPermission(videoPermission()) == PackageManager.PERMISSION_GRANTED) {
            openPicker()
        } else {
            permLauncher.launch(videoPermission())
        }
    }

    private fun openPicker() {
        val p = PickerScreen(
            this,
            onBack = { closePicker() },
            onNext = { list ->
                if (audioMode) openAudioSettings(list) else openSettings(list)
            },
            onBrowse = { browseLauncher.launch(arrayOf("*/*")) }
        )
        picker = p
        setContentView(p.root)
    }

    private fun closePicker() {
        picker = null
        settings = null
        audioScreen = null
        buildUi()
    }

    private fun openSettings(list: List<VideoItem>) {
        selectedVideos = list
        convertSettings.audioMode = "DEFAULT"
        convertSettings.subtitleMode = "DEFAULT"
        val sc = SettingsScreen(
            this,
            list,
            convertSettings,
            pickExternal = { cb ->
                pendingExternal = cb
                externalLauncher.launch(arrayOf("*/*"))
            },
            onBack = { showPicker() },
            onDone = { startNow -> onConvertRequested(list, startNow) }
        )
        settings = sc
        setContentView(sc.root)
    }

    private fun openAudioSettings(list: List<VideoItem>) {
        selectedVideos = list
        val sc = AudioSettingsScreen(
            this,
            list,
            onBack = { showPicker() },
            onDone = { a, names, startNow -> onAudioConvertRequested(list, a, names, startNow) }
        )
        audioScreen = sc
        setContentView(sc.root)
    }

    private fun showPicker() {
        settings = null
        audioScreen = null
        val p = picker
        if (p != null) setContentView(p.root) else buildUi()
    }

    private fun outputNameFor(st: ConvertSettings, item: VideoItem, index: Int, single: Boolean): String {
        val orig = item.name.substringBeforeLast('.', item.name)
        return if (single) {
            if (st.outputName.isBlank()) orig else st.outputName
        } else {
            if (st.useOriginalName) orig else st.prefix + (index + 1)
        }
    }

    private fun afterQueued(startNow: Boolean, count: Int) {
        settings = null
        audioScreen = null
        picker = null
        if (startNow) {
            showProgress()
        } else {
            buildUi()
            Toast.makeText(this, "$count টি ভিডিও কিউতে যোগ হয়েছে — কনভার্ট চলছে", Toast.LENGTH_LONG).show()
        }
    }

    private fun onConvertRequested(list: List<VideoItem>, startNow: Boolean) {
        val st = convertSettings
        val single = list.size == 1
        val specs = list.mapIndexed { i, item ->
            JobSpec(
                single,
                st.format,
                st.resolution,
                st.frameRate,
                st.audioBitrate,
                st.compress,
                st.keepMetadata,
                st.audioSel.toSet(),
                st.subSel.toSet(),
                st.extAudio.toList(),
                st.extSubs.toList(),
                outputNameFor(st, item, i, single)
            )
        }
        askNotifPermission()
        QueueManager.add(this, list, specs)
        ConvertService.start(this)
        afterQueued(startNow, list.size)
    }

    private fun onAudioConvertRequested(
        list: List<VideoItem>,
        a: AudioSettings,
        names: ConvertSettings,
        startNow: Boolean
    ) {
        val single = list.size == 1
        val specs = list.mapIndexed { i, item ->
            JobSpec(
                single,
                "ORIGINAL",
                "ORIGINAL",
                "ORIGINAL",
                "AUTO",
                false,
                names.keepMetadata,
                emptySet(),
                emptySet(),
                emptyList(),
                emptyList(),
                outputNameFor(names, item, i, single)
            )
        }
        val audioSpec = AudioSpec(
            a.format,
            a.bitrate,
            a.sampleRate,
            a.channels,
            if (single) a.track else 0,
            names.keepMetadata
        )
        askNotifPermission()
        QueueManager.add(this, list, specs, audioSpec)
        ConvertService.start(this)
        afterQueued(startNow, list.size)
    }

    private fun showProgress() {
        progress?.close()
        val p = ProgressScreen(this) { closeProgress() }
        progress = p
        setContentView(p.root)
    }

    private fun closeProgress() {
        progress?.close()
        progress = null
        buildUi()
    }

    private fun updateQueueBar() {
        val active = QueueManager.hasActive()
        if (wasActive && !active && progress == null) {
            val ok = QueueManager.jobs.count { it.state == QueueManager.DONE }
            val bad = QueueManager.jobs.count { it.state == QueueManager.FAILED }
            Toast.makeText(this, "কনভার্ট শেষ — সফল $ok, ব্যর্থ $bad", Toast.LENGTH_LONG).show()
        }
        wasActive = active
        val bar = queueBar ?: return
        if (active && progress == null) {
            val total = QueueManager.jobs.size
            val finished = QueueManager.jobs.count {
                it.state != QueueManager.WAITING && it.state != QueueManager.RUNNING
            }
            bar.text = "●  Running " + minOf(finished + 1, total) + "/" + total + " — tap to view"
            bar.visibility = View.VISIBLE
        } else {
            bar.visibility = View.GONE
        }
    }

    private fun systemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun effectiveDark(): Boolean = mode == 1 || (mode == 2 && systemDark())

    private fun setMode(m: Int) {
        mode = m
        getSharedPreferences("app", MODE_PRIVATE).edit().putInt("theme", m).apply()
        buildUi()
    }

    private fun select(index: Int) {
        tab = index
        content.removeAllViews()
        content.addView(if (index == 0) homeView else outView)
        homeNav.setActive(index == 0)
        outNav.setActive(index == 1)
    }

    private fun buildUi() {
        UI.applyTheme(effectiveDark())

        window.statusBarColor = UI.bg
        window.navigationBarColor = UI.card
        window.decorView.setBackgroundColor(UI.bg)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !UI.dark
        controller.isAppearanceLightNavigationBars = !UI.dark

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UI.bg)
        }

        val qb = TextView(this).apply {
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(UI.blue)
            setPadding(0, UI.dp(this@MainActivity, 10), 0, UI.dp(this@MainActivity, 10))
            visibility = View.GONE
            setOnClickListener { showProgress() }
        }
        queueBar = qb
        root.addView(
            qb,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        homeView = buildHome(this, mode, { m -> setMode(m) }) { name ->
            when (name) {
                "Convert or Compress" -> startPicker(false)
                "Video To Audio" -> startPicker(true)
                else -> Toast.makeText(this, "$name — শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
            }
        }
        outView = buildOutput(this)

        val divider = View(this)
        divider.setBackgroundColor(UI.border)
        root.addView(
            divider,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UI.dp(this, 1))
        )

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(UI.card)
        }
        homeNav = NavItem(LineIcons.HOME, "Home")
        outNav = NavItem(LineIcons.FOLDER, "Output Files")
        bar.addView(homeNav.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(outNav.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(
            bar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        homeNav.root.setOnClickListener { select(0) }
        outNav.root.setOnClickListener { select(1) }

        setContentView(root)
        select(tab)
        updateQueueBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = getSharedPreferences("app", MODE_PRIVATE).getInt("theme", 0)
        tab = savedInstanceState?.getInt("tab", 0) ?: 0
        wasActive = QueueManager.hasActive()
        QueueManager.addListener(queueListener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (progress != null) {
                    closeProgress()
                    return
                }
                if (settings != null || audioScreen != null) {
                    showPicker()
                    return
                }
                val p = picker
                if (p != null) {
                    if (!p.handleBack()) closePicker()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        buildUi()
    }

    override fun onDestroy() {
        QueueManager.removeListener(queueListener)
        progress?.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }
}
