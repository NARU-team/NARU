package ai.naru.app.camping.night

import ai.naru.app.R
import ai.naru.app.camping.arrival.PlaceType
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

/** 밤 테마 소리. 화면·ViewModel은 이 인터페이스만 안다. */
interface NightSound {
    /** 테마 소리를 반복 재생한다. 오디오 포커스를 얻지 못하면 재생하지 않고 false를 돌려준다. */
    fun play(theme: PlaceType): Boolean

    /** 재생을 멈추고 오디오 포커스를 반납한다. */
    fun stop()
}

/**
 * Android MediaPlayer로 테마 소리를 반복 재생한다.
 *
 * Pleos 오디오 포커스 정책 (오디오 포커스 개발 가이드):
 * - 재생 전에 AudioAttributes + AudioFocusRequest로 포커스를 요청한다.
 * - LOSS → 정지, LOSS_TRANSIENT → 일시정지 후 복귀 가능, LOSS_TRANSIENT_CAN_DUCK → 볼륨 감쇄.
 * - Gleo 음성 호출로 LOSS_TRANSIENT를 받으면 즉시 멈춘다.
 *
 * 음원: Pixabay (Pixabay Content License), 출처는 저장소의 THIRD_PARTY_NOTICES.md 참고.
 */
class AndroidNightSound(context: Context) : NightSound {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> player?.run { setVolume(1f, 1f); if (!isPlaying) start() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(DUCK_VOLUME, DUCK_VOLUME)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
            AudioManager.AUDIOFOCUS_LOSS -> stop()
        }
    }

    override fun play(theme: PlaceType): Boolean {
        stop()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // 차량 오디오 정책 응답이 늦으면 앱은 실패로 받는데 시스템은 나중에 포커스를 줄 수 있다 (에뮬레이터 실측).
            // 그대로 두면 쓰지도 않는 포커스를 계속 쥐고 있으므로 반드시 반납한다.
            audioManager.abandonAudioFocusRequest(request)
            Log.w(TAG, "오디오 포커스를 얻지 못해 테마 소리를 재생하지 않음")
            return false
        }
        focusRequest = request

        val created = MediaPlayer.create(appContext, soundRes(theme), attributes, audioManager.generateAudioSessionId())
        if (created == null) {
            Log.w(TAG, "테마 소리 파일을 열지 못함: $theme")
            abandonFocus()
            return false
        }
        player = created.apply {
            isLooping = true
            start()
        }
        return true
    }

    override fun stop() {
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null
        abandonFocus()
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun soundRes(theme: PlaceType): Int = when (theme) {
        PlaceType.BEACH -> R.raw.beach
        PlaceType.FOREST -> R.raw.forest
        PlaceType.LAKE -> R.raw.lake
    }

    private companion object {
        const val TAG = "NightSound"
        const val DUCK_VOLUME = 0.3f
    }
}
