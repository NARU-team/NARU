package ai.naru.app.camping.narration

import ai.naru.app.camping.arrival.ArrivalAssessment
import ai.naru.app.camping.arrival.ArrivalReason
import ai.naru.app.camping.arrival.CampingPermission
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.arrival.Precipitation
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

    /**
     * 도착 안내 멘트: 화면의 세 카드(날씨 → 물때 또는 일출 → 이용 정보) 순서로 설명하고 등급 안내로 끝낸다.
     * 값은 카드와 같은 규칙 결과만 쓴다.
     */
    fun arrival(site: Site, conditions: SiteConditions, assessment: ArrivalAssessment): NarrationRequest {
        val reasons = assessment.reasons.map { it.label() }
        val weather = conditions.weather
        val beach = site.placeType == PlaceType.BEACH
        val nightHigh = assessment.reasons.filterIsInstance<ArrivalReason.NightHighTide>().firstOrNull()?.at
        val high = nightHigh ?: conditions.highTides?.firstOrNull()
        val low = conditions.lowTides?.firstOrNull()
        val usage = usageText(site)
        val facts = buildMap<String, Any> {
            put("장소", site.name.removeSuffix(DEMO_SUFFIX))
            put("장소유형", site.placeType.label())
            put("안내등급", assessment.grade.title())
            put("등급안내", assessment.grade.guide())
            if (reasons.isNotEmpty()) put("판단근거", reasons)
            if (weather != null) {
                put("야간최저기온C", weather.minTempC.formatTemp())
                weather.maxWindMs?.let { put("최대풍속ms", it.formatTemp()) }
                put("강한바람", weather.strongWind)
                put("강수", weather.precipitation.label())
            } else {
                put("날씨", "조회 실패")
            }
            if (beach) {
                when {
                    conditions.highTides == null -> put("물때", "조회 실패")
                    high == null -> put("물때", "밤사이 만조 없음")
                    else -> {
                        put("만조", high.format(hhmm))
                        put("자는동안만조", nightHigh != null)
                        low?.let { put("간조", it.format(hhmm)) }
                    }
                }
            } else {
                conditions.sunrise?.let { put("일출", it.format(hhmm)) }
            }
            put("이용정보", usage)
            if (site.facilities.isNotEmpty()) put("시설", site.facilities)
        }

        val weatherText = when {
            weather == null -> "날씨는 불러오지 못했어요."
            weather.strongWind -> "날씨는 최저 ${weather.minTempC.formatTemp()}도이고, " +
                (weather.maxWindMs?.let { "바람이 초속 ${it.formatTemp()}미터로 강해요." } ?: "강한 바람 예보가 있어요.")
            weather.precipitation == Precipitation.HEAVY_RAIN -> "날씨는 최저 ${weather.minTempC.formatTemp()}도이고, 강한 비 예보가 있어요."
            weather.precipitation == Precipitation.RAIN -> "날씨는 최저 ${weather.minTempC.formatTemp()}도이고, 비 예보가 있어요."
            else -> "날씨는 최저 ${weather.minTempC.formatTemp()}도이고, 비 소식은 없어요."
        }
        val secondText = when {
            !beach -> conditions.sunrise?.let { "내일 일출은 ${it.format(hhmm)}이에요." }
            conditions.highTides == null -> "물때는 불러오지 못했어요."
            high == null -> "밤사이 만조는 없어요."
            nightHigh != null -> "물때는 ${high.format(hhmm)} 만조로, 자는 동안 물이 가장 높아져요."
            else -> "물때는 만조가 ${high.format(hhmm)}" + (low?.let { ", 간조가 ${it.format(hhmm)}이에요." } ?: "이에요.")
        }
        val template = listOfNotNull(
            "${site.name.removeSuffix(DEMO_SUFFIX)}의 오늘 밤 안내예요.",
            weatherText,
            secondText,
            "이용 정보는 $usage.",
            assessment.grade.guide(),
        ).joinToString(" ")
        return NarrationRequest(NarrationMoment.ARRIVAL, facts, template)
    }

    /** 이용 정보 카드와 같은 내용을 한 구절로 (문장 끝 마침표 없이) */
    private fun usageText(site: Site): String {
        val camp = site.id.startsWith("camp-")
        return when (site.permission) {
            CampingPermission.ALLOWED -> (if (camp) "등록된 자동차야영장이지만" else "등록된 차박 장소지만") + " 오늘 밤 이용 여부는 현장에서 확인해 주세요"
            CampingPermission.UNKNOWN -> if (camp) "자동차야영장이 아니라 차박 가능 여부를 현장에서 확인해 주세요" else "차박 허용 여부를 안내판에서 확인해 주세요"
            CampingPermission.PROHIBITED -> "차박 금지 장소라 다른 곳으로 이동해 주세요"
        }
    }

    private const val DEMO_SUFFIX = " (시연)"

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
        val basis = if (settings.wakeBasis == WakeBasis.SUNRISE) "해 뜨기 조금 전인 " else ""
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
