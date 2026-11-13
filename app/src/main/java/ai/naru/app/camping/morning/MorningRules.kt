package ai.naru.app.camping.morning

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.MorningWeather
import ai.naru.app.camping.arrival.Sky
import ai.naru.app.vehicle.AmbientLight

/** 기상 단계 기준값. 기획서에 숫자가 없어 정한 제안값이다 (2026-11-13, 이슈 #17). */
object MorningThresholds {
    /** 아침 조명 밝기 (0~100). 눈부시지 않게 밤보다 밝은 정도로 둔다. */
    const val LIGHT_BRIGHTNESS = 70

    /** 아침 조명 색 (0xRRGGBB). 따뜻한 햇살색. */
    const val LIGHT_COLOR_RGB = 0xFFD8A8

    /** 이 기온(°C) 이하면 브리핑에서 옷차림을 주의시킨다. */
    const val COLD_MORNING_TEMP_C = 5f
}

/** 아침 브리핑 내용. 화면은 [lines]를 순서대로 보여준다. */
data class MorningBriefing(
    /** 배터리 하한 때문에 일찍 깨운 경우 맨 위에 보여줄 경고. 아니면 null */
    val warning: String?,
    val lines: List<String>,
    /** 밤사이 배터리 사용량(%p). 알 수 없으면 null. 기록 저장 이슈에서도 쓴다. */
    val batteryUsedPercent: Int?,
)

/**
 * 기상 단계 규칙. 규칙 기반 순수 함수다.
 * 브리핑 문구는 템플릿이고, AI 멘트·TTS 이슈에서 이 결과를 멘트 생성 입력으로 바꾼다.
 */
object MorningRules {

    val morningLight: AmbientLight = AmbientLight(
        on = true,
        brightness = MorningThresholds.LIGHT_BRIGHTNESS,
        colorRgb = MorningThresholds.LIGHT_COLOR_RGB,
    )

    /**
     * 밤사이 배터리 사용량(%p). 둘 중 하나라도 모르면 null.
     * 충전 등으로 오히려 늘었으면 0으로 본다.
     */
    fun batteryUsed(atNightStart: Int?, now: Int?): Int? {
        if (atNightStart == null || now == null) return null
        return (atNightStart - now).coerceAtLeast(0)
    }

    fun briefing(
        reason: EndReason?,
        weather: MorningWeather?,
        batteryAtNightStart: Int?,
        batteryNow: Int?,
        cabinTempC: Float?,
    ): MorningBriefing {
        val warning = if (reason == EndReason.BATTERY_LOW) {
            "배터리가 ${batteryNow?.let { "$it%" } ?: "하한"}까지 내려가서 기상 시각보다 일찍 깨웠어요. 공조를 줄이고 충전 계획을 세워 주세요."
        } else {
            null
        }

        val used = batteryUsed(batteryAtNightStart, batteryNow)
        val lines = buildList {
            add(
                if (weather == null) {
                    "아침 날씨를 불러오지 못했어요."
                } else {
                    val cold = if (weather.tempC <= MorningThresholds.COLD_MORNING_TEMP_C) " 쌀쌀하니 겉옷을 챙기세요." else ""
                    "지금 바깥은 ${weather.sky.label()}, ${formatTemp(weather.tempC)}°C예요.$cold"
                },
            )
            add(
                when {
                    batteryNow == null -> "배터리 잔량을 확인하지 못했어요."
                    used == null -> "배터리는 $batteryNow% 남았어요."
                    else -> "배터리는 $batteryNow% 남았어요. 밤사이 ${used}%를 썼어요."
                },
            )
            cabinTempC?.let { add("차 안은 ${formatTemp(it)}°C예요.") }
        }
        return MorningBriefing(warning, lines, used)
    }

    private fun Sky.label() = when (this) {
        Sky.CLEAR -> "맑음"
        Sky.CLOUDY -> "구름 많음"
        Sky.OVERCAST -> "흐림"
        Sky.RAIN -> "비"
        Sky.SNOW -> "눈"
    }

    private fun formatTemp(celsius: Float): String =
        if (celsius % 1f == 0f) celsius.toInt().toString() else "%.1f".format(celsius)
}
