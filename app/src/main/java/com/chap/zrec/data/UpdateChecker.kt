package com.chap.zrec.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val PREFS_NAME = "zrec_update_prefs"
    private const val KEY_HIDE_VERSION = "hide_version"
    private const val KEY_HIDE_FOREVER = "hide_forever"

    data class ReleaseInfo(
        val tagName: String,
        val downloadUrl: String,
        val body: String
    )

    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        val current = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val latest = latestVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(current.size, latest.size)) {
            val c = current.getOrElse(i) { 0 }
            val l = latest.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun checkForUpdates(context: Context, currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/chappcG/ZRecorder/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "ZRecorder-App")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                val body = json.optString("body", "")

                val assets = json.getJSONArray("assets")
                var apkUrl = ""
                if (assets.length() > 0) {
                    val firstAsset = assets.getJSONObject(0)
                    apkUrl = firstAsset.getString("browser_download_url")
                }

                if (apkUrl.isNotEmpty() && isUpdateAvailable(currentVersion, tagName)) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val hideForever = prefs.getBoolean(KEY_HIDE_FOREVER, false)
                    val hideVersion = prefs.getString(KEY_HIDE_VERSION, "")

                    if (!hideForever && hideVersion != tagName) {
                        return@withContext ReleaseInfo(tagName, apkUrl, body)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore network errors
        }
        return@withContext null
    }

    fun ignoreVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HIDE_VERSION, version).apply()
    }

    fun ignoreForever(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_FOREVER, true).apply()
    }
}
