package ai.naru.app.camping.night

import java.time.Duration
import java.time.LocalDateTime

/**
 * 밤 테마(소리·조명)를 끄는 시각 (피그마 v2 갱신 "테마 종료"). 공조와 기상 알람은 그대로 둔다.
 * 고를 수 있는 시간은 여기서만 바꾼다.
 */
object ThemeTimer {
    val OPTIONS: List<Int> = listOf(30, 60, 90)
    const val DEFAULT_MINUTES = 60

    /** [step] 칸 옮긴 시간. 처음·끝을 넘지 않는다 */
    fun move(minutes: Int, step: Int): Int {
        val index = OPTIONS.indexOf(minutes).takeIf { it >= 0 } ?: OPTIONS.indexOf(DEFAULT_MINUTES)
        return OPTIONS[(index + step).coerceIn(0, OPTIONS.lastIndex)]
    }

    fun endsAt(start: LocalDateTime, minutes: Int): LocalDateTime = start.plusMinutes(minutes.toLong())

    fun isOver(now: LocalDateTime, endsAt: LocalDateTime?): Boolean = endsAt != null && !now.isBefore(endsAt)

    /** 남은 분 (올림). 이미 지났으면 0 */
    fun minutesLeft(now: LocalDateTime, endsAt: LocalDateTime): Long {
        val seconds = Duration.between(now, endsAt).seconds
        return if (seconds <= 0) 0 else (seconds + 59) / 60
    }
}
