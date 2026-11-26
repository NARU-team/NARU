package ai.naru.app.comma

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
internal fun CabinEffects(active: Boolean, phase: RestPhase, settings: CabinSettings, manageWindow:Boolean=true) {
    val activity = LocalContext.current.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val brightness by animateFloatAsState(settings.brightness, label = "restBrightness")
    DisposableEffect(activity, active, manageWindow) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness
        val keptScreenOn = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if (active && manageWindow) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (active && manageWindow && window != null) {
                if (original != null) window.attributes = window.attributes.apply { screenBrightness = original }
                if (!keptScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    SideEffect {
        if (active && manageWindow) activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
    }
    LaunchedEffect(active, settings.sound, phase, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (active) {
                val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 15) }.getOrNull()
                try {
                    if (phase == RestPhase.WAKING) {
                        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 1200)
                        delay(1500)
                    } else if (settings.sound == RestSound.SOFT_TONE) {
                        while (true) { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 200); delay(15_000) }
                    } else if (settings.sound in setOf(RestSound.RAIN, RestSound.OCEAN, RestSound.FOREST, RestSound.LAKE)) {
                        RestAudioCoordinator.playNature(settings.sound)
                        while (true) delay(60_000)
                    }
                } finally {
                    RestAudioCoordinator.stopNature()
                    tone?.release()
                }
            }
        }
    }
}
