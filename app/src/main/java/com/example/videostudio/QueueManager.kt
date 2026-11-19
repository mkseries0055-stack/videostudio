package com.example.videostudio

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

class Job(val id: Long, val item: VideoItem, val spec: JobSpec) {
    @Volatile var state = QueueManager.WAITING
    @Volatile var progress = 0f
    @Volatile var sessionId = -1L
    @Volatile var cancelRequested = false
    @Volatile var outUri: Uri? = null
    @Volatile var outDeleted = false
    var outName = ""
    var outSize = 0L
    var outRes = ""
    var info = ""
    var error = ""
    var note = ""
}

private class CancelledException : Exception()

object QueueManager {
    const val WAITING = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
    const val CANCELLED = 4

    val jobs = CopyOnWriteArrayList<Job>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var worker: Thread? = null
    @Volatile private var current: Job? = null
    @Volatile private var curTotal = 0L
    private var nextId = 1L
    private var statsInstalled = false
    private var lastNotify = 0L

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChange() {
        for (l in listeners) {
            try { l() } catch (_: Throwable) {}
        }
    }

    fun hasActive(): Boolean = jobs.any { it.state == WAITING || it.state == RUNNING }

    @Synchronized
    fun add(ctx: Context, items: List<VideoItem>, specs: List<JobSpec>) {
        if (!hasActive()) jobs.clear()
        for (i in items.indices) {
            jobs.add(Job(nextId, items[i], specs[i]))
            nextId++
        }
        startWorker(ctx.applicationContext)
        notifyChange()
    }

    fun cancelJob(job: Job) {
        if (job.state == WAITING) {
            job.state = CANCELLED
        } else if (job.state == RUNNING) {
            job.cancelRequested = true
            try { FFmpegKit.cancel(job.sessionId) } catch (_: Throwable) {}
        }
        notifyChange()
    }

    fun cancelAll() {
        for (j in jobs) cancelJob(j)
    }

    private fun installStats() {
        if (statsInstalled) return
        statsInstalled = true
        FFmpegKitConfig.enableStatisticsCallback { st ->
            val j = current
            val total = curTotal
            if (j != null && total > 0) {
                val p = (st.time.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
                j.progress = p
                val now = System.currentTimeMillis()
                if (now - lastNotify > 250) {
                    lastNotify = now
                    notifyChange()
                }
            }
        }
    }

    private fun startWorker(app: Context) {
        installStats()
        synchronized(this) {
            if (worker != null) return
            val t = Thread {
                while (true) {
                    var job: Job? = null
                    synchronized(this@QueueManager) {
                        job = jobs.firstOrNull { it.state == WAITING }
                        if (job == null) worker = null
                    }
                    val j = job ?: break
                    current = j
                    j.state = RUNNING
                    notifyChange()
                    try {
                        runJob(app, j)
                    } catch (e: Throwable) {
                        j.state = FAILED
                        j.error = e.toString()
                    }
                    current = null
                    notifyChange()
                }
            }
            worker = t
            t.start()
        }
    }

    private fun extOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().ifEmpty { "bin" }

    private fun safeName(n: String): String {
        val clean = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(100)
        return if (clean.isEmpty()) "video" else clean
    }

    private fun durationOf(app: Context, uri: Uri): Long {
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(app, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release()
            d
        } catch (_: Throwable) {
            0L
        }
    }

    private fun copyToCache(
        app: Context, uri: Uri, prefix: String, ext: String, job: Job, temps: MutableList<File>
    ): File {
        val f = File(app.cacheDir, prefix + "_" + System.nanoTime() + "." + ext)
        temps.add(f)
        app.contentResolver.openInputStream(uri)!!.use { input ->
            f.outputStream().use { output ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    if (job.cancelRequested) throw CancelledException()
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                }
            }
        }
        return f
    }

    private fun execute(job: Job, args: List<String>): Pair<Boolean, String> {
        val latch = CountDownLatch(1)
        var ok = false
        var out = ""
        val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { s ->
            ok = ReturnCode.isSuccess(s.returnCode)
            out = s.output ?: ""
            latch.countDown()
        }
        job.sessionId = session.sessionId
        if (job.cancelRequested) {
            try { FFmpegKit.cancel(job.sessionId) } catch (_: Throwable) {}
        }
        latch.await()
        return Pair(ok, out)
    }

    private fun displayName(app: Context, uri: Uri): String? = try {
        app.contentResolver.query(
            uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Throwable) {
        null
    }

    private fun saveOutput(app: Context, job: Job, file: File, fmt: String) {
        val resolver = app.contentResolver
        val orig = job.item.name.substringBeforeLast('.', job.item.name)
        val base = safeName(if (job.spec.outName.isBlank()) orig else job.spec.outName)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, base + "." + Containers.ext(fmt))
            put(MediaStore.Video.Media.MIME_TYPE, Containers.mime(fmt))
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoStudio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("আউটপুট ফাইল তৈরি করা যায়নি")
        resolver.openOutputStream(uri)!!.use { o ->
            file.inputStream().use { it.copyTo(o) }
        }
        val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        job.outUri = uri
        job.outSize = file.length()
        job.outName = displayName(app, uri) ?: (base + "." + Containers.ext(fmt))
        val d = TrackProbe.dims(app, uri)
        job.outRes = if (d != null) "${d.first}×${d.second}" else ""
    }

    private fun runJob(app: Context, job: Job) {
        val temps = ArrayList<File>()
        var pfd: ParcelFileDescriptor? = null
        try {
            if (job.cancelRequested) {
                job.state = CANCELLED
                return
            }
            val item = job.item
            val spec = job.spec

            var dur = item.durationMs
            if (dur <= 0) dur = durationOf(app, item.uri)
            curTotal = dur
            job.progress = 0f

            val probe = TrackProbe.probe(app, item.uri)

            val embA = ArrayList<Int>()
            val extA = ArrayList<ExtTrack>()
            val embS = ArrayList<Pair<Int, String>>()
            val extS = ArrayList<ExtTrack>()
            if (spec.single && probe != null) {
                val an = probe.audio.size
                for (i in spec.audioSel.sorted()) {
                    if (i < an) {
                        embA.add(i)
                    } else {
                        val e = spec.extAudio.getOrNull(i - an)
                        if (e != null) extA.add(e)
                    }
                }
                val sn = probe.subs.size
                for (i in spec.subSel.sorted()) {
                    if (i < sn) {
                        embS.add(Pair(i, probe.subs[i].detail))
                    } else {
                        val e = spec.extSubs.getOrNull(i - sn)
                        if (e != null) extS.add(e)
                    }
                }
            } else if (spec.single) {
                embA.add(0)
                extA.addAll(spec.extAudio)
                extS.addAll(spec.extSubs)
            } else {
                if (spec.audioSel.isNotEmpty()) embA.add(0)
                if (spec.subSel.isNotEmpty() && probe != null && probe.subs.isNotEmpty()) {
                    embS.add(Pair(0, probe.subs[0].detail))
                }
            }

            val extAPaths = ArrayList<String>()
            for (t in extA) {
                extAPaths.add(copyToCache(app, t.uri, "xa${job.id}", extOf(t.name), job, temps).absolutePath)
            }
            val extSPaths = ArrayList<String>()
            for (t in extS) {
                extSPaths.add(copyToCache(app, t.uri, "xs${job.id}", extOf(t.name), job, temps).absolutePath)
            }

            val fmt = if (spec.format == "ORIGINAL") Containers.fromName(item.name) else spec.format
            val outFile = File(app.cacheDir, "out_${job.id}_${System.nanoTime()}." + Containers.ext(fmt))
            temps.add(outFile)

            fun makePlan(input: String): Plan {
                val p = Planner.build(
                    spec, item.name, input, embA, extAPaths, embS, extSPaths, outFile.absolutePath
                )
                job.info = p.fmt + "  •  " + p.vLabel + "  •  " + p.aLabel +
                    (if (spec.compress) "  •  Compress" else "")
                if (p.subsDropped) job.note = "ℹ️ এই ফরম্যাটে কিছু সাবটাইটেল যোগ হয়নি"
                return p
            }

            val started = System.currentTimeMillis()
            var ok = false
            var log = ""
            pfd = try {
                app.contentResolver.openFileDescriptor(item.uri, "r")
            } catch (_: Throwable) {
                null
            }
            val fdNow = pfd
            if (fdNow != null) {
                val plan = makePlan("/proc/self/fd/" + fdNow.fd)
                notifyChange()
                val r = execute(job, plan.args)
                ok = r.first
                log = r.second
            }

            // সরাসরি পড়া না গেলে ফাইল কপি করে আবার চেষ্টা
            if (!ok && !job.cancelRequested &&
                (fdNow == null || System.currentTimeMillis() - started < 10000)
            ) {
                try { pfd?.close() } catch (_: Throwable) {}
                pfd = null
                val inFile = copyToCache(app, item.uri, "in${job.id}", extOf(item.name), job, temps)
                outFile.delete()
                val plan = makePlan(inFile.absolutePath)
                notifyChange()
                val r = execute(job, plan.args)
                ok = r.first
                log = r.second
            }

            if (job.cancelRequested) {
                job.state = CANCELLED
                return
            }
            if (!ok || !outFile.exists() || outFile.length() <= 0L) {
                job.state = FAILED
                job.error = log.takeLast(500)
                return
            }

            job.progress = 1f
            saveOutput(app, job, outFile, fmt)
            job.state = DONE
        } catch (e: CancelledException) {
            job.state = CANCELLED
        } catch (t: Throwable) {
            if (job.cancelRequested) {
                job.state = CANCELLED
            } else {
                job.state = FAILED
                job.error = t.toString()
            }
        } finally {
            try { pfd?.close() } catch (_: Throwable) {}
            for (f in temps) {
                try { f.delete() } catch (_: Throwable) {}
            }
        }
    }
}
