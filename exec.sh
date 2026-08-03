#!/usr/bin/env bash
set -euo pipefail

APP_DIR="app/src/main/java/com/chap/zrec"

if [ ! -d "$APP_DIR" ]; then
    echo "ERROR: Run this from the ZRecorder project root."
    exit 1
fi

echo "Updating subtitle, dynamic version, and reworking Settings UI..."

###############################################################################
# 1. Update MainScreen.kt subtitle
###############################################################################
sed -i 's/Material You screen recorder/A powerful screen recorder/g' "$APP_DIR/ui/MainScreen.kt"

###############################################################################
# 2. Rework SettingsScreen.kt with beautiful Material 3 UI + Dynamic Version
###############################################################################
cat > "$APP_DIR/ui/SettingsScreen.kt" <<'EOF'
package com.chap.zrec.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
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

    // Dynamically get version name
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                SettingsCard(title = "Video") {
                    SettingRow("Resolution", RecorderOptions.resolutionLabel(settings.resolution)) { openDialog = SettingsDialog.RESOLUTION }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow("Frame rate", "${settings.fps} fps") { openDialog = SettingsDialog.FPS }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow("Bitrate", RecorderOptions.bitrateLabel(settings.videoBitrateMbps)) { openDialog = SettingsDialog.BITRATE_INPUT }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow("Encoder", settings.videoEncoder) { openDialog = SettingsDialog.ENCODER }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow("Orientation", settings.orientation) { openDialog = SettingsDialog.ORIENTATION }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow("Countdown", if (settings.countdown == 0) "Off" else "${settings.countdown}s") { openDialog = SettingsDialog.COUNTDOWN }
                }
            }

            item {
                SettingsCard(title = "Audio") {
                    SwitchRow("Record audio", settings.audioMode != "None") { checked ->
                        scope.launch { prefs.setAudioMode(if (checked) "Microphone" else "None") }
                    }
                    if (settings.audioMode != "None") {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow("Audio source", settings.audioMode) { openDialog = SettingsDialog.AUDIO_MODE }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow("Audio bitrate", RecorderOptions.audioBitrateLabel(settings.audioBitrate)) { openDialog = SettingsDialog.AUDIO_BITRATE }
                    }
                }
            }

            item {
                SettingsCard(title = "File") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("File prefix", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Prefix added to recorded file names", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.filePrefix,
                            onValueChange = { newValue -> scope.launch { prefs.setFilePrefix(newValue) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                AboutCard(versionName = versionName, onReset = { scope.launch { prefs.reset() } })
            }
            
            item { Spacer(Modifier.height(32.dp)) }
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
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun AboutCard(versionName: String, onReset: () -> Unit) {
    val localContext = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.VideoFile, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Z Recorder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Version $versionName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            
            Text("Author: chappcG", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "github.com/chappcG/ZRecorder",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    try { localContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/chappcG/ZRecorder"))) } catch (_: Exception) {}
                }
            )
            
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            
            TextButton(
                onClick = onReset,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset all settings", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AudioModeDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val isQOrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio source", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                RecorderOptions.audioModes.forEach { mode ->
                    val isInternal = mode == "Internal Audio" || mode == "Internal + Microphone"
                    val enabled = !isInternal || isQOrAbove
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelect(mode); onDismiss() },
                        color = Color.Transparent,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == mode, onClick = if (enabled) { { onSelect(mode); onDismiss() } } else null)
                            Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                                Text(mode, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                if (isInternal && !isQOrAbove) {
                                    Text("Requires Android 10+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
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
private fun RadioDialog(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { option ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(option); onDismiss() },
                        color = Color.Transparent,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option == selected, onClick = { onSelect(option); onDismiss() })
                            Text(option, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BitrateInputDialog(current: Float, onSave: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(RecorderOptions.trim(current)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video bitrate", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue -> val filtered = newValue.filter { it.isDigit() || it == '.' }; if (filtered.count { it == '.' } <= 1) { text = filtered; error = null } },
                    label = { Text("Mbps") },
                    supportingText = { Text("Allowed range: 1 - 38 Mbps") },
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { val value = text.toFloatOrNull(); when { value == null -> error = "Enter a valid number"; value < 1f -> error = "Minimum is 1 Mbps"; value > 38f -> error = "Maximum is 38 Mbps"; else -> { onSave(value); onDismiss() } } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
EOF

echo "Done!"
echo "1. Subtitle updated to 'A powerful screen recorder'."
echo "2. Settings screen now uses beautiful Material 3 cards with better spacing and typography."
echo "3. Version name is now fetched dynamically from the app's package info."
echo ""
echo "Rebuild in AndroidIDE!"