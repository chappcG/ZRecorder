package com.chap.zrec.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "zrec_settings")

data class RecorderSettings(
    val resolution: String = "1280x720",
    val fps: Int = 30,
    val videoBitrateMbps: Float = 8f,
    val videoEncoder: String = "H.264",
    val orientation: String = "Auto",
    val countdown: Int = 0,
    val audioMode: String = "Microphone", // "None", "Microphone", "Internal", "Internal+Mic"
    val audioBitrate: Int = 128_000,
    val filePrefix: String = "ZREC"
) {
    val width: Int get() = resolution.substringBefore('x').toIntOrNull() ?: 1280
    val height: Int get() = resolution.substringAfter('x').toIntOrNull() ?: 720
    val videoBitrate: Int get() = (videoBitrateMbps * 1_000_000f).toInt().coerceIn(1_000_000, 38_000_000)
}

class PrefsManager(private val context: Context) {
    private companion object {
        val KEY_RESOLUTION = stringPreferencesKey("resolution")
        val KEY_FPS = intPreferencesKey("fps")
        val KEY_VIDEO_BITRATE_MBPS = stringPreferencesKey("video_bitrate_mbps")
        val KEY_VIDEO_ENCODER = stringPreferencesKey("video_encoder")
        val KEY_ORIENTATION = stringPreferencesKey("orientation")
        val KEY_COUNTDOWN = intPreferencesKey("countdown")
        val KEY_AUDIO_MODE = stringPreferencesKey("audio_mode")
        val KEY_AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        val KEY_FILE_PREFIX = stringPreferencesKey("file_prefix")
    }

    val settings: Flow<RecorderSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            RecorderSettings(
                resolution = prefs[KEY_RESOLUTION] ?: "1280x720",
                fps = prefs[KEY_FPS] ?: 30,
                videoBitrateMbps = prefs[KEY_VIDEO_BITRATE_MBPS]?.toFloatOrNull() ?: 8f,
                videoEncoder = prefs[KEY_VIDEO_ENCODER] ?: "H.264",
                orientation = prefs[KEY_ORIENTATION] ?: "Auto",
                countdown = prefs[KEY_COUNTDOWN] ?: 0,
                audioMode = prefs[KEY_AUDIO_MODE] ?: "Microphone",
                audioBitrate = prefs[KEY_AUDIO_BITRATE] ?: 128_000,
                filePrefix = prefs[KEY_FILE_PREFIX] ?: "ZREC"
            )
        }

    suspend fun setResolution(v: String) { context.dataStore.edit { it[KEY_RESOLUTION] = v } }
    suspend fun setFps(v: Int) { context.dataStore.edit { it[KEY_FPS] = v } }
    suspend fun setVideoBitrateMbps(v: Float) { context.dataStore.edit { it[KEY_VIDEO_BITRATE_MBPS] = v.coerceIn(1f, 38f).toString() } }
    suspend fun setVideoEncoder(v: String) { context.dataStore.edit { it[KEY_VIDEO_ENCODER] = v } }
    suspend fun setOrientation(v: String) { context.dataStore.edit { it[KEY_ORIENTATION] = v } }
    suspend fun setCountdown(v: Int) { context.dataStore.edit { it[KEY_COUNTDOWN] = v } }
    suspend fun setAudioMode(v: String) { context.dataStore.edit { it[KEY_AUDIO_MODE] = v } }
    suspend fun setAudioBitrate(v: Int) { context.dataStore.edit { it[KEY_AUDIO_BITRATE] = v } }
    suspend fun setFilePrefix(v: String) { context.dataStore.edit { it[KEY_FILE_PREFIX] = v } }
    suspend fun reset() { context.dataStore.edit { it.clear() } }
}
