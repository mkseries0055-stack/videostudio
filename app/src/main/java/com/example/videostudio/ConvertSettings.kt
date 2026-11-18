package com.example.videostudio

data class Opt(val label: String, val sub: String = "")

class ConvertSettings {
    var profile = "NONE"
    var format = "ORIGINAL"
    var resolution = "ORIGINAL"
    var audioMode = "DEFAULT"      // DEFAULT, ALL, NONE, অথবা TRACK:<নম্বর>
    var subtitleMode = "DEFAULT"
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

    val normalRes = listOf("ORIGINAL", "1080P", "720P", "480P", "360P", "240P", "144P")
    val keypadRes = listOf("176×144", "352×288", "128×96")
    val frameRates = listOf("ORIGINAL", "30", "25", "24", "15")
    val audioBitrates = listOf("AUTO", "64K", "96K", "128K", "192K")

    val basicModes = listOf(
        Opt("DEFAULT", "ফাইলের ডিফল্ট ট্র্যাক"),
        Opt("ALL", "সব ট্র্যাক রাখুন"),
        Opt("NONE", "বাদ দিন")
    )

    fun resolutionsFor(format: String): List<String> =
        if (format == "3GP") keypadRes else normalRes

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
        if (!resolutionsFor(f).contains(s.resolution)) {
            s.resolution = if (f == "3GP") "176×144" else "ORIGINAL"
        }
    }
}
