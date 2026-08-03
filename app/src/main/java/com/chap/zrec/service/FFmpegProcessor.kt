package com.chap.zrec.service

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object FFmpegProcessor {

    private const val TAG = "FFmpegProcessor"

    enum class EncodeResult { SUCCESS, CANCELLED, FAILED }

    data class EncodeConfig(
        val inputPath: String,
        val outputPath: String,
        val videoCodec: String,
        val videoBitrateKbps: Int,
        val audioBitrateKbps: Int,
        val fps: Int,
        val width: Int,
        val height: Int
    )

    suspend fun encode(
        config: EncodeConfig,
        durationMs: Long,
        onProgress: (Int) -> Unit
    ): EncodeResult = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<EncodeResult>()

        val cmd = buildString {
            append("-y -i \"").append(config.inputPath).append("\" ")
            append("-c:v ").append(config.videoCodec).append(" ")
            append("-preset veryfast ")
            append("-pix_fmt yuv420p ")
            append("-b:v ").append(config.videoBitrateKbps).append("k ")
            append("-maxrate ").append(config.videoBitrateKbps).append("k ")
            append("-bufsize ").append(config.videoBitrateKbps * 2).append("k ")
            append("-r ").append(config.fps).append(" ")
            append("-s ").append(config.width).append("x").append(config.height).append(" ")
            append("-c:a aac ")
            append("-b:a ").append(config.audioBitrateKbps).append("k ")
            append("-movflags +faststart ")
            append("\"").append(config.outputPath).append("\"")
        }

        Log.d(TAG, "FFmpeg cmd: $cmd")

        FFmpegKit.executeAsync(
            cmd,
            { session ->
                deferred.complete(when {
                    ReturnCode.isSuccess(session.returnCode) -> EncodeResult.SUCCESS
                    ReturnCode.isCancel(session.returnCode) -> EncodeResult.CANCELLED
                    else -> {
                        Log.e(TAG, "FFmpeg failed: ${session.output}")
                        EncodeResult.FAILED
                    }
                })
            },
            { log -> Log.d(TAG, "ffmpeg: ${log.message}") },
            { stats ->
                if (durationMs > 0) {
                    val p = ((stats.time * 100) / durationMs).toInt().coerceIn(0, 99)
                    onProgress(p)
                }
            }
        )

        deferred.await()
    }

    fun cancel() {
        Log.d(TAG, "Cancelling FFmpeg session")
        FFmpegKit.cancel()
    }

    suspend fun probe(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val deferred = CompletableDeferred<Boolean>()

        FFprobeKit.executeAsync(
            "-v quiet -print_format json -show_format -show_streams \"$path\"",
            { session -> deferred.complete(ReturnCode.isSuccess(session.returnCode)) },
            { log -> sb.append(log.message) }
        )

        if (deferred.await()) runCatching { JSONObject(sb.toString()) }.getOrNull() else null
    }
}
