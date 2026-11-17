package com.example.videostudio

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.util.Locale
import java.util.concurrent.Executors

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val size: Long,
    val folder: String,
    val dateAdded: Long
)

fun loadVideos(ctx: Context): List<VideoItem> {
    val out = ArrayList<VideoItem>()
    val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DATE_ADDED
    )
    try {
        val order = MediaStore.Video.Media.DATE_ADDED + " DESC"
        ctx.contentResolver.query(base, proj, null, null, order)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
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
                        c.getString(iBucket) ?: "Other",
                        c.getLong(iDate)
                    )
                )
            }
        }
    } catch (_: Throwable) {
    }
    return out
}

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun fmtSize(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024)
    else String.format(Locale.US, "%.2f MB", mb)
}

object Thumbs {
    private val cache = LruCache<Long, Bitmap>(100)
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, item: VideoItem, cb: (Bitmap?) -> Unit) {
        val cached = cache.get(item.id)
        if (cached != null) {
            cb(cached)
            return
        }
        val app = ctx.applicationContext
        pool.execute {
            val bmp: Bitmap? = try {
                app.contentResolver.loadThumbnail(item.uri, Size(256, 256), null)
            } catch (_: Throwable) {
                null
            }
            if (bmp != null) cache.put(item.id, bmp)
            main.post { cb(bmp) }
        }
    }
}
