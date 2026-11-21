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
        Opt("M4A", "AAC • প্রায় সব ডিভাইসে চলে"),
        Opt("AAC", "AAC (ADTS)"),
        Opt("OGG", "Vorbis"),
        Opt("OPUS", "Opus • ছোট সাইজ, ভালো কোয়ালিটি"),
        Opt("FLAC", "Lossless • বড় সাইজ"),
        Opt("WAV", "PCM • আনকম্প্রেসড"),
        Opt("AC3", "Dolby Digital"),
        Opt("WMA", "Windows Media Audio")
    )
    val bitrates = listOf("AUTO", "64K", "96K", "128K", "192K", "256K", "320K")
    val sampleRates = listOf("ORIGINAL", "48000", "44100", "32000", "22050", "16000", "8000")
    val channelOpts = listOf("ORIGINAL", "MONO", "STEREO")

    fun ext(f: String): String = when (f) {
        "M4A" -> "m4a"
        "AAC" -> "aac"
        "OGG" -> "ogg"
        "OPUS" -> "opus"
        "FLAC" -> "flac"
        "WAV" -> "wav"
        "AC3" -> "ac3"
        "WMA" -> "wma"
        else -> "m4a"
    }

    fun mime(f: String): String = when (f) {
        "M4A" -> "audio/mp4"
        "AAC" -> "audio/aac"
        "OGG" -> "audio/ogg"
        "OPUS" -> "audio/opus"
        "FLAC" -> "audio/flac"
        "WAV" -> "audio/x-wav"
        "AC3" -> "audio/ac3"
        "WMA" -> "audio/x-ms-wma"
        else -> "audio/mp4"
    }

    fun lossless(f: String): Boolean = f == "FLAC" || f == "WAV"
}

object AudioPlanner {
    private fun defaultBitrate(f: String): String = when (f) {
        "OPUS" -> "96k"
        "AC3" -> "192k"
        else -> "128k"
    }

    fun label(a: AudioSpec): String {
        val parts = ArrayList<String>()
        parts.add(a.format)
        if (!AudioOptions.lossless(a.format)) {
            parts.add(if (a.bitrate == "AUTO") defaultBitrate(a.format).uppercase() else a.bitrate)
        }
        if (a.sampleRate != "ORIGINAL") {
            val khz = (a.sampleRate.toIntOrNull() ?: 0) / 1000.0
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
            "OGG" -> args.addAll(listOf("-c:a", "libvorbis", "-b:a", br))
            "OPUS" -> args.addAll(listOf("-c:a", "libopus", "-b:a", br))
            "FLAC" -> args.addAll(listOf("-c:a", "flac"))
            "WAV" -> args.addAll(listOf("-c:a", "pcm_s16le"))
            "AC3" -> args.addAll(listOf("-c:a", "ac3", "-b:a", br))
            "WMA" -> args.addAll(listOf("-c:a", "wmav2", "-b:a", br))
        }

        if (a.sampleRate != "ORIGINAL") {
            args.add("-ar")
            args.add(a.sampleRate)
        }
        when (a.channels) {
            "MONO" -> args.addAll(listOf("-ac", "1"))
            "STEREO" -> args.addAll(listOf("-ac", "2"))
        }

        val muxer = when (a.format) {
            "M4A" -> "ipod"
            "AAC" -> "adts"
            "OGG" -> "ogg"
            "OPUS" -> "ogg"
            "FLAC" -> "flac"
            "WAV" -> "wav"
            "AC3" -> "ac3"
            "WMA" -> "asf"
            else -> "ipod"
        }
        args.add("-f")
        args.add(muxer)
        args.add(outPath)
        return args
    }
}
