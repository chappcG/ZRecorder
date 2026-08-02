package com.chap.zrec.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chap.zrec.data.PrefsManager
import com.chap.zrec.data.RecorderOptions
import com.chap.zrec.data.RecorderSettings
import kotlinx.coroutines.launch

private enum class SettingsDialog { NONE, RESOLUTION, FPS, ENCODER, ORIENTATION, COUNTDOWN, AUDIO_MODE, AUDIO_BITRATE, BITRATE_INPUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: PrefsManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by prefs.settings.collectAsState(initial = RecorderSettings())
    var openDialog by remember { mutableStateOf(SettingsDialog.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionTitle("Video") }
            item { OptionRow("Resolution", RecorderOptions.resolutionLabel(settings.resolution)) { openDialog = SettingsDialog.RESOLUTION } }
            item { OptionRow("Frame rate", "${settings.fps} fps") { openDialog = SettingsDialog.FPS } }
            item { OptionRow("Bitrate", RecorderOptions.bitrateLabel(settings.videoBitrateMbps)) { openDialog = SettingsDialog.BITRATE_INPUT } }
            item { OptionRow("Encoder", settings.videoEncoder) { openDialog = SettingsDialog.ENCODER } }
            item { OptionRow("Orientation", settings.orientation) { openDialog = SettingsDialog.ORIENTATION } }
            item { OptionRow("Countdown", if (settings.countdown == 0) "Off" else "${settings.countdown}s") { openDialog = SettingsDialog.COUNTDOWN } }
            
            item { SectionTitle("Audio") }
            item { OptionRow("Audio source", settings.audioMode) { openDialog = SettingsDialog.AUDIO_MODE } }
            if (settings.audioMode != "None") {
                item { OptionRow("Audio bitrate", RecorderOptions.audioBitrateLabel(settings.audioBitrate)) { openDialog = SettingsDialog.AUDIO_BITRATE } }
            }
            
            item { SectionTitle("File") }
            item { PrefixRow(settings.filePrefix) { newValue -> scope.launch { prefs.setFilePrefix(newValue) } } }
            
            item { SectionTitle("About") }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Z Recorder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Version: 1.0", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Author: chappcG", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    val localContext = LocalContext.current
                    Text(
                        text = "GitHub: github.com/chappcG/ZRecorder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            try { localContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/chappcG/ZRecorder"))) } catch (_: Exception) {}
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Videos are saved to Movies/ZRecorder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { TextButton(onClick = { scope.launch { prefs.reset() } }, modifier = Modifier.padding(16.dp)) { Text("Reset settings", color = MaterialTheme.colorScheme.error) } }
        }
    }

    when (openDialog) {
        SettingsDialog.RESOLUTION -> RadioDialog("Resolution", RecorderOptions.resolutions.map { it.second }, RecorderOptions.resolutionLabel(settings.resolution), { label -> val value = RecorderOptions.resolutions.firstOrNull { it.second == label }?.first ?: settings.resolution; scope.launch { prefs.setResolution(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.FPS -> RadioDialog("Frame rate", RecorderOptions.frameRates.map { "$it fps" }, "${settings.fps} fps", { label -> val value = label.removeSuffix(" fps").toIntOrNull() ?: settings.fps; scope.launch { prefs.setFps(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.ENCODER -> RadioDialog("Encoder", RecorderOptions.videoEncoders, settings.videoEncoder, { value -> scope.launch { prefs.setVideoEncoder(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.ORIENTATION -> RadioDialog("Orientation", RecorderOptions.orientations, settings.orientation, { value -> scope.launch { prefs.setOrientation(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.COUNTDOWN -> RadioDialog("Countdown", RecorderOptions.countdowns.map { if (it == 0) "Off" else "${it}s" }, if (settings.countdown == 0) "Off" else "${settings.countdown}s", { label -> val value = if (label == "Off") 0 else label.removeSuffix("s").toIntOrNull() ?: 0; scope.launch { prefs.setCountdown(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.AUDIO_MODE -> AudioModeDialog(settings.audioMode, { value -> scope.launch { prefs.setAudioMode(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.AUDIO_BITRATE -> RadioDialog("Audio bitrate", RecorderOptions.audioBitrates.map { RecorderOptions.audioBitrateLabel(it) }, RecorderOptions.audioBitrateLabel(settings.audioBitrate), { label -> val value = RecorderOptions.audioBitrates.firstOrNull { RecorderOptions.audioBitrateLabel(it) == label } ?: settings.audioBitrate; scope.launch { prefs.setAudioBitrate(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.BITRATE_INPUT -> BitrateInputDialog(settings.videoBitrateMbps, { value -> scope.launch { prefs.setVideoBitrateMbps(value) } }, { openDialog = SettingsDialog.NONE })
        SettingsDialog.NONE -> Unit
    }
}

@Composable
private fun AudioModeDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val isQOrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio source") },
        text = {
            Column {
                RecorderOptions.audioModes.forEach { mode ->
                    val isInternal = mode == "Internal Audio" || mode == "Internal + Microphone"
                    val enabled = !isInternal || isQOrAbove
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onSelect(mode); onDismiss() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == mode, onClick = if (enabled) { { onSelect(mode); onDismiss() } } else null)
                        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(mode, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                            if (isInternal && !isQOrAbove) {
                                Text("Requires Android 10+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SectionTitle(text: String) { Text(text, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
@Composable
private fun OptionRow(title: String, value: String, onClick: () -> Unit) { ListItem(headlineContent = { Text(title) }, supportingContent = { Text(value) }, modifier = Modifier.clickable(onClick = onClick)) }
@Composable
private fun PrefixRow(value: String, onChange: (String) -> Unit) { Column(modifier = Modifier.padding(16.dp)) { OutlinedTextField(value = value, onValueChange = onChange, label = { Text("File prefix") }, singleLine = true, modifier = Modifier.fillMaxWidth()) } }

@Composable
private fun RadioDialog(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { options.forEach { option -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(option); onDismiss() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = option == selected, onClick = { onSelect(option); onDismiss() }); Text(option, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge) } } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BitrateInputDialog(current: Float, onSave: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(com.chap.zrec.data.RecorderOptions.trim(current)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video bitrate") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue -> val filtered = newValue.filter { it.isDigit() || it == '.' }; if (filtered.count { it == '.' } <= 1) { text = filtered; error = null } },
                    label = { Text("Mbps") }, supportingText = { Text("Allowed range: 1 - 38 Mbps") }, isError = error != null, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { val value = text.toFloatOrNull(); when { value == null -> error = "Enter a valid number"; value < 1f -> error = "Minimum is 1 Mbps"; value > 38f -> error = "Maximum is 38 Mbps"; else -> { onSave(value); onDismiss() } } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
