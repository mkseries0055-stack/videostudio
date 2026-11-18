package com.example.videostudio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private val RED = Color.parseColor("#EF4444")

class ItemHolder(
    val root: View,
    val thumb: ImageView,
    val badge: TextView,
    val name: TextView,
    val info: TextView,
    val check: TextView
)

fun createHolder(ctx: Context, grid: Boolean, cell: Int): ItemHolder {
    val d = { v: Int -> UI.dp(ctx, v) }

    val thumb = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = UI.round(ctx, Color.parseColor("#374151"), 10)
        clipToOutline = true
    }
    val frame = FrameLayout(ctx)
    frame.addView(thumb, FrameLayout.LayoutParams(MATCH, MATCH))

    val badge = TextView(ctx).apply {
        textSize = 10f
        setTextColor(Color.WHITE)
        background = UI.round(ctx, Color.parseColor("#B3000000"), 6)
        setPadding(d(5), d(1), d(5), d(1))
    }
    val badgeLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.BOTTOM)
    badgeLp.setMargins(0, 0, d(4), d(4))
    frame.addView(badge, badgeLp)

    val name = TextView(ctx).apply {
        textSize = if (grid) 11f else 13f
        setTextColor(UI.textMain)
        maxLines = if (grid) 1 else 2
        ellipsize = TextUtils.TruncateAt.END
    }
    val info = TextView(ctx).apply {
        textSize = 12f
        setTextColor(UI.textSub)
    }
    val check = TextView(ctx).apply {
        gravity = Gravity.CENTER
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
    }

    val root: View
    if (grid) {
        badge.visibility = View.VISIBLE
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(frame, LinearLayout.LayoutParams(cell, cell))
        val nameLp = LinearLayout.LayoutParams(cell, WRAP)
        nameLp.topMargin = d(4)
        col.addView(name, nameLp)
        val checkLp = FrameLayout.LayoutParams(d(24), d(24), Gravity.START or Gravity.TOP)
        checkLp.setMargins(d(6), d(6), 0, 0)
        frame.addView(check, checkLp)
        col.layoutParams = AbsListView.LayoutParams(cell, WRAP)
        root = col
    } else {
        badge.visibility = View.GONE
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(8), d(8), d(12), d(8))
        }
        row.addView(frame, LinearLayout.LayoutParams(d(96), d(64)))
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(12), 0, d(8), 0)
        }
        col.addView(name)
        col.addView(info)
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(check, LinearLayout.LayoutParams(d(24), d(24)))
        row.layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
        root = row
    }
    return ItemHolder(root, thumb, badge, name, info, check)
}

fun bindHolder(
    ctx: Context,
    h: ItemHolder,
    item: VideoItem,
    selected: Boolean,
    invalid: Boolean,
    grid: Boolean
) {
    val d = { v: Int -> UI.dp(ctx, v) }
    val dur = if (item.durationMs > 0) fmtDuration(item.durationMs) else "--:--"
    h.name.text = item.name
    h.name.setTextColor(if (invalid) RED else UI.textMain)
    h.info.setTextColor(UI.textSub)
    h.info.text = dur + "  •  " + fmtSize(item.size)
    h.badge.text = dur

    h.thumb.tag = item.id
    h.thumb.setImageBitmap(null)
    Thumbs.load(ctx, item) { bmp ->
        if (bmp != null && h.thumb.tag == item.id) h.thumb.setImageBitmap(bmp)
    }

    if (!grid) {
        h.root.background = GradientDrawable().apply {
            setColor(UI.card)
            cornerRadius = d(12).toFloat()
            val strokeColor = when {
                invalid -> RED
                selected -> UI.blue
                else -> UI.border
            }
            setStroke(d(if (selected || invalid) 2 else 1), strokeColor)
        }
    }
    h.thumb.alpha = when {
        invalid -> 0.4f
        selected && grid -> 0.55f
        else -> 1f
    }

    if (invalid) {
        h.check.text = "!"
        h.check.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(RED)
        }
    } else {
        h.check.text = if (selected) "✓" else ""
        h.check.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (selected) {
                setColor(UI.blue)
            } else {
                setColor(if (grid) Color.parseColor("#55000000") else Color.TRANSPARENT)
                setStroke(d(2), if (grid) Color.WHITE else Color.parseColor("#9CA3AF"))
            }
        }
    }
}

class VideoAdapter(
    private val ctx: Context,
    private val items: List<VideoItem>,
    private val grid: Boolean,
    private val cell: Int,
    private val isSelected: (Long) -> Boolean,
    private val isInvalid: (Long) -> Boolean
) : BaseAdapter() {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = items[position].id
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val h = (convertView?.tag as? ItemHolder)
            ?: createHolder(ctx, grid, cell).also { it.root.tag = it }
        val item = items[position]
        bindHolder(ctx, h, item, isSelected(item.id), isInvalid(item.id), grid)
        return h.root
    }
}

class FolderAdapter(
    private val ctx: Context,
    private val items: List<Pair<String, Int>>
) : BaseAdapter() {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val d = { v: Int -> UI.dp(ctx, v) }
        val outer = FrameLayout(ctx).apply { setPadding(0, 0, 0, d(8)) }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UI.cardBg(ctx, UI.card, 12)
            setPadding(d(14), d(14), d(14), d(14))
        }
        row.addView(TextView(ctx).apply {
            text = "📁"
            textSize = 26f
        })
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(12), 0, d(8), 0)
        }
        col.addView(TextView(ctx).apply {
            text = items[position].first
            textSize = 15f
            setTextColor(UI.textMain)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        col.addView(TextView(ctx).apply {
            text = "${items[position].second} videos"
            textSize = 12f
            setTextColor(UI.textSub)
        })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(TextView(ctx).apply {
            text = "›"
            textSize = 22f
            setTextColor(UI.textSub)
        })
        outer.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
        outer.layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
        return outer
    }
}
