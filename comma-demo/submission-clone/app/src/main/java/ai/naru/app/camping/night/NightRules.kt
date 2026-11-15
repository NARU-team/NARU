package ai.naru.app.camping.night

import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.vehicle.AmbientLight
import java.time.LocalDateTime
import java.time.LocalTime

/** 밤 단계 기준값. 기획서에 숫자가 없어 정한 제안값이다 (2026-11-13, 이슈 #15). */
object NightThresholds {
    /** 배터리가 이 잔량(%) 이하가 되면 경고하고 아침으로 넘긴다 */
    const val BATTERY_LIMIT_PERCENT = 20

    /** 밤 테마 조명 밝기 (0~100). 잠들기 전이라 낮게 둔다. */
    const val THEME_BRIGHTNESS = 30
}

/** 테마별 조명 색 (0xRRGGBB). 제안값이다. */
object NightThemes {
    fun ambientFor(theme: PlaceType): AmbientLight = AmbientLight(
        on = true,
        brightness = NightThresholds.THEME_BRIGHTNESS,
        colorRgb = when (theme) {
            PlaceType.BEACH -> 0x2F7DD1 // 바다색 파랑
            PlaceType.FOREST -> 0x2E8B57 // 숲 초록
            PlaceType.LAKE -> 0x2BB3A8 // 호수 청록
        },
    )
}

/** 밤 단계 규칙. 규칙 기반 순수 함수다. */
object NightRules {

    /**
     * 배터리가 하한에 닿았는지. 잔량을 모르면 판단하지 않는다 (모르는 값으로 세션을 끝내지 않는다).
     */
    fun isBatteryAtLimit(percent: Int?, limit: Int = NightThresholds.BATTERY_LIMIT_PERCENT): Boolean =
        percent != null && percent <= limit

    /**
     * 밤을 시작한 시각 기준으로 다음 기상 일시를 구한다.
     * 기상 시각이 지금보다 늦으면 오늘, 같거나 이르면 내일이다. (23:00 시작, 07:00 기상 → 내일 07:00)
     */
    fun nextWakeDateTime(now: LocalDateTime, wakeAt: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(wakeAt)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun isWakeTimeReached(now: LocalDateTime, wakeDateTime: LocalDateTime): Boolean = !now.isBefore(wakeDateTime)
}
