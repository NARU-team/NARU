package ai.naru.app.camping.narration

import ai.naru.app.camping.arrival.ArrivalAssessment
import ai.naru.app.camping.arrival.Site
import ai.naru.app.camping.arrival.SiteConditions
import ai.naru.app.camping.arrival.formatTemp
import ai.naru.app.camping.arrival.guide
import ai.naru.app.camping.arrival.label
import ai.naru.app.camping.arrival.title
import ai.naru.app.camping.bedtime.BedtimeSettings
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.camping.morning.MorningBriefing
import ai.naru.app.camping.night.NightThresholds
import ai.naru.app.camping.record.MorningForecast
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 멘트를 만드는 세 순간 (기획서 "세 순간의 멘트"). */
enum class NarrationMoment { ARRIVAL, BEDTIME, MORNING }

/**
 * 멘트 요청 하나.
 * @property facts 규칙 엔진이 정한 사실. 서버의 LLM은 이 값만 문장으로 다듬는다 (값을 바꾸지 않는다).
 * @property template LLM이 실패했을 때 그대로 쓰는 준비된 문장
 */
data class NarrationRequest(
    val moment: NarrationMoment,
    val facts: Map<String, Any>,
    val template: String,
)

/** 세 순간의 사실과 준비된 문장을 만든다. 순수 함수라 단위 테스트한다. */
object NarrationScripts {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun arrival(site: Site, conditions: SiteConditions, assessment: ArrivalAssessment): NarrationRequest {
        val reasons = assessment.reasons.map { it.label() }
        val facts = buildMap<String, Any> {
            put("장소", site.name)
            put("장소유형", site.placeType.label())
            put("안내등급", assessment.grade.title())
            put("등급안내", assessment.grade.guide())
            if (reasons.isNotEmpty()) put("판단근거", reasons)
            conditions.weather?.let {
                put("야간최저기온C", it.minTempC.formatTemp())
                put("강수", it.precipitation.label())
            }
            conditions.highTides?.takeIf { it.isNotEmpty() }?.let { tides -> put("만조", tides.map { it.format(hhmm) }) }
        }
        val reasonText = reasons.joinToString(" ") { "$it." }
        val template = "도착 안내는 ${assessment.grade.title()}입니다. ${if (reasonText.isNotEmpty()) "$reasonText " else ""}${assessment.grade.guide()}"
        return NarrationRequest(NarrationMoment.ARRIVAL, facts, template)
    }

    fun bedtime(settings: BedtimeSettings, sunrise: LocalTime?, batteryPercent: Int?, forecast: MorningForecast? = null): NarrationRequest {
        val wake = settings.wakeAt.format(hhmm)
        val facts = buildMap<String, Any> {
            put("테마", settings.theme.label())
            put("설정온도C", settings.targetTempC.formatTemp())
            put("기상시각", wake)
            put("기상기준", if (settings.wakeBasis == WakeBasis.SUNRISE) "일출" else "직접 설정")
            sunrise?.let { put("일출", it.format(hhmm)) }
            batteryPercent?.let { put("배터리퍼센트", it) }
            forecast?.let {
                put("아침예상잔량퍼센트", "${it.lowPercent}~${it.highPercent}")
                if (it.belowLimit) {
                    put("주의", "아침에 배터리가 ${NightThresholds.BATTERY_LIMIT_PERCENT}%보다 낮아질 수 있으니 설정 온도를 조금 낮추는 걸 권해요.")
                }
            }
        }
        val basis = if (settings.wakeBasis == WakeBasis.SUNRISE) "일출 시각인 " else ""
        val forecastText = forecast?.let {
            val warning = if (it.belowLimit) "배터리 하한보다 낮아질 수 있으니 설정 온도를 조금 낮추는 걸 권해요. " else ""
            "내일 아침 배터리는 ${it.lowPercent}에서 ${it.highPercent}퍼센트 정도로 예상돼요. $warning"
        } ?: ""
        val template = "오늘 밤은 ${settings.theme.label()} 테마로 쉬어요. 설정 온도는 ${settings.targetTempC.formatTemp()}도이고, " +
            "${basis}${wake}에 깨워 드릴게요. ${forecastText}편히 쉬세요."
        return NarrationRequest(NarrationMoment.BEDTIME, facts, template)
    }

    fun morning(briefing: MorningBriefing): NarrationRequest {
        val facts = buildMap<String, Any> {
            briefing.warning?.let { put("경고", it) }
            put("브리핑", briefing.lines)
        }
        val template = listOfNotNull(briefing.warning, *briefing.lines.toTypedArray()).joinToString(" ")
        return NarrationRequest(NarrationMoment.MORNING, facts, template)
    }
}
