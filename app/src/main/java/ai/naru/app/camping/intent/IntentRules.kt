package ai.naru.app.camping.intent

import ai.naru.app.camping.CampingStage
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.BedtimeRules
import java.time.LocalTime

/** 제안을 검사할 때 쓰는 지금 상태. */
data class IntentContext(
    val stage: CampingStage,
    val targetTempC: Float?,
    val vehicleTempRange: ClosedFloatingPointRange<Float>?,
    val wakeAt: LocalTime?,
    val sunrise: LocalTime?,
    val theme: PlaceType?,
    val soundOn: Boolean,
    val voiceOn: Boolean,
)

/**
 * 발화 해석 규칙. LLM 제안을 단계·범위로 다시 검사해 실제 변경으로 바꾼다. 규칙 기반 순수 함수다.
 * 서버도 같은 단계 규칙으로 거르지만, 최종 판단은 앱(차량 범위를 아는 쪽)이 한다.
 */
object IntentRules {

    /** 발화 해석을 쓸 수 있는 단계 */
    val STAGES = setOf(CampingStage.BEDTIME_PREP, CampingStage.NIGHT)

    /** 단계마다 허용하는 동작 (서버 ALLOWED와 같음) */
    fun allowed(stage: CampingStage): Set<IntentAction> = when (stage) {
        CampingStage.BEDTIME_PREP -> setOf(
            IntentAction.SET_TEMPERATURE, IntentAction.ADJUST_TEMPERATURE,
            IntentAction.SET_WAKE_TIME, IntentAction.WAKE_AT_SUNRISE, IntentAction.SET_THEME,
        )
        CampingStage.NIGHT -> setOf(
            IntentAction.SET_TEMPERATURE, IntentAction.ADJUST_TEMPERATURE,
            IntentAction.SET_SOUND, IntentAction.SET_VOICE,
        )
        else -> emptySet()
    }

    /**
     * 제안 목록 → 적용할 변경 목록.
     * - 허용되지 않은 동작, 값이 없는 제안, 지금과 같은 값은 버린다
     * - 온도는 0.5°C 단위로 맞추고 차량 범위 안으로 자른다
     * - 같은 종류가 여러 개면 마지막 것만 쓴다 (고쳐 말하기)
     */
    fun resolve(proposals: List<IntentProposal>, context: IntentContext): List<ProposedChange> {
        val allowed = allowed(context.stage)
        val changes = linkedMapOf<Class<out ProposedChange>, ProposedChange>()
        for (p in proposals) {
            if (p.action !in allowed) continue
            val change = toChange(p, context) ?: continue
            changes.remove(change.javaClass)
            changes[change.javaClass] = change
        }
        return changes.values.toList()
    }

    private fun toChange(p: IntentProposal, c: IntentContext): ProposedChange? = when (p.action) {
        IntentAction.SET_TEMPERATURE, IntentAction.ADJUST_TEMPERATURE -> {
            val from = c.targetTempC ?: return null
            val raw = if (p.action == IntentAction.SET_TEMPERATURE) p.celsius else p.deltaC?.let { from + it }
            raw?.let { target ->
                val to = BedtimeRules.adjustTemperature(target, 0, c.vehicleTempRange)
                if (to != from) ProposedChange.Temperature(from, to) else null
            }
        }
        IntentAction.SET_WAKE_TIME -> {
            val from = c.wakeAt
            val to = p.time
            if (from != null && to != null && to != from) ProposedChange.WakeTime(from, to, sunrise = false) else null
        }
        IntentAction.WAKE_AT_SUNRISE -> {
            val from = c.wakeAt
            val sunrise = c.sunrise
            val to = sunrise?.let { BedtimeRules.sunriseWakeTime(it) }
            if (from != null && to != null && to != from) ProposedChange.WakeTime(from, to, sunrise = true) else null
        }
        IntentAction.SET_THEME -> {
            val from = c.theme
            val to = p.theme
            if (from != null && to != null && to != from) ProposedChange.Theme(from, to) else null
        }
        IntentAction.SET_SOUND -> p.on?.takeIf { it != c.soundOn }?.let { ProposedChange.Sound(it) }
        IntentAction.SET_VOICE -> p.on?.takeIf { it != c.voiceOn }?.let { ProposedChange.Voice(it) }
    }
}
