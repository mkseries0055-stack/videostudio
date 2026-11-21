package com.example.videostudio

data class AudioSpec(
    val format: String,
    val bitrate: String,
    val sampleRate: String,
    val channels: String,
    val trackIndex: Int,
    val keepMetadata: Boolean
)

class AudioSettings {
    var format = "M4A"
    var bitrate = "AUTO"
    var sampleRate = "ORIGINAL"
    var channels = "ORIGINAL"
    var track = 0
}

object AudioOptions {
    val formats = listOf(
        Opt("M4A"),
        Opt("AAC"),
        Opt("AC3"),
        Opt("WAV"),
        Opt("OGG"),
        Opt("OPUS"),
        Opt("FLAC"),
        Opt("WMA"),
        Opt("ALAC"),
        Opt("MP2"),
        Opt("EAC3"),
        Opt("AIFF"),
        Opt("AU"),
        Opt("MKA")
    )
    val bitrates = listOf("AUTO", "64K", "96K", "128K", "192K", "256K", "320K")
    val sampleRates = listOf(
        "ORIGINAL", "48000", "44100", "32000", "24000", "22050", "16000", "12000", "11025", "8000"
    )
    val channelOpts = listOf("ORIGINAL", "MONO", "STEREO")

    fun ext(f: String): String = when (f) {
        "M4A" -> "m4a"
        "AAC" -> "aac"
        "AC3" -> "ac3"
        "WAV" -> "wav"
        "OGG" -> "ogg"
        "OPUS" -> "opus"
        "FLAC" -> "flac"
        "WMA" -> "wma"
        "ALAC" -> "m4a"
        "MP2" -> "mp2"
        "EAC3" -> "eac3"
        "AIFF" -> "aiff"
        "AU" -> "au"
        "MKA" -> "mka"
        else -> "m4a"
    }

    fun mime(f: String): String = when (f) {
        "M4A" -> "audio/mp4"
        "AAC" -> "audio/aac"
        "AC3" -> "audio/ac3"
        "WAV" -> "audio/x-wav"
        "OGG" -> "audio/ogg"
        "OPUS" -> "audio/opus"
        "FLAC" -> "audio/flac"
        "WMA" -> "audio/x-ms-wma"
        "ALAC" -> "audio/mp4"
        "MP2" -> "audio/mpeg"
        "EAC3" -> "audio/eac3"
        "AIFF" -> "audio/x-aiff"
        "AU" -> "audio/basic"
        "MKA" -> "audio/x-matroska"
        else -> "audio/mp4"
    }

    // লসলেস বা আনকম্প্রেসড ফরম্যাটে বিটরেট লাগে না
    fun lossless(f: String): Boolean =
        f == "FLAC" || f == "WAV" || f == "ALAC" || f == "AIFF" || f == "AU"
}

object AudioPlanner {
    private fun defaultBitrate(f: String): String = when (f) {
        "OPUS" -> "96k"
        "AC3", "EAC3", "MP2" -> "192k"
        else -> "128k"
    }

    // কিছু কোডেক সব স্যাম্পল রেট সাপোর্ট করে না, তাই কাছাকাছি সাপোর্টেড মাপ বসানো
    private fun fixRate(format: String, rate: String): String {
        if (rate == "ORIGINAL") return rate
        val r = rate.toIntOrNull() ?: return rate
        return when (format) {
            "AC3", "EAC3" -> if (r == 32000 || r == 44100 || r == 48000) rate else "48000"
            "MP2" -> if (listOf(16000, 22050, 24000, 32000, 44100, 48000).contains(r)) rate else "44100"
            else -> rate
        }
    }

    fun label(a: AudioSpec): String {
        val parts = ArrayList<String>()
        parts.add(a.format)
        if (!AudioOptions.lossless(a.format)) {
            parts.add(if (a.bitrate == "AUTO") defaultBitrate(a.format).uppercase() else a.bitrate)
        }
        if (a.sampleRate != "ORIGINAL") {
            val khz = (fixRate(a.format, a.sampleRate).toIntOrNull() ?: 0) / 1000.0
            parts.add(khz.toString().removeSuffix(".0") + " kHz")
        }
        when (a.channels) {
            "MONO" -> parts.add("Mono")
            "STEREO" -> parts.add("Stereo")
        }
        return parts.joinToString("  •  ")
    }

    fun build(a: AudioSpec, input: String, outPath: String): List<String> {
        val args = ArrayList<String>()
        args.add("-y")
        args.add("-i")
        args.add(input)
        args.add("-vn")
        args.add("-map")
        args.add("0:a:" + a.trackIndex + "?")
        if (!a.keepMetadata) {
            args.add("-map_metadata")
            args.add("-1")
        }

        val br = (if (a.bitrate == "AUTO") defaultBitrate(a.format) else a.bitrate).lowercase()
        when (a.format) {
            "M4A" -> args.addAll(listOf("-c:a", "aac", "-b:a", br))
            "AAC" -> args.addAll(listOf("-c:a", "aac", "-b:a", br))
            "AC3" -> args.addAll(listOf("-c:a", "ac3", "-b:a", br))
            "WAV" -> args.addAll(listOf("-c:a", "pcm_s16le"))
            "OGG" -> args.addAll(listOf("-c:a", "libvorbis", "-b:a", br))
            "OPUS" -> args.addAll(listOf("-c:a", "libopus", "-b:a", br))
            "FLAC" -> args.addAll(listOf("-c:a", "flac"))
            "WMA" -> args.addAll(listOf("-c:a", "wmav2", "-b:a", br))
            "ALAC" -> args.addAll(listOf("-c:a", "alac"))
            "MP2" -> args.addAll(listOf("-c:a", "mp2", "-b:a", br))
            "EAC3" -> args.addAll(listOf("-c:a", "eac3", "-b:a", br))
            "AIFF" -> args.addAll(listOf("-c:a", "pcm_s16be"))
            "AU" -> args.addAll(listOf("-c:a", "pcm_s16be"))
            "MKA" -> args.addAll(listOf("-c:a", "aac", "-b:a", br))
        }

        val rate = fixRate(a.format, a.sampleRate)
        if (rate != "ORIGINAL") {
            args.add("-ar")
            args.add(rate)
        }
        when (a.channels) {
            "MONO" -> args.addAll(listOf("-ac", "1"))
            "STEREO" -> args.addAll(listOf("-ac", "2"))
        }

        val muxer = when (a.format) {
            "M4A" -> "ipod"
            "AAC" -> "adts"
            "AC3" -> "ac3"
            "WAV" -> "wav"
            "OGG" -> "ogg"
            "OPUS" -> "ogg"
            "FLAC" -> "flac"
            "WMA" -> "asf"
            "ALAC" -> "ipod"
            "MP2" -> "mp2"
            "EAC3" -> "eac3"
            "AIFF" -> "aiff"
            "AU" -> "au"
            "MKA" -> "matroska"
            else -> "ipod"
        }
        args.add("-f")
        args.add(muxer)
        args.add(outPath)
        return args
    }
}
