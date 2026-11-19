package com.example.videostudio

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.PathParser

// পাতলা লাইনের ক্লাসিক আইকন (২৪×২৪ গ্রিড)
object LineIcons {
    const val HOME = "M3,11L12,3L21,11M5,9.5V20h5v-6h4v6h5V9.5"
    const val FOLDER =
        "M3,7a2,2 0 0 1 2,-2h4l2,2h8a2,2 0 0 1 2,2v9a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2z"
    const val ADD_FOLDER =
        "M3,7a2,2 0 0 1 2,-2h4l2,2h8a2,2 0 0 1 2,2v9a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2zM12,11v6M9,14h6"
    const val BACK = "M19,12H5M11,6l-6,6l6,6"
    const val SEARCH = "M5,11a6,6 0 1,0 12,0a6,6 0 1,0 -12,0M20,20l-4.2,-4.2"
    const val GRID = "M4,4h6v6h-6zM14,4h6v6h-6zM4,14h6v6h-6zM14,14h6v6h-6z"
    const val LIST = "M4,6h16M4,12h16M4,18h16"
    const val SORT = "M7,4v16M7,4l-3,3M7,4l3,3M17,20V4M17,20l-3,-3M17,20l3,-3"
    const val HELP =
        "M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0M9.5,9.5a2.5,2.5 0 1,1 3.5,2.3c-0.7,0.4 -1,0.9 -1,1.7M12,17v0.01"
    const val CHEVRON = "M9,6l6,6l-6,6"
    const val DOWN = "M6,9l6,6l6,-6"
    const val UP = "M6,15l6,-6l6,6"
    const val PROFILE = "M12,3l9,4.5l-9,4.5l-9,-4.5zM3,12l9,4.5l9,-4.5M3,16.5l9,4.5l9,-4.5"
    const val FORMAT =
        "M5,5h14a1,1 0 0 1 1,1v12a1,1 0 0 1 -1,1h-14a1,1 0 0 1 -1,-1v-12a1,1 0 0 1 1,-1zM8,5v14M16,5v14M4,9h4M4,15h4M16,9h4M16,15h4"
    const val RESOLUTION =
        "M4,9V5a1,1 0 0 1 1,-1h4M15,4h4a1,1 0 0 1 1,1v4M20,15v4a1,1 0 0 1 -1,1h-4M9,20H5a1,1 0 0 1 -1,-1v-4M9,9h6v6h-6z"
    const val SUBTITLE =
        "M5,5h14a1,1 0 0 1 1,1v12a1,1 0 0 1 -1,1h-14a1,1 0 0 1 -1,-1v-12a1,1 0 0 1 1,-1zM7,11h4M13,11h4M7,15h6M15,15h2"
    const val AUDIO = "M4,10v4h3l5,4v-12l-5,4zM15.5,9.5a4,4 0 0 1 0,5M18.5,7a8,8 0 0 1 0,10"
    const val ADVANCED =
        "M4,6h9M17,6h3M4,12h3M11,12h9M4,18h11M19,18h1M13,6a2,2 0 1,0 4,0a2,2 0 1,0 -4,0M7,12a2,2 0 1,0 4,0a2,2 0 1,0 -4,0M15,18a2,2 0 1,0 4,0a2,2 0 1,0 -4,0"
    const val COMPRESS =
        "M4,4L10,10M6,10h4v-4M20,20L14,14M18,14h-4v4M20,4L14,10M14,6v4h4M4,20L10,14M6,14h4v4"
    const val BOLT = "M13,3L5,13h6l-1,8l8,-10h-6z"
}

class PathIcon(pathData: String, color: Int, private val strokeW: Float = 1.9f) : Drawable() {
    private val path: Path = try {
        PathParser.createPathFromPathData(pathData)
    } catch (_: Throwable) {
        Path()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = strokeW
    }

    init {
        paint.color = color
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val sc = minOf(b.width(), b.height()) / 24f
        if (sc <= 0f) return
        canvas.save()
        canvas.translate(
            b.left + (b.width() - 24f * sc) / 2f,
            b.top + (b.height() - 24f * sc) / 2f
        )
        canvas.scale(sc, sc)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    fun setColor(c: Int) {
        paint.color = c
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.colorFilter = cf
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun lineIcon(ctx: Context, path: String, color: Int, sizeDp: Int): ImageView {
    val px = UI.dp(ctx, sizeDp)
    return ImageView(ctx).apply {
        setImageDrawable(PathIcon(path, color))
        layoutParams = LinearLayout.LayoutParams(px, px)
    }
}
