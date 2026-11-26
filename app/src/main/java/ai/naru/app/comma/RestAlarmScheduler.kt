package ai.naru.app.comma

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper

class RestAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(sessionId: String, triggerAtWallMillis: Long): AlarmScheduleResult {
        if (triggerAtWallMillis <= System.currentTimeMillis()) return AlarmScheduleResult.EXPIRED
        val operation = operation(sessionId)
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtWallMillis,
                    operation,
                )
                AlarmScheduleResult.EXACT
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtWallMillis,
                    operation,
                )
                AlarmScheduleResult.INEXACT
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtWallMillis,
                operation,
            )
            AlarmScheduleResult.INEXACT
        }
    }

    fun cancel(sessionId: String) {
        alarmManager.cancel(operation(sessionId))
        RestAlarmPlayback.stop()
    }

    private fun operation(sessionId: String): PendingIntent {
        val intent = Intent(context, RestAlarmReceiver::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_SESSION_ID = "comma_session_id"
    }
}

enum class AlarmScheduleResult { EXACT, INEXACT, EXPIRED }

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra(RestAlarmScheduler.EXTRA_SESSION_ID).isNullOrBlank()) return
        RestAlarmPlayback.start(context.applicationContext)
    }
}

private object RestAlarmPlayback {
    private var ringtone: Ringtone? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        RestAudioCoordinator.stopNature()
        stop()
        val manager = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS) stop()
                }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (!granted) return
        audioManager = manager
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = runCatching {
            RingtoneManager.getRingtone(context, uri)?.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                it.play()
            }
        }.getOrNull()
        handler.postDelayed(::stop, MAX_RING_MILLIS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { ringtone?.stop() }
        ringtone = null
        val manager = audioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { request -> manager?.abandonAudioFocusRequest(request) }
        } else {
            @Suppress("DEPRECATION")
            manager?.abandonAudioFocus(null)
        }
        focusRequest = null
        audioManager = null
    }

    private const val MAX_RING_MILLIS = 30_000L
}
