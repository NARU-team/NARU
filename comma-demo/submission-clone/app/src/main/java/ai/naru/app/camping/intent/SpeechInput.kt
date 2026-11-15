package ai.naru.app.camping.intent

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import java.util.Locale

/** 음성 입력 상태. 화면은 이 값만 보고 마이크 버튼을 그린다. */
sealed interface SpeechEvent {
    /** 듣기 시작 */
    data object Listening : SpeechEvent

    /** 인식 중인 문장 (중간 결과) */
    data class Partial(val text: String) : SpeechEvent

    /** 최종 문장 */
    data class Final(val text: String) : SpeechEvent

    /** 실패 (마이크 없음, 엔진 오류 등). 텍스트 입력으로 계속한다 */
    data class Failed(val reason: String) : SpeechEvent

    /** 아무 말도 인식하지 못하고 끝남 */
    data object Ended : SpeechEvent
}

/** 음성 입력. 화면·ViewModel은 이 인터페이스만 안다. */
interface SpeechInput {
    /** 마이크로 한 문장을 듣는다. 결과는 [onEvent]로 온다. */
    fun listen(onEvent: (SpeechEvent) -> Unit)

    fun stop()

    fun release()
}

/**
 * Pleos SpeechToText SDK (v3.3.1) — ON_DEVICE 모드 (Playground 앱 등록 불필요, TTS와 같은 방식).
 * 순서: initialize → addListener → setLocale → request()(마이크) 또는 sendAudio()(오디오 직접 전달) → stop → release
 *
 * 에뮬레이터 실측 (2026-11-13, 이슈 #27):
 * - 한국어 ON_DEVICE 인식 동작 확인 (macOS 음성으로 만든 "조금 더 따뜻하게 해 줘" PCM → "더 따뜻하게 해줘")
 * - onEndedRecognition이 최종 문장(onUpdated isFinal=true)보다 **먼저** 온다 → 끝 신호는 최종 문장으로만 판단
 * - 엔진이 말 끝을 스스로 감지해 작업을 나누기도 한다. 최종 문장을 받은 뒤의 오류는 무시한다
 * - sendAudio는 request() 없이 써야 한다 (request()는 마이크 녹음을 시작해 충돌)
 */
class PleosSpeechInput(context: Context) : SpeechInput {

    private val appContext = context.applicationContext
    private var stt: SpeechToText? = null
    private var ready = false
    private var onEvent: ((SpeechEvent) -> Unit)? = null
    private var gotFinal = false

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() {
            onEvent?.invoke(SpeechEvent.Listening)
        }
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (gotFinal) return
            if (isFinal) {
                gotFinal = true
                onEvent?.invoke(if (text.isBlank()) SpeechEvent.Ended else SpeechEvent.Final(text.trim()))
            } else if (text.isNotBlank()) {
                onEvent?.invoke(SpeechEvent.Partial(text.trim()))
            }
        }
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit

        // 최종 문장보다 먼저 오므로 끝 신호로 쓰지 않는다. 최종 문장이 안 오면 ViewModel 제한 시간이 처리한다
        override fun onEndedRecognition() = Unit

        override fun onError(type: ErrorType) {
            if (gotFinal) return
            Log.w(TAG, "음성 인식 오류: $type")
            gotFinal = true
            onEvent?.invoke(SpeechEvent.Failed(type.name))
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override fun listen(onEvent: (SpeechEvent) -> Unit) = start(onEvent) { it.request() }

    /**
     * 개발·검증용: 마이크 대신 16kHz·16bit·mono PCM을 직접 넣는다 (SDK는 ON_DEVICE에서만 허용).
     * 에뮬레이터에 말할 사람이 없어도 인식을 확인할 수 있다.
     */
    fun recognizePcm(pcm: ByteArray, onEvent: (SpeechEvent) -> Unit) = start(onEvent) { engine ->
        pcm.toList().chunked(CHUNK_BYTES).forEach { engine.sendAudio(it.toByteArray()) }
        engine.completeAudioSend()
    }

    override fun stop() {
        runCatching { stt?.stop() }
    }

    override fun release() {
        stop()
        runCatching {
            stt?.removeListener(listener)
            stt?.release()
        }
        stt = null
        ready = false
    }

    private fun start(onEvent: (SpeechEvent) -> Unit, action: (SpeechToText) -> Unit) {
        this.onEvent = onEvent
        gotFinal = false
        val engine = stt
        if (engine != null && ready) {
            runCatching { action(engine) }.onFailure { onEvent(SpeechEvent.Failed(it.message ?: "오류")) }
            return
        }
        val created = try {
            SpeechToText(appContext, Mode.ON_DEVICE)
        } catch (e: Exception) {
            Log.w(TAG, "음성 입력을 쓸 수 없는 환경", e)
            onEvent(SpeechEvent.Failed("NOT_AVAILABLE"))
            return
        }
        stt = created
        created.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                created.addListener(listener)
                created.setLocale(Locale.KOREA)
                ready = true
                runCatching { action(created) }.onFailure { onEvent(SpeechEvent.Failed(it.message ?: "오류")) }
            }

            override fun onFailure(type: ErrorType) {
                Log.w(TAG, "음성 입력 초기화 실패: $type")
                onEvent(SpeechEvent.Failed(type.name))
            }
        })
    }

    private companion object {
        const val TAG = "SpeechInput"

        /** 100ms 분량 (16000Hz × 2바이트 × 0.1초) */
        const val CHUNK_BYTES = 3200
    }
}
