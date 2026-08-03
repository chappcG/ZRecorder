package com.chap.zrec

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chap.zrec.data.PrefsManager
import com.chap.zrec.data.RecorderSettings
import com.chap.zrec.data.RecordingRepository
import com.chap.zrec.service.MediaProjectionCache
import com.chap.zrec.service.ProcessingState
import com.chap.zrec.service.RecorderService
import com.chap.zrec.ui.MainScreen
import com.chap.zrec.ui.SettingsScreen
import com.chap.zrec.ui.theme.ZRecorderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ZRecorder", "MainActivity onCreate")
        enableEdgeToEdge()
        if (intent?.getBooleanExtra("show_processing", false) == true) ProcessingState.show()
        val prefs = PrefsManager(applicationContext)
        val repository = RecordingRepository(applicationContext)
        setContent {
            ZRecorderTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") { MainScreen(prefs = prefs, repository = repository, onOpenSettings = { navController.navigate("settings") }) }
                    composable("settings") { SettingsScreen(prefs = prefs, onBack = { navController.popBackStack() }) }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("show_processing", false)) ProcessingState.show()
    }
}

fun startRecorderService(context: Context, resultCode: Int, data: Intent, settings: RecorderSettings, isPortrait: Boolean) {
    Log.d("ZRecorder", "startRecorderService: resultCode=$resultCode, isPortrait=$isPortrait")
    MediaProjectionCache.resultCode = resultCode
    MediaProjectionCache.data = data
    
    val intent = Intent(context, RecorderService::class.java).apply {
        action = RecorderService.ACTION_START
        putExtra(RecorderService.EXTRA_WIDTH, settings.width)
        putExtra(RecorderService.EXTRA_HEIGHT, settings.height)
        putExtra(RecorderService.EXTRA_FPS, settings.fps)
        putExtra(RecorderService.EXTRA_VIDEO_BITRATE, settings.videoBitrate)
        putExtra(RecorderService.EXTRA_VIDEO_ENCODER, settings.videoEncoder)
        putExtra(RecorderService.EXTRA_ORIENTATION, settings.orientation)
        putExtra(RecorderService.EXTRA_COUNTDOWN, settings.countdown)
        putExtra(RecorderService.EXTRA_AUDIO_MODE, settings.audioMode)
        putExtra(RecorderService.EXTRA_AUDIO_BITRATE, settings.audioBitrate)
        putExtra(RecorderService.EXTRA_FILE_PREFIX, settings.filePrefix)
        putExtra(RecorderService.EXTRA_IS_PORTRAIT, isPortrait)
    }
    
    ContextCompat.startForegroundService(context, intent)
}
