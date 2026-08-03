package com.chap.zrec.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FFmpegProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "FFmpegProcessor"
    }
    
    data class EncodeConfig(
        val inputPath: String,
        val outputPath: String,
        val videoCodec: String = "libx265", // or libx264
        val videoBitrate: Int, // in kbps
        val audioCodec: String = "aac",
        val audioBitrate: Int = 128, // in kbps
        val fps: Int = 30,
        val width: Int,
        val height: Int,
        val preset: String = "medium" // ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow
    )
    
    suspend fun encode(config: EncodeConfig): Boolean = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Boolean>()
        
        // Build FFmpeg command
        val command = buildString {
            append("-i \"${config.inputPath}\" ")
            append("-c:v ${config.videoCodec} ")
            append("-b:v ${config.videoBitrate}k ")
            append("-r ${config.fps} ")
            append("-s ${config.width}x${config.height} ")
            append("-preset ${config.preset} ")
            append("-c:a ${config.audioCodec} ")
            append("-b:a ${config.audioBitrate}k ")
            append("-movflags +faststart ")
            append("-y \"${config.outputPath}\"")
        }
        
        Log.d(TAG, "FFmpeg command: $command")
        
        FFmpegKit.executeAsync(command, { session ->
            when {
                ReturnCode.isSuccess(session.returnCode) -> {
                    Log.d(TAG, "FFmpeg encoding successful")
                    deferred.complete(true)
                }
                ReturnCode.isCancel(session.returnCode) -> {
                    Log.e(TAG, "FFmpeg encoding cancelled")
                    deferred.complete(false)
                }
                else -> {
                    Log.e(TAG, "FFmpeg encoding failed: ${session.failStackTrace}")
                    deferred.complete(false)
                }
            }
        }, { log ->
            Log.d(TAG, "FFmpeg log: ${log.message}")
        }, { statistics ->
            Log.d(TAG, "FFmpeg stats: ${statistics.toString()}")
        })
        
        deferred.await()
    }
    
    suspend fun getVideoInfo(inputPath: String): VideoInfo? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<VideoInfo?>()
        
        val command = "-i \"$inputPath\" -f null -"
        
        FFmpegKit.executeAsync(command, { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                // Parse output to get info
                deferred.complete(null) // Simplified for now
            } else {
                deferred.complete(null)
            }
        }, null, null)
        
        deferred.await()
    }
    
    data class VideoInfo(
        val duration: Long,
        val bitrate: Long,
        val fps: Double,
        val width: Int,
        val height: Int
    )
}
