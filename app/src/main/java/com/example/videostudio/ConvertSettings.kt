package com.example.videostudio

import android.net.Uri

data class Opt(val label: String, val sub: String = "")

data class ExtTrack(val uri: Uri, val name: String)

class ConvertSettings {
    var profile = "NONE"
    var format = "ORIGINAL"
    var resolution = "ORIGINAL"    // ORIGINAL, 720P, CUSTOM:854x480, 176×144
    var audioSel: Set<Int> = setOf(0)   // খালি মানে NONE (ইনডেক্স: ভেতরের ট্র্যাক, তারপর বাহ্যিক)
    var subSel: Set<Int> = setOf(0)
    val extAudio = ArrayList<ExtTrack>()
    val extSubs = ArrayList<ExtTrack>()
    var audioMode = "DEFAULT"      // পুরোনো, এখন ব্যবহার হয় না
    var subtitleMode = "DEFAULT"   // পুরোনো, এখন ব্যবহার হয় না
    var frameRate = "ORIGINAL"
    var audioBitrate = "AUTO"
    var compress = false
    var keepMetadata = true
    var useOriginalName = true
    var outputName = ""
    var prefix = "video_converter_"
}

object Options {
    val profiles = listOf(
        Opt("NONE", "কোনো প্রিসেট নয়"),
        Opt("Keypad phone", "3GP • 176×144 • পুরোনো কিপ্যাড ফোন"),
        Opt("Small MP4", "MP4 • 480P • ছোট সাইজ"),
        Opt("HD share", "MP4 • 720P • শেয়ারের জন্য"),
        Opt("Web video", "WEBM • 480P • ছোট সাইজ")
    )

    val formats = listOf(
        Opt("ORIGINAL", "মূল ফরম্যাট ঠিক রেখে"),
        Opt("3GP", "H.263 • পুরোনো কিপ্যাড ফোন"),
        Opt("AVI", "MPEG-4"),
        Opt("FLV", "FLV1"),
        Opt("M4V", "MPEG-4 • AAC"),
        Opt("MKV", "MPEG-4 • AAC"),
        Opt("MOV", "MPEG-4 • AAC"),
        Opt("MP4", "MPEG-4 • AAC"),
        Opt("MPEG", "MPEG-2"),
        Opt("WEBM", "VP9 • Opus")
    )

    val keypadRes = listOf("176×144", "352×288", "128×96")
    val presetHeights = listOf(1440, 1080, 960, 720, 640, 480, 360, 320, 240, 144)
    val frameRates = listOf("ORIGINAL", "30", "25", "24", "15")
    val audioBitrates = listOf("AUTO", "64K", "96K", "128K", "192K")

    fun applyProfile(s: ConvertSettings, name: String) {
        s.profile = name
        when (name) {
            "Keypad phone" -> {
                s.format = "3GP"
                s.resolution = "176×144"
                s.compress = false
            }
            "Small MP4" -> {
                s.format = "MP4"
                s.resolution = "480P"
                s.compress = true
            }
            "HD share" -> {
                s.format = "MP4"
                s.resolution = "720P"
                s.compress = false
            }
            "Web video" -> {
                s.format = "WEBM"
                s.resolution = "480P"
                s.compress = true
            }
        }
    }

    fun setFormat(s: ConvertSettings, f: String) {
        s.format = f
        s.profile = "NONE"
        if (f == "3GP") {
            if (!keypadRes.contains(s.resolution)) s.resolution = "176×144"
        } else if (keypadRes.contains(s.resolution)) {
            s.resolution = "ORIGINAL"
        }
    }
}
