package com.example.videostudio

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var homeTab: TextView
    private lateinit var outTab: TextView
    private lateinit var homeView: View
    private lateinit var outView: View

    private var tab = 0
    private var mode = 0 // 0 = লাইট, 1 = ডার্ক, 2 = অটো

    private fun systemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun effectiveDark(): Boolean = mode == 1 || (mode == 2 && systemDark())

    private fun setMode(m: Int) {
        mode = m
        getSharedPreferences("app", MODE_PRIVATE).edit().putInt("theme", m).apply()
        buildUi()
    }

    private fun navItem(emoji: String, label: String): TextView = TextView(this).apply {
        text = "$emoji\n$label"
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(0, UI.dp(this@MainActivity, 8), 0, UI.dp(this@MainActivity, 8))
    }

    private fun select(index: Int) {
        tab = index
        content.removeAllViews()
        content.addView(if (index == 0) homeView else outView)
        homeTab.setTextColor(if (index == 0) UI.blue else UI.textSub)
        outTab.setTextColor(if (index == 1) UI.blue else UI.textSub)
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
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        homeView = buildHome(this, mode, { m -> setMode(m) }) { name ->
            Toast.makeText(this, "$name — শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
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
        homeTab = navItem("🏠", "Home")
        outTab = navItem("📁", "Output Files")
        bar.addView(homeTab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(outTab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(
            bar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        homeTab.setOnClickListener { select(0) }
        outTab.setOnClickListener { select(1) }

        setContentView(root)
        select(tab)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = getSharedPreferences("app", MODE_PRIVATE).getInt("theme", 0)
        tab = savedInstanceState?.getInt("tab", 0) ?: 0
        buildUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }
}
