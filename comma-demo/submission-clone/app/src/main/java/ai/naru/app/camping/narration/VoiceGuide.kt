package ai.naru.app.camping.narration

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import java.util.Locale

/** 멘트 음성 안내. 화면·ViewModel은 이 인터페이스만 안다. */
interface VoiceGuide {
    /** 문장을 읽는다. 읽는 중이면 멈추고 새 문장을 읽는다. 쓸 수 없는 환경이면 아무것도 하지 않는다. */
    fun speak(text: String)

    /** 읽기를 멈춘다. */
    fun stop()

    /** 자원을 놓는다. */
    fun release()
}

/**
 * Pleos TextToSpeech SDK (v3.3.1) — ON_DEVICE 모드.
 * ON_DEVICE는 네트워크·Playground 앱 등록(Client ID/Secret)이 필요 없다. HYBRID/SERVER는 범위 밖 (이슈 #23).
 *
 * 순서 (SDK 문서): initialize → addEventListener → setLocale → speak(text, isFinal = true) → stop → release
 * 초기화가 끝나기 전에 들어온 문장은 마지막 하나만 기억했다가 읽는다.
 *
 * 오디오 포커스: 읽는 동안 GAIN_TRANSIENT_MAY_DUCK을 잡는다. 밤 테마 소리는 볼륨을 줄였다가 돌아온다.
 * TODO(확인 필요): 문서 권장 "선행 묵음 150~200ms"는 오디오 데이터 단계 패딩이라 이 SDK API로는 넣을 수 없다.
 *   에뮬레이터에서 첫 음절이 잘리는지 확인한다.
 */
class PleosVoiceGuide(context: Context) : VoiceGuide {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS) stop() }
        .build()

    private var tts: TextToSpeech? = null
    private var state = State.IDLE
    private var pending: String? = null
    private var holdingFocus = false

    private enum class State { IDLE, INITIALIZING, READY, UNAVAILABLE }

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = abandonFocus()
        override fun onStop() = abandonFocus()
        override fun onError(type: ErrorType) {
            Log.w(TAG, "음성 안내 오류: $type")
            abandonFocus()
        }
    }

    override fun speak(text: String) {
        when (state) {
            State.UNAVAILABLE -> return
            State.READY -> say(text)
            State.INITIALIZING -> pending = text
            State.IDLE -> {
                pending = text
                initialize()
            }
        }
    }

    override fun stop() {
        pending = null
        runCatching { tts?.stop() }
        abandonFocus()
    }

    override fun release() {
        stop()
        runCatching {
            tts?.removeEventListener(events)
            tts?.release()
        }
        tts = null
        state = State.IDLE
    }

    private fun initialize() {
        state = State.INITIALIZING
        val engine = try {
            TextToSpeech(appContext, Mode.ON_DEVICE)
        } catch (e: Exception) {
            // Pleos 음성 서비스가 없는 환경(일반 에뮬레이터·기기)
            Log.w(TAG, "음성 안내를 쓸 수 없는 환경", e)
            state = State.UNAVAILABLE
            return
        }
        tts = engine
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                engine.addEventListener(events)
                engine.setLocale(Locale.KOREA)
                state = State.READY
                pending?.let { say(it) }
                pending = null
            }

            override fun onFailure(type: ErrorType) {
                Log.w(TAG, "음성 안내 초기화 실패: $type")
                state = State.UNAVAILABLE
                pending = null
            }
        })
    }

    private fun say(text: String) {
        val engine = tts ?: return
        runCatching { engine.stop() }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // 응답이 늦어 실패로 받은 포커스가 나중에 주어질 수 있어 반납한다 (이슈 #17 실측)
            audioManager.abandonAudioFocusRequest(focusRequest)
            Log.w(TAG, "오디오 포커스를 얻지 못해 음성 안내를 건너뜀")
            return
        }
        holdingFocus = true
        engine.speak(text, true)
    }

    private fun abandonFocus() {
        if (!holdingFocus) return
        holdingFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val TAG = "VoiceGuide"
    }
}
