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

    data class EncodeConfig(
        val inputPath: String,
        val outputPath: String,
        val videoCodec: String,      // "libx264" or "libx265"
        val videoBitrateKbps: Int,
        val audioBitrateKbps: Int,
        val fps: Int,
        val width: Int,
        val height: Int
    )

    /** Re-encode with EXACT bitrate + constant frame rate. */
    suspend fun encode(config: EncodeConfig): Boolean = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Boolean>()

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
            append("-ar 44100 -ac 1 ")
            append("-movflags +faststart ")
            append("\"").append(config.outputPath).append("\"")
        }

        Log.d(TAG, "FFmpeg cmd: $cmd")

        FFmpegKit.executeAsync(cmd) { session ->
            val ok = ReturnCode.isSuccess(session.returnCode)
            if (!ok) Log.e(TAG, "FFmpeg failed: ${session.output}")
            deferred.complete(ok)
        }

        deferred.await()
    }

    /** Read REAL file properties with FFprobe (returns raw JSON). */
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
