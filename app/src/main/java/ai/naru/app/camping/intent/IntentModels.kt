package ai.naru.app.camping.intent

import ai.naru.app.camping.arrival.PlaceType
import java.time.LocalTime

/** 서버 `/api/intent`와 같은 동작 종류. */
enum class IntentAction {
    SET_TEMPERATURE,
    ADJUST_TEMPERATURE,
    SET_WAKE_TIME,
    WAKE_AT_SUNRISE,
    SET_THEME,
    SET_SOUND,
    SET_VOICE,
}

/** LLM이 낸 제안 하나 (아직 검사·승인 전). 동작에 맞는 값만 채워진다. */
data class IntentProposal(
    val action: IntentAction,
    val celsius: Float? = null,
    val deltaC: Float? = null,
    val time: LocalTime? = null,
    val theme: PlaceType? = null,
    val on: Boolean? = null,
)

data class IntentReply(
    val reply: String,
    val needsClarification: Boolean,
    val proposals: List<IntentProposal>,
)

enum class TurnRole { USER, ASSISTANT }

/** 대화 한 턴 (멀티턴 기억용) */
data class Turn(val role: TurnRole, val text: String)

/**
 * 규칙 검사를 통과한, 승인하면 적용될 변경. 지금 값 → 바뀔 값을 화면에 보여준다.
 * 실행은 ViewModel의 기존 규칙 코드가 한다 (LLM 출력이 차량 제어로 바로 이어지지 않음).
 */
sealed interface ProposedChange {
    data class Temperature(val from: Float, val to: Float) : ProposedChange
    data class WakeTime(val from: LocalTime, val to: LocalTime, val sunrise: Boolean) : ProposedChange
    data class Theme(val from: PlaceType, val to: PlaceType) : ProposedChange
    data class Sound(val on: Boolean) : ProposedChange
    data class Voice(val on: Boolean) : ProposedChange
}
