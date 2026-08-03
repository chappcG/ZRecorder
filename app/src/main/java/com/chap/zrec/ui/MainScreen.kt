package com.chap.zrec.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chap.zrec.data.PrefsManager
import com.chap.zrec.data.RecorderSettings
import com.chap.zrec.data.RecordingItem
import com.chap.zrec.data.RecordingRepository
import android.provider.MediaStore
import com.chap.zrec.service.FFmpegProcessor
import com.chap.zrec.data.UpdateChecker
import androidx.compose.material3.LinearProgressIndicator
import com.chap.zrec.service.ProcessingState
import com.chap.zrec.service.RecorderService
import com.chap.zrec.service.RecorderState
import com.chap.zrec.startRecorderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(prefs: PrefsManager, repository: RecordingRepository, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val settings by prefs.settings.collectAsState(initial = RecorderSettings())
    val recordingState by RecorderState.state.collectAsState()
    val recordings by repository.recordings.collectAsState()
    
    val updateInfo by produceState<UpdateChecker.ReleaseInfo?>(initialValue = null) {
        value = UpdateChecker.checkForUpdates(context.applicationContext, "1.0")
    }

    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var deletingItem by remember { mutableStateOf<RecordingItem?>(null) }
    var propertiesItem by remember { mutableStateOf<RecordingItem?>(null) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var dontShowAgain by remember { mutableStateOf(false) }

    LaunchedEffect(recordingState.active, recordingState.paused, recordingState.countdown) {
        if ((recordingState.active && !recordingState.paused) || recordingState.countdown > 0) {
            while (true) { now = SystemClock.elapsedRealtime(); delay(1000L) }
        }
    }
    LaunchedEffect(Unit) { repository.refresh() }
    LaunchedEffect(recordingState.active) { if (!recordingState.active) { delay(700L); repository.refresh() } }
    LaunchedEffect(updateInfo) { if (updateInfo != null) showUpdateDialog = true }

    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    val screenCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startRecorderService(context, result.resultCode, result.data!!, settings, isPortrait)
        } else {
            Toast.makeText(context, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchScreenCapture(context, projectionManager, screenCaptureLauncher)
        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (settings.audioMode != "None" && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                launchScreenCapture(context, projectionManager, screenCaptureLauncher)
            }
        } else Toast.makeText(context, "Notification permission is required", Toast.LENGTH_SHORT).show()
    }

    fun beginRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS); return
        }
        if (settings.audioMode != "None" && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        launchScreenCapture(context, projectionManager, screenCaptureLauncher)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Z Recorder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("A powerful screen recorder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { repository.refresh() } }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { beginRecording() },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                icon = { Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error) },
                text = { Text("Record", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (recordings.isEmpty()) EmptyState()
            else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recordings, key = { it.id }) { item ->
                        RecordingCard(
                            item = item, 
                            onClick = { openVideo(context, item.uri) }, 
                            onDelete = { deletingItem = item },
                            onProperties = { propertiesItem = item }
                        )
                    }
                }
            }
        }
    }

    if (recordingState.active || recordingState.countdown > 0) {
        RecordingBottomSheet(
            state = recordingState, now = now, sheetState = sheetState, onDismiss = { },
            onPauseResume = { if (recordingState.paused) sendAction(context, RecorderService.ACTION_RESUME) else sendAction(context, RecorderService.ACTION_PAUSE) },
            onStop = { sendAction(context, RecorderService.ACTION_STOP) }
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete recording?") }, text = { Text(item.displayName) },
            confirmButton = { TextButton(onClick = { val target = item; deletingItem = null; scope.launch { val deleted = repository.delete(target); repository.refresh(); Toast.makeText(context, if (deleted) "Deleted" else "Failed", Toast.LENGTH_SHORT).show() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Cancel") } }
        )
    }

    propertiesItem?.let { item ->
        PropertiesDialog(item = item, onDismiss = { propertiesItem = null })
    }

    val processing by ProcessingState.state.collectAsState()
    if (processing.active && !processing.minimized) {
        ProcessingDialog(
            fileName = processing.fileName,
            progress = processing.progress,
            onCancel = { sendAction(context, RecorderService.ACTION_CANCEL_PROCESSING) },
            onMinimize = { sendAction(context, RecorderService.ACTION_MINIMIZE) }
        )
    }

    val currentUpdateInfo = updateInfo
    if (showUpdateDialog && currentUpdateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false; if (dontShowAgain) UpdateChecker.ignoreForever(context.applicationContext) },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("Version ${currentUpdateInfo.tagName} is available.")
                    if (currentUpdateInfo.body.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(currentUpdateInfo.body, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it }); Text("Don't show this again", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(4.dp))
                    Text("You can manually check for updates in Settings > About > Version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUpdateInfo.downloadUrl))) } catch (_: Exception) {}; showUpdateDialog = false }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { if (dontShowAgain) UpdateChecker.ignoreForever(context.applicationContext) else UpdateChecker.ignoreVersion(context.applicationContext, currentUpdateInfo.tagName); showUpdateDialog = false }) { Text("Later") } }
        )
    }
}

@Composable
private fun PropertiesDialog(item: RecordingItem, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val json by produceState<org.json.JSONObject?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var path: String? = null
            var copied = false
            // Try direct path first
            context.contentResolver.query(
                item.uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val p = c.getString(0)
                    if (p != null && java.io.File(p).exists()) path = p
                }
            }
            // Fallback: copy to cache
            if (path == null) {
                val tmp = java.io.File(context.cacheDir, "probe_${'$'}{System.currentTimeMillis()}.mp4")
                try {
                    context.contentResolver.openInputStream(item.uri)?.use { i ->
                        tmp.outputStream().use { i.copyTo(it) }
                    }
                    path = tmp.absolutePath
                    copied = true
                } catch (_: Exception) {}
            }
            val result = path?.let { FFmpegProcessor.probe(it) }
            if (copied) java.io.File(path!!).delete()
            result
        }
    }

    val rows = remember(json) { buildPropertyRows(item, json) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Properties", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(rows) { (section, label, value) ->
                    if (label.isEmpty()) {
                        Text(section, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(value, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun buildPropertyRows(item: RecordingItem, json: org.json.JSONObject?): List<Triple<String, String, String>> {
    val rows = mutableListOf<Triple<String, String, String>>()
    rows += Triple("General", "", "")
    rows += Triple("", "File name", item.displayName)
    rows += Triple("", "File size", formatSize(item.size))

    if (json == null) {
        val res = "${item.width}x${item.height}"
        rows += Triple("", "Resolution", res)
        rows += Triple("", "Duration", formatDuration(item.duration))
        rows += Triple("", "Note", "Probe unavailable")
        return rows
    }

    val format = json.optJSONObject("format")
    val streams = json.optJSONArray("streams")
    var video: org.json.JSONObject? = null
    var audio: org.json.JSONObject? = null
    if (streams != null) {
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            if (video == null && s.optString("codec_type") == "video") video = s
            else if (audio == null && s.optString("codec_type") == "audio") audio = s
        }
    }

    format?.let { f ->
        val fName = f.optString("format_name", "mp4")
        rows += Triple("", "Format", if (fName.contains("mp4")) "MP4 (MPEG-4)" else fName.uppercase())
        val dur = (f.optDouble("duration", 0.0) * 1000).toLong()
        rows += Triple("", "Duration", formatDuration(dur))
        rows += Triple("", "Overall bit rate", bitrateText(f.optLong("bit_rate", 0)))
    }

    video?.let { v ->
        rows += Triple("Video", "", "")
        rows += Triple("", "Codec", codecLabel(v.optString("codec_name", "")))
        val w = v.optInt("width")
        val h = v.optInt("height")
        val res = "$w x $h"
        rows += Triple("", "Resolution", res)
        rows += Triple("", "Frame rate", fpsText(v.optString("avg_frame_rate", v.optString("r_frame_rate", ""))))
        rows += Triple("", "Bit rate", bitrateText(v.optLong("bit_rate", 0)))
    }

    audio?.let { a ->
        rows += Triple("Audio", "", "")
        rows += Triple("", "Codec", a.optString("codec_name", "aac").uppercase())
        val sr = a.optString("sample_rate", "?")
        val srText = "$sr Hz"
        rows += Triple("", "Sampling rate", srText)
        rows += Triple("", "Channel(s)", a.optString("channels", "?"))
        rows += Triple("", "Bit rate", bitrateText(a.optLong("bit_rate", 0)))
    }
    return rows
}

private fun codecLabel(name: String): String = when (name) {
    "h264" -> "H.264 (AVC)"
    "hevc" -> "H.265 (HEVC)"
    else -> name.uppercase()
}

private fun fpsText(frac: String): String {
    if (frac.isBlank()) return "Unknown"
    if (frac.contains("/")) {
        val parts = frac.split("/")
        val n = parts[0].toDoubleOrNull() ?: return "Unknown"
        val d = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        if (d <= 0.0) return "Unknown"
        val value = (n / d).toInt()
        return "$value fps"
    }
    val value = frac.toDoubleOrNull()?.toInt() ?: 0
    return "$value fps"
}

private fun bitrateText(bits: Long): String {
    if (bits <= 0) return "Unknown"
    return if (bits >= 1_000_000) {
        val mbps = bits / 1_000_000.0
        String.format(java.util.Locale.US, "%.2f Mb/s", mbps)
    } else {
        val kbps = bits / 1000
        "$kbps kb/s"
    }
}

@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.VideoFile, null, modifier = Modifier.size(84.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text("No recordings yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Tap Record to capture your screen.\nVideos are saved to Movies/ZRecorder.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecordingCard(item: RecordingItem, onClick: () -> Unit, onDelete: () -> Unit, onProperties: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(96.dp, 64.dp)) {
                AsyncImage(model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(), contentDescription = item.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(28.dp).align(Alignment.Center))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text("${formatDuration(item.duration)} • ${formatSize(item.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onProperties) { Icon(Icons.Filled.Info, "Properties", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingBottomSheet(state: RecorderState.State, now: Long, sheetState: androidx.compose.material3.SheetState, onDismiss: () -> Unit, onPauseResume: () -> Unit, onStop: () -> Unit) {
    val pulse by animateFloatAsState(targetValue = if (state.active && !state.paused) 1f else 0.72f, animationSpec = tween(durationMillis = 650), label = "pulse")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = state.countdown > 0, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Starting in", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("${state.countdown}s", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator()
                }
            }
            AnimatedVisibility(visible = state.countdown == 0, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.FiberManualRecord, null, tint = if (state.paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp).scale(pulse)) }
                    Spacer(Modifier.height(10.dp))
                    Text(if (state.paused) "Paused" else "Recording", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(formatDuration(state.elapsed(now)), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = onPauseResume) { Icon(if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null); Spacer(Modifier.width(8.dp)); Text(if (state.paused) "Resume" else "Pause") }
                        Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop") }
                    }
                }
            }
        }
    }
}

private fun launchScreenCapture(context: Context, projectionManager: MediaProjectionManager, launcher: ActivityResultLauncher<Intent>) { try { launcher.launch(projectionManager.createScreenCaptureIntent()) } catch (e: Exception) { Toast.makeText(context, "Cannot request screen capture", Toast.LENGTH_SHORT).show() } }
private fun sendAction(context: Context, action: String) { context.startService(Intent(context, RecorderService::class.java).apply { this.action = action }) }
private fun openVideo(context: Context, uri: Uri) { try { context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "video/mp4"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) } catch (e: ActivityNotFoundException) { Toast.makeText(context, "No player found", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, "Cannot open video", Toast.LENGTH_SHORT).show() } }
private fun formatDuration(ms: Long): String { val totalSeconds = ms / 1000L; val hours = totalSeconds / 3600L; val minutes = (totalSeconds % 3600L) / 60L; val seconds = totalSeconds % 60L; return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds) else String.format(Locale.US, "%02d:%02d", minutes, seconds) }
private fun formatSize(bytes: Long): String = when { bytes >= 1_073_741_824 -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0); bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0); bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0); else -> "$bytes B" }

@Composable
private fun ProcessingDialog(
    fileName: String,
    progress: Int,
    onCancel: () -> Unit,
    onMinimize: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onMinimize,
        title = { Text("Processing video", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Re-encoding with FFmpeg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$progress%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onMinimize) { Text("Minimize") }
        }
    )
}
