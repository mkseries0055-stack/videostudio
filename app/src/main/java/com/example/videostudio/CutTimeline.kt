package com.example.videostudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

// থাম্বনেইল স্ট্রিপ, শুরু/শেষ হ্যান্ডেল ও হলুদ প্লেহেডসহ কাটার টাইমলাইন
class CutTimeline(ctx: Context) : View(ctx) {
    var durationMs = 1L
    var startMs = 0L
    var endMs = 1L
    var posMs = 0L
    var onChange: ((Long, Long, Long, Boolean) -> Unit)? = null

    private var thumbs: List<Bitmap> = emptyList()
    private val dn = resources.displayMetrics.density
    private val hw = 22f * dn
    private val padV = 6f * dn
    private val slop = 30f * dn
    private val minLen = 200L
    private var drag = 0

    private val blue = Color.parseColor("#0EA5E9")
    private val pStrip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#374151") }
    private val pBmp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pDim = Paint().apply { color = Color.parseColor("#99000000") }
    private val pHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
    private val pArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = blue
        style = Paint.Style.STROKE
        strokeWidth = 3f * dn
    }
    private val pHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FACC15")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dn
        strokeCap = Paint.Cap.ROUND
    }

    fun setThumbs(list: List<Bitmap>) {
        thumbs = list
        invalidate()
    }

    private fun xOf(ms: Long): Float {
        val l = hw
        val r = width - hw
        val frac = if (durationMs > 0) ms.toFloat() / durationMs.toFloat() else 0f
        return l + frac.coerceIn(0f, 1f) * (r - l)
    }

    private fun msOf(x: Float): Long {
        val l = hw
        val r = width - hw
        if (r <= l) return 0L
        val frac = ((x - l) / (r - l)).coerceIn(0f, 1f)
        return (frac * durationMs).toLong()
    }

    private fun cropRect(b: Bitmap, dst: RectF): Rect {
        val bw = b.width.toFloat()
        val bh = b.height.toFloat()
        val dr = dst.width() / dst.height()
        val br = bw / bh
        return if (br > dr) {
            val nw = bh * dr
            val x0 = ((bw - nw) / 2f).toInt()
            Rect(x0, 0, (x0 + nw).toInt(), b.height)
        } else {
            val nh = bw / dr
            val y0 = ((bh - nh) / 2f).toInt()
            Rect(0, y0, b.width, (y0 + nh).toInt())
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val l = hw
        val r = w - hw
        val top = padV
        val bottom = h - padV
        c.drawRect(RectF(l, top, r, bottom), pStrip)

        if (thumbs.isNotEmpty()) {
            val seg = (r - l) / thumbs.size
            for (i in thumbs.indices) {
                val b = thumbs[i]
                val dst = RectF(l + i * seg, top, l + (i + 1) * seg + 1f, bottom)
                c.drawBitmap(b, cropRect(b, dst), dst, pBmp)
            }
        }

        val xs = xOf(startMs)
        val xe = xOf(endMs)
        c.drawRect(l, top, xs, bottom, pDim)
        c.drawRect(xe, top, r, bottom, pDim)
        c.drawLine(xs, top, xe, top, pBorder)
        c.drawLine(xs, bottom, xe, bottom, pBorder)

        val rad = 8f * dn
        c.drawRoundRect(RectF(xs - hw, 0f, xs, h), rad, rad, pHandle)
        c.drawRoundRect(RectF(xe, 0f, xe + hw, h), rad, rad, pHandle)

        val cy = h / 2f
        val a = 4f * dn
        val left = Path().apply {
            moveTo(xs - hw / 2f - a / 2f, cy - a)
            lineTo(xs - hw / 2f + a / 2f, cy)
            lineTo(xs - hw / 2f - a / 2f, cy + a)
            close()
        }
        c.drawPath(left, pArrow)
        val right = Path().apply {
            moveTo(xe + hw / 2f + a / 2f, cy - a)
            lineTo(xe + hw / 2f - a / 2f, cy)
            lineTo(xe + hw / 2f + a / 2f, cy + a)
            close()
        }
        c.drawPath(right, pArrow)

        val xp = xOf(posMs)
        c.drawLine(xp, 0f, xp, h, pHead)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val xs = xOf(startMs)
                val xe = xOf(endMs)
                val ds = abs(x - (xs - hw / 2f))
                val de = abs(x - (xe + hw / 2f))
                drag = when {
                    ds <= slop && ds <= de -> 1
                    de <= slop -> 2
                    else -> 3
                }
                applyDrag(x)
            }
            MotionEvent.ACTION_MOVE -> applyDrag(x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drag = 0
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun applyDrag(x: Float) {
        when (drag) {
            1 -> {
                val t = msOf(x + hw / 2f)
                startMs = minOf(t, (endMs - minLen).coerceAtLeast(0L)).coerceAtLeast(0L)
                posMs = startMs
            }
            2 -> {
                val t = msOf(x - hw / 2f)
                endMs = maxOf(t, startMs + minLen).coerceAtMost(durationMs)
                posMs = endMs
            }
            3 -> posMs = msOf(x)
        }
        invalidate()
        onChange?.invoke(startMs, endMs, posMs, true)
    }
}
