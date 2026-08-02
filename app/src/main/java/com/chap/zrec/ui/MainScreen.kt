package com.chap.zrec.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
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
import com.chap.zrec.data.UpdateChecker
import com.chap.zrec.service.RecorderService
import com.chap.zrec.service.RecorderState
import com.chap.zrec.startRecorderService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import java.util.Locale

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
                        Text("Material You screen recorder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val props by produceState<Triple<Int, Int, Long>?>(null) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                Triple(w, h, d)
            } catch (_: Exception) { null }
            finally { runCatching { retriever.release() } }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Properties") },
        text = {
            Column {
                Text("File: ${item.displayName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Size: ${formatSize(item.size)}", style = MaterialTheme.typography.bodyMedium)
                if (props != null) {
                    Text("Resolution: ${props!!.first} x ${props!!.second}", style = MaterialTheme.typography.bodyMedium)
                    Text("Duration: ${formatDuration(props!!.third)}", style = MaterialTheme.typography.bodyMedium)
                    val durationSec = props!!.third / 1000f
                    if (durationSec > 0) {
                        val bitrateKbps = (item.size * 8) / durationSec / 1000f
                        Text("Avg Bitrate: ~${String.format(Locale.US, "%.1f", bitrateKbps)} kbps", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("Duration: ${formatDuration(item.duration)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Resolution: Unable to read", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
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
