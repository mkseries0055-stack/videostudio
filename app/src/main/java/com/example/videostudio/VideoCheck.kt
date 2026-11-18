package com.example.videostudio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit

object VideoCheck {

    // ফাইলের ভেতরের ডেটা দেখে ভিডিও কি না যাচাই (নাম বা এক্সটেনশন নয়)
    fun isVideo(ctx: Context, uri: Uri): Boolean {
        // ১) অ্যান্ড্রয়েডের নিজস্ব পদ্ধতি
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                    return true
                }
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
        }

        // ২) FFprobe (AVI, FLV ইত্যাদির জন্য)
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val session = FFprobeKit.getMediaInformation("/proc/self/fd/" + pfd.fd)
                val info = session.mediaInformation
                val streams = info?.streams
                if (streams != null) {
                    for (s in streams) {
                        if (s.type == "video") {
                            val c = s.codec ?: ""
                            if (c != "mjpeg" && c != "png" && c != "bmp") return true
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return false
    }
}
