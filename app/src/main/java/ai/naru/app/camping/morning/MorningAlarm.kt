package ai.naru.app.camping.morning

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

/** 기상 알람 소리. 화면·ViewModel은 이 인터페이스만 안다. */
interface MorningAlarm {
    /** 알람을 반복 재생한다. 오디오 포커스를 얻지 못하거나 알람음이 없으면 false. */
    fun start(): Boolean

    /** 알람을 멈추고 오디오 포커스를 반납한다. */
    fun stop()
}

/**
 * 안드로이드 기본 알람음을 반복 재생한다 (이슈 #17: 별도 음원 없이 기본 알람음 사용).
 * 기본 알람음이 없으면 알림음 → 벨소리 순서로 대체한다.
 *
 * 오디오 포커스 정책은 밤 테마 소리와 같다. LOSS면 멈추고, LOSS_TRANSIENT(Gleo 음성 호출 등)면 잠시 멈췄다가 다시 받으면 이어서 울린다.
 * 알람은 깨우는 게 목적이라 볼륨 감쇄(DUCK)는 하지 않는다.
 */
class AndroidMorningAlarm(context: Context) : MorningAlarm {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> player?.run { if (!isPlaying) start() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
            AudioManager.AUDIOFOCUS_LOSS -> stop()
        }
    }

    override fun start(): Boolean {
        stop()
        val uri = defaultAlarmUri() ?: run {
            Log.w(TAG, "기본 알람음을 찾지 못함")
            return false
        }

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "오디오 포커스를 얻지 못해 알람을 울리지 않음")
            return false
        }
        focusRequest = request

        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(appContext, uri)
                isLooping = true
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "알람음을 재생하지 못함: $uri", e)
            stop()
            false
        }
    }

    override fun stop() {
        player?.run {
            runCatching { if (isPlaying) stop() }
            release()
        }
        player = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun defaultAlarmUri(): Uri? =
        listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE)
            .firstNotNullOfOrNull { RingtoneManager.getActualDefaultRingtoneUri(appContext, it) }

    private companion object {
        const val TAG = "MorningAlarm"
    }
}
