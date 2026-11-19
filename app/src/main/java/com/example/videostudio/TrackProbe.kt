package com.example.videostudio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit

data class TrackInfo(val short: String, val detail: String)

class ProbeResult(val audio: List<TrackInfo>, val subs: List<TrackInfo>)

object TrackProbe {

    // ভিডিওর আসল মাপ (প্রস্থ, উচ্চতা)
    fun dims(ctx: Context, uri: Uri): Pair<Int, Int>? {
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) {
                    val t = w
                    w = h
                    h = t
                }
                if (w > 0 && h > 0) return Pair(w, h)
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
        }
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val session = FFprobeKit.getMediaInformation("/proc/self/fd/" + pfd.fd)
                val streams = session.mediaInformation?.streams
                if (streams != null) {
                    for (st in streams) {
                        if (st.type == "video") {
                            val w = (st.width ?: 0L).toInt()
                            val h = (st.height ?: 0L).toInt()
                            if (w > 0 && h > 0) return Pair(w, h)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    // ফাইলের ভেতরের অডিও ও সাবটাইটেল ট্র্যাকের তালিকা
    fun probe(ctx: Context, uri: Uri): ProbeResult? {
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val session = FFprobeKit.getMediaInformation("/proc/self/fd/" + pfd.fd)
                val info = session.mediaInformation ?: return null
                val streams = info.streams ?: return null
                val audio = ArrayList<TrackInfo>()
                val subs = ArrayList<TrackInfo>()
                for (st in streams) {
                    val lang = try {
                        st.tags?.optString("language", "") ?: ""
                    } catch (_: Throwable) {
                        ""
                    }
                    val suffix = if (lang.isNotEmpty() && lang != "und") " (" + lang.uppercase() + ")" else ""
                    val codec = (st.codec ?: "").uppercase()
                    when (st.type) {
                        "audio" -> audio.add(TrackInfo("AUDIO " + (audio.size + 1) + suffix, codec))
                        "subtitle" -> subs.add(TrackInfo("SUBTITLE " + (subs.size + 1) + suffix, codec))
                    }
                }
                return ProbeResult(audio, subs)
            }
        } catch (_: Throwable) {
        }
        return null
    }
}
