package com.example.videostudio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit

data class TrackInfo(val short: String, val detail: String)

class ProbeResult(val audio: List<TrackInfo>, val subs: List<TrackInfo>)

object TrackProbe {

    // ফাইলের ভেতরের অডিও ও সাবটাইটেল ট্র্যাকের তালিকা বের করা
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
