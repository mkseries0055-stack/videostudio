package com.example.videostudio

data class JobSpec(
    val single: Boolean,
    val format: String,
    val resolution: String,
    val frameRate: String,
    val audioBitrate: String,
    val compress: Boolean,
    val keepMetadata: Boolean,
    val audioSel: Set<Int>,
    val subSel: Set<Int>,
    val extAudio: List<ExtTrack>,
    val extSubs: List<ExtTrack>,
    val outName: String
)

object Containers {
    fun ext(fmt: String): String = when (fmt) {
        "MP4" -> "mp4"
        "M4V" -> "m4v"
        "MOV" -> "mov"
        "MKV" -> "mkv"
        "3GP" -> "3gp"
        "AVI" -> "avi"
        "FLV" -> "flv"
        "MPEG" -> "mpeg"
        "WEBM" -> "webm"
        else -> "mkv"
    }

    fun mime(fmt: String): String = when (fmt) {
        "MP4" -> "video/mp4"
        "M4V" -> "video/x-m4v"
        "MOV" -> "video/quicktime"
        "MKV" -> "video/x-matroska"
        "3GP" -> "video/3gpp"
        "AVI" -> "video/x-msvideo"
        "FLV" -> "video/x-flv"
        "MPEG" -> "video/mpeg"
        "WEBM" -> "video/webm"
        else -> "video/x-matroska"
    }

    fun muxer(fmt: String): String = when (fmt) {
        "MP4" -> "mp4"
        "M4V" -> "mp4"
        "MOV" -> "mov"
        "MKV" -> "matroska"
        "3GP" -> "3gp"
        "AVI" -> "avi"
        "FLV" -> "flv"
        "MPEG" -> "mpeg"
        "WEBM" -> "webm"
        else -> "matroska"
    }

    // ORIGINAL ফরম্যাটের ক্ষেত্রে সোর্স ফাইলের নাম দেখে ফরম্যাট ঠিক করা
    fun fromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp4" -> "MP4"
            "m4v" -> "M4V"
            "mov" -> "MOV"
            "3gp", "3g2" -> "3GP"
            "avi" -> "AVI"
            "flv" -> "FLV"
            "mpg", "mpeg" -> "MPEG"
            "webm" -> "WEBM"
            else -> "MKV"
        }
    }
}

class Plan(
    val args: List<String>,
    val fmt: String,
    val vLabel: String,
    val aLabel: String,
    val subsDropped: Boolean
)

object Planner {
    private val TEXT_SUBS = setOf("ASS", "SSA", "SUBRIP", "SRT", "MOV_TEXT", "WEBVTT", "TEXT")

    fun build(
        spec: JobSpec,
        sourceName: String,
        mainInput: String,
        embA: List<Int>,
        extA: List<String>,
        embS: List<Pair<Int, String>>,
        extS: List<String>,
        outPath: String
    ): Plan {
        val fmt = if (spec.format == "ORIGINAL") Containers.fromName(sourceName) else spec.format
        val copyMode = spec.format == "ORIGINAL" && spec.resolution == "ORIGINAL" &&
            spec.frameRate == "ORIGINAL" && !spec.compress && spec.audioBitrate == "AUTO"
        val mp4Family = fmt == "MP4" || fmt == "M4V" || fmt == "MOV"

        val a = ArrayList<String>()
        a.add("-y")
        a.add("-i")
        a.add(mainInput)

        var next = 1
        val audioIdx = ArrayList<Int>()
        for (p in extA) {
            a.add("-i")
            a.add(p)
            audioIdx.add(next)
            next++
        }
        val subIdx = ArrayList<Int>()
        for (p in extS) {
            a.add("-i")
            a.add(p)
            subIdx.add(next)
            next++
        }

        // ভিডিও ও অডিও ট্র্যাক
        a.add("-map")
        a.add("0:v:0?")
        for (i in embA) {
            a.add("-map")
            a.add("0:a:$i?")
        }
        for (k in audioIdx) {
            a.add("-map")
            a.add("$k:a:0")
        }

        // সাবটাইটেল ট্র্যাক
        val subOpts = ArrayList<String>()
        var dropped = false
        var j = 0
        if (fmt == "MKV" || mp4Family) {
            for (pair in embS) {
                val i = pair.first
                val codec = pair.second
                if (mp4Family && !TEXT_SUBS.contains(codec)) {
                    dropped = true
                    continue
                }
                a.add("-map")
                a.add("0:s:$i?")
                val c = if (mp4Family) "mov_text" else if (codec == "MOV_TEXT") "srt" else "copy"
                subOpts.add("-c:s:$j")
                subOpts.add(c)
                j++
            }
            for (k in subIdx) {
                a.add("-map")
                a.add("$k:s:0")
                subOpts.add("-c:s:$j")
                subOpts.add(if (mp4Family) "mov_text" else "copy")
                j++
            }
        } else if (embS.isNotEmpty() || extS.isNotEmpty()) {
            dropped = true
        }

        if (!spec.keepMetadata) {
            a.add("-map_metadata")
            a.add("-1")
        }

        var vLabel = "COPY"
        var aLabel = "COPY"

        if (copyMode) {
            a.add("-c:v")
            a.add("copy")
            a.add("-c:a")
            a.add("copy")
        } else {
            // রেজোলিউশন
            val vf = ArrayList<String>()
            val res = if (fmt == "3GP" && !spec.resolution.contains("×")) "176×144" else spec.resolution
            if (res.startsWith("CUSTOM:")) {
                val wh = res.substring(7).split("x")
                if (wh.size == 2) vf.add("scale=" + wh[0] + ":" + wh[1])
            } else if (res.contains("×")) {
                val wh = res.split("×")
                val w = wh[0]
                val h = wh[1]
                vf.add("scale=$w:$h:force_original_aspect_ratio=decrease")
                vf.add("pad=$w:$h:(ow-iw)/2:(oh-ih)/2")
            } else if (res.endsWith("P")) {
                val hp = res.dropLast(1).toIntOrNull()
                if (hp != null) {
                    vf.add("scale=if(gt(iw\\,ih)\\,-2\\,$hp):if(gt(iw\\,ih)\\,$hp\\,-2)")
                }
            }
            if (vf.isNotEmpty()) {
                a.add("-vf")
                a.add(vf.joinToString(","))
            }

            // ফ্রেম রেট
            val fps = if (spec.frameRate != "ORIGINAL") spec.frameRate else if (fmt == "3GP") "15" else null
            if (fps != null) {
                a.add("-r")
                a.add(fps)
            }

            // ভিডিও কোডেক
            val q = if (spec.compress) "8" else "4"
            when (fmt) {
                "3GP" -> {
                    vLabel = "H.263"
                    val w3 = res.split("×")[0].toIntOrNull() ?: 176
                    val br = if (w3 >= 352) "400k" else if (w3 <= 128) "64k" else "128k"
                    a.add("-c:v")
                    a.add("h263")
                    a.add("-b:v")
                    a.add(br)
                }
                "FLV" -> {
                    vLabel = "FLV1"
                    a.add("-c:v")
                    a.add("flv")
                    a.add("-q:v")
                    a.add(q)
                }
                "MPEG" -> {
                    vLabel = "MPEG-2"
                    a.add("-c:v")
                    a.add("mpeg2video")
                    a.add("-q:v")
                    a.add(q)
                }
                "WEBM" -> {
                    vLabel = "VP9"
                    a.add("-c:v")
                    a.add("libvpx-vp9")
                    a.add("-b:v")
                    a.add("0")
                    a.add("-crf")
                    a.add(if (spec.compress) "38" else "32")
                    a.add("-deadline")
                    a.add("realtime")
                    a.add("-cpu-used")
                    a.add("8")
                    a.add("-row-mt")
                    a.add("1")
                }
                else -> {
                    vLabel = "MPEG-4"
                    a.add("-c:v")
                    a.add("mpeg4")
                    a.add("-q:v")
                    a.add(q)
                    if (fmt == "AVI") {
                        a.add("-vtag")
                        a.add("xvid")
                    }
                }
            }

            // অডিও কোডেক
            val abr = if (spec.audioBitrate != "AUTO") spec.audioBitrate.lowercase()
            else if (fmt == "3GP") "32k"
            else if (spec.compress) "96k"
            else "128k"
            val aCodec = when (fmt) {
                "WEBM" -> "libopus"
                "MPEG" -> "mp2"
                else -> "aac"
            }
            aLabel = when (aCodec) {
                "libopus" -> "OPUS"
                "mp2" -> "MP2"
                else -> "AAC"
            }
            a.add("-c:a")
            a.add(aCodec)
            a.add("-b:a")
            a.add(abr)
            if (fmt == "3GP") {
                a.add("-ar")
                a.add("16000")
                a.add("-ac")
                a.add("1")
            }
        }

        a.addAll(subOpts)
        a.add("-f")
        a.add(Containers.muxer(fmt))
        if (mp4Family) {
            a.add("-movflags")
            a.add("+faststart")
        }
        a.add(outPath)

        return Plan(a, fmt, vLabel, aLabel, dropped)
    }
}
