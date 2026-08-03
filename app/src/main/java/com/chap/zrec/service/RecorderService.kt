package com.chap.zrec.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.chap.zrec.MainActivity
import com.chap.zrec.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.chap.zrec.action.START"
        const val ACTION_PAUSE = "com.chap.zrec.action.PAUSE"
        const val ACTION_RESUME = "com.chap.zrec.action.RESUME"
        const val ACTION_STOP = "com.chap.zrec.action.STOP"
        const val ACTION_CANCEL_PROCESSING = "com.chap.zrec.action.CANCEL_PROCESSING"
        const val ACTION_MINIMIZE = "com.chap.zrec.action.MINIMIZE"
        const val ACTION_SHOW_PROCESSING = "com.chap.zrec.action.SHOW_PROCESSING"

        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_VIDEO_ENCODER = "extra_video_encoder"
        const val EXTRA_ORIENTATION = "extra_orientation"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val EXTRA_AUDIO_MODE = "extra_audio_mode"
        const val EXTRA_AUDIO_BITRATE = "extra_audio_bitrate"
        const val EXTRA_FILE_PREFIX = "extra_file_prefix"
        const val EXTRA_IS_PORTRAIT = "extra_is_portrait"

        private const val CHANNEL_ID = "zrec_recording_channel"
        private const val NOTIFICATION_ID = 2001
        private const val PROCESSING_NOTIFICATION_ID = 2002
        private const val REQ_CONTENT = 100
        private const val REQ_STOP = 101
        private const val REQ_PAUSE = 102
        private const val REQ_RESUME = 103
        private const val REQ_CANCEL = 104
        private const val REQ_SHOW = 105
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var outputFd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tempFile: File? = null

    private var isRecording = false
    private var isPaused = false
    private var isStopping = false
    private var isCountingDown = false

    private var startedAt = 0L
    private var pausedAt = 0L
    private var accumulatedPause = 0L
    private var recordedDurationMs = 0L
    private var currentDisplayName = ""

    private var resultCode = 0
    private var resultData: Intent? = null

    private var width = 1280
    private var height = 720
    private var fps = 30
    private var videoBitrate = 8_000_000
    private var videoEncoderName = "H.264"
    private var orientation = "Auto"
    private var countdown = 0
    private var audioMode = "Microphone"
    private var audioBitrate = 128_000
    private var filePrefix = "ZREC"
    private var isPortraitSetting = false

    private var countdownValue = 0
    private var outWidth = 1280
    private var outHeight = 720

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private var audioBufferSize = 4096

    private val isRunning = AtomicBoolean(false)
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { handler.post { stopRecording(fromProjection = true) } }
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!isCountingDown) return
            if (countdownValue <= 0) {
                isCountingDown = false
                RecorderState.setCountdown(0)
                startRecording()
            } else {
                RecorderState.setCountdown(countdownValue)
                showNotification("Z Recorder", "Starting in ${countdownValue}s")
                countdownValue--
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        RecorderState.setInactive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (isRecording || isCountingDown) return START_NOT_STICKY

                val cachedResultCode = MediaProjectionCache.resultCode
                val cachedData = MediaProjectionCache.data
                if (cachedResultCode == 0 || cachedData == null) {
                    startForegroundWithNotification("Z Recorder", "Recording cancelled")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }

                resultCode = cachedResultCode
                resultData = cachedData
                width = intent.getIntExtra(EXTRA_WIDTH, 1280)
                height = intent.getIntExtra(EXTRA_HEIGHT, 720)
                fps = intent.getIntExtra(EXTRA_FPS, 30)
                videoBitrate = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 8_000_000)
                videoEncoderName = intent.getStringExtra(EXTRA_VIDEO_ENCODER) ?: "H.264"
                orientation = intent.getStringExtra(EXTRA_ORIENTATION) ?: "Auto"
                countdown = intent.getIntExtra(EXTRA_COUNTDOWN, 0)
                audioMode = intent.getStringExtra(EXTRA_AUDIO_MODE) ?: "Microphone"
                audioBitrate = intent.getIntExtra(EXTRA_AUDIO_BITRATE, 128_000)
                filePrefix = intent.getStringExtra(EXTRA_FILE_PREFIX) ?: "ZREC"
                isPortraitSetting = intent.getBooleanExtra(EXTRA_IS_PORTRAIT, false)

                acquireWakeLock()
                startForegroundWithNotification("Z Recorder", if (countdown > 0) "Starting in ${countdown}s" else "Preparing...")

                if (countdown > 0) {
                    isCountingDown = true
                    countdownValue = countdown
                    handler.post(countdownRunnable)
                } else {
                    startRecording()
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_CANCEL_PROCESSING -> FFmpegProcessor.cancel()
            ACTION_MINIMIZE -> ProcessingState.minimize()
            ACTION_SHOW_PROCESSING -> ProcessingState.show()
            else -> if (!isRecording && !isCountingDown) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isRecording || isStopping) return
        val data = resultData ?: run { stopRecording(); return }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, data) ?: throw IOException("MediaProjection unavailable")
            mediaProjection = projection
            projection.registerCallback(projectionCallback, handler)

            val long = maxOf(width, height)
            val short = minOf(width, height)
            val isPortrait = when (orientation) {
                "Portrait" -> true
                "Landscape" -> false
                else -> isPortraitSetting
            }
            outWidth = if (isPortrait) short else long
            outHeight = if (isPortrait) long else short

            tempFile = File(cacheDir, "zrec_temp_${System.currentTimeMillis()}.mp4")
            outputFd = ParcelFileDescriptor.open(
                tempFile!!,
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            )

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safePrefix = filePrefix.trim().ifEmpty { "ZREC" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
            currentDisplayName = "${safePrefix}_$timestamp.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, currentDisplayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ZRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create file")

            muxer = MediaMuxer(outputFd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val mimeType = if (videoEncoderName == "H.265" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

            val videoFormat = MediaFormat.createVideoFormat(mimeType, outWidth, outHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, maxOf(videoBitrate, 20_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoEncoder = MediaCodec.createEncoderByType(mimeType).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = videoEncoder!!.createInputSurface()

            if (audioMode != "None") {
                try {
                    val sampleRate = 48000
                    val channelConfig = AudioFormat.CHANNEL_IN_MONO
                    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                    audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

                    audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        (audioMode == "Internal Audio" || audioMode == "Internal + Microphone")) {
                        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build()
                        AudioRecord.Builder()
                            .setAudioPlaybackCaptureConfig(config)
                            .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(channelConfig).build())
                            .setBufferSizeInBytes(audioBufferSize)
                            .build()
                    } else {
                        AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, audioBufferSize)
                    }

                    val audioMediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                        setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
                    }
                    audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                        configure(audioMediaFormat, null, null, 0)
                    }
                } catch (e: Exception) {
                    Log.e("ZRecorder", "Audio init failed: ${e.message}")
                    audioRecord = null
                    audioEncoder = null
                }
            }

            virtualDisplay = projection.createVirtualDisplay(
                "ZRecorder", outWidth, outHeight, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null
            )

            videoEncoder!!.start()
            audioEncoder?.start()
            audioRecord?.startRecording()

            isRunning.set(true)
            muxerStarted = false
            videoThread = Thread { drainVideo() }.apply { start() }
            if (audioEncoder != null && audioRecord != null) {
                audioThread = Thread { drainAudio() }.apply { start() }
            }

            isRecording = true
            isPaused = false
            startedAt = SystemClock.elapsedRealtime()
            accumulatedPause = 0L
            pausedAt = 0L
            RecorderState.setActive(startedAt)
            showNotification("Z Recorder", "Recording", chronometer = true, whenMillis = SystemClock.elapsedRealtime())
        } catch (e: Exception) {
            Log.e("ZRecorder", "startRecording failed: ${e.message}", e)
            cleanupFailedOutput()
            releaseAll()
            mediaProjection?.let { runCatching { it.unregisterCallback(projectionCallback) }; runCatching { it.stop() } }
            mediaProjection = null
            RecorderState.setInactive()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun drainVideo() {
        val bufferInfo = MediaCodec.BufferInfo()
        var sawEOS = false
        while (isRunning.get() && !sawEOS) {
            if (isPaused) { Thread.sleep(50); continue }
            val status = videoEncoder!!.dequeueOutputBuffer(bufferInfo, 10000)
            if (status >= 0) {
                if (!muxerStarted) {
                    videoTrackIndex = muxer!!.addTrack(videoEncoder!!.getOutputFormat(status))
                    if (audioEncoder == null) { muxer!!.start(); muxerStarted = true }
                }
                if (muxerStarted && bufferInfo.size > 0) {
                    muxer?.writeSampleData(videoTrackIndex, videoEncoder!!.getOutputBuffer(status)!!, bufferInfo)
                }
                sawEOS = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                videoEncoder!!.releaseOutputBuffer(status, false)
            }
        }
    }

    private fun drainAudio() {
        val bufferInfo = MediaCodec.BufferInfo()
        val recorder = audioRecord ?: return
        val encoder = audioEncoder ?: return
        val buffer = ByteArray(audioBufferSize)
        var sawEOS = false

        while (isRunning.get() && !sawEOS) {
            if (isPaused) { Thread.sleep(50); continue }
            val read = recorder.read(buffer, 0, audioBufferSize)
            if (read > 0) {
                val inIdx = encoder.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    encoder.getInputBuffer(inIdx)?.put(buffer, 0, read)
                    encoder.queueInputBuffer(inIdx, 0, read, System.nanoTime() / 1000, 0)
                }
                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outIdx >= 0) {
                    if (!muxerStarted) {
                        audioTrackIndex = muxer!!.addTrack(encoder.getOutputFormat(outIdx))
                        if (videoTrackIndex >= 0) { muxer!!.start(); muxerStarted = true }
                    }
                    if (muxerStarted && bufferInfo.size > 0) {
                        muxer?.writeSampleData(audioTrackIndex, encoder.getOutputBuffer(outIdx)!!, bufferInfo)
                    }
                    sawEOS = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(outIdx, false)
                }
            }
        }
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return
        isPaused = true
        pausedAt = SystemClock.elapsedRealtime()
        RecorderState.setPaused(pausedAt)
        showNotification("Z Recorder", "Paused • ${formatElapsed(elapsedMillis())}")
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return
        isPaused = false
        accumulatedPause += SystemClock.elapsedRealtime() - pausedAt
        pausedAt = 0L
        RecorderState.setResumed(accumulatedPause)
        showNotification("Z Recorder", "Recording", chronometer = true, whenMillis = SystemClock.elapsedRealtime())
    }

    private fun stopRecording(fromProjection: Boolean = false) {
        if (isStopping) return
        isStopping = true
        isCountingDown = false
        handler.removeCallbacks(countdownRunnable)
        isRunning.set(false)

        runCatching { videoEncoder?.signalEndOfInputStream() }
        runCatching {
            audioEncoder?.dequeueInputBuffer(10000)?.let { idx ->
                if (idx >= 0) audioEncoder?.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }

        videoThread?.join(2000)
        audioThread?.join(2000)

        val wasRecording = isRecording
        recordedDurationMs = elapsedMillis()
        isRecording = false
        isPaused = false
        RecorderState.setInactive()
        releaseAll()
        mediaProjection?.let { runCatching { it.unregisterCallback(projectionCallback) }; if (!fromProjection) runCatching { it.stop() } }
        mediaProjection = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (wasRecording) {
            finalizeWithFFmpeg()
        } else {
            cleanupFailedOutput()
            stopSelf()
        }
        isStopping = false
    }

    private fun finalizeWithFFmpeg() {
        runCatching { if (muxerStarted) muxer?.stop() }
        runCatching { muxer?.release() }
        runCatching { outputFd?.close() }
        outputFd = null
        muxer = null

        ProcessingState.start(currentDisplayName)

        serviceScope.launch {
            val collector: Job = launch {
                ProcessingState.state.collect { st ->
                    if (st.active && st.minimized) showProcessingNotification(st.progress)
                }
            }

            val temp = tempFile
            val uri = outputUri
            var cancelled = false

            if (temp != null && temp.exists() && temp.length() > 0 && uri != null) {
                val finalFile = File(cacheDir, "zrec_final_${System.currentTimeMillis()}.mp4")
                val result = FFmpegProcessor.encode(
                    FFmpegProcessor.EncodeConfig(
                        inputPath = temp.absolutePath,
                        outputPath = finalFile.absolutePath,
                        videoCodec = if (videoEncoderName == "H.265") "libx265" else "libx264",
                        videoBitrateKbps = (videoBitrate / 1000).coerceAtLeast(1000),
                        audioBitrateKbps = (audioBitrate / 1000).coerceAtLeast(64),
                        fps = fps,
                        width = outWidth,
                        height = outHeight
                    ),
                    recordedDurationMs
                ) { p -> ProcessingState.progress(p) }

                cancelled = result == FFmpegProcessor.EncodeResult.CANCELLED
                Log.d("ZRecorder", "FFmpeg result=$result")

                val source = if (result == FFmpegProcessor.EncodeResult.SUCCESS && finalFile.exists() && finalFile.length() > 0) finalFile else temp
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        FileInputStream(source).use { it.copyTo(out) }
                    }
                }
                runCatching { finalFile.delete() }
                runCatching { temp.delete() }
            } else {
                runCatching { temp?.delete() }
            }

            if (uri != null) {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                runCatching { contentResolver.update(uri, values, null, null) }
            }

            collector.cancel()
            val wasMinimized = ProcessingState.state.value.minimized
            ProcessingState.finish()
            cancelProcessingNotification()

            if (wasMinimized) {
                postResultNotification(
                    when {
                        cancelled -> "Processing cancelled - original video saved"
                        else -> "Video saved to Movies/ZRecorder"
                    }
                )
            }

            tempFile = null
            outputUri = null
            if (!isRecording) stopSelf()
        }
    }

    private fun showProcessingNotification(progress: Int) {
        createChannel()
        val cancelIntent = PendingIntent.getService(
            this, REQ_CANCEL,
            Intent(this, RecorderService::class.java).apply { action = ACTION_CANCEL_PROCESSING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            this, REQ_SHOW,
            Intent(this, MainActivity::class.java).apply {
                putExtra("show_processing", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Processing video")
            .setContentText("$progress% - re-encoding with FFmpeg")
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(showIntent)
            .addAction(android.R.drawable.ic_menu_view, "Show", showIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
        try { NotificationManagerCompat.from(this).notify(PROCESSING_NOTIFICATION_ID, n) } catch (_: Exception) {}
    }

    private fun cancelProcessingNotification() {
        try { NotificationManagerCompat.from(this).cancel(PROCESSING_NOTIFICATION_ID) } catch (_: Exception) {}
    }

    private fun postResultNotification(text: String) {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this, REQ_CONTENT,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Z Recorder")
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
        try { NotificationManagerCompat.from(this).notify(PROCESSING_NOTIFICATION_ID, n) } catch (_: Exception) {}
    }

    private fun cleanupFailedOutput() {
        runCatching { outputFd?.close() }
        runCatching { tempFile?.delete() }
        outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
        outputFd = null; outputUri = null; tempFile = null
    }

    private fun releaseAll() {
        runCatching { virtualDisplay?.release() }
        runCatching { videoEncoder?.stop(); videoEncoder?.release() }
        runCatching { audioEncoder?.stop(); audioEncoder?.release() }
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        virtualDisplay = null; videoEncoder = null; audioEncoder = null; audioRecord = null
    }

    private fun elapsedMillis(): Long = RecorderState.state.value.elapsed(SystemClock.elapsedRealtime())

    private fun acquireWakeLock() {
        if (wakeLock == null) runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZRecorder::Recording").apply {
                setReferenceCounted(false); acquire()
            }
        }
    }

    private fun releaseWakeLock() { runCatching { wakeLock?.release() }; wakeLock = null }

    private fun startForegroundWithNotification(title: String, text: String) {
        createChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(title, text, false, 0L), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun showNotification(title: String, text: String, chronometer: Boolean = false, whenMillis: Long = 0L) {
        createChannel()
        try { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(title, text, chronometer, whenMillis)) } catch (_: Exception) {}
    }

    private fun buildNotification(title: String, text: String, chronometer: Boolean, whenMillis: Long): Notification {
        val contentIntent = PendingIntent.getActivity(this, REQ_CONTENT,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, REQ_STOP,
            Intent(this, RecorderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setOnlyAlertOnce(true).setOngoing(true).setSilent(true)
            .setContentIntent(contentIntent)
        if (chronometer) builder.setUsesChronometer(true).setWhen(whenMillis)
        if (isRecording) {
            if (isPaused) {
                builder.addAction(android.R.drawable.ic_media_play, "Resume", PendingIntent.getService(this, REQ_RESUME,
                    Intent(this, RecorderService::class.java).apply { action = ACTION_RESUME },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } else {
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", PendingIntent.getService(this, REQ_PAUSE,
                    Intent(this, RecorderService::class.java).apply { action = ACTION_PAUSE },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
        return builder.build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows recording controls"; setShowBadge(false)
        })
    }

    private fun formatElapsed(ms: Long): String {
        val t = ms / 1000; val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }
}
