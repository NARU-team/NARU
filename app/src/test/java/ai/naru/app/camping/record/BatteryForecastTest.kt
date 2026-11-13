package ai.naru.app.camping.record

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BatteryForecastTest {

    private val base = LocalDateTime.of(2026, 11, 10, 22, 0)

    /** 22시부터 [hours]시간, 배터리 start → end, 바깥 최저 minTemp, 설정 target */
    private fun record(hours: Long = 8, start: Int? = 80, end: Int? = 72, minTemp: Float? = 5f, target: Float = 22f, day: Long = 0) = SessionRecord(
        id = "r$day$hours$end",
        nightStartedAt = base.plusDays(day),
        nightEndedAt = base.plusDays(day).plusHours(hours),
        siteName = "테스트",
        placeType = PlaceType.FOREST,
        grade = null,
        minTempC = minTemp,
        targetTempC = target,
        theme = PlaceType.FOREST,
        nightStartBattery = start,
        nightEndBattery = end,
        endReason = EndReason.WAKE_TIME,
    )

    private val now = LocalDateTime.of(2026, 11, 13, 23, 0)
    private val wake = LocalDateTime.of(2026, 11, 14, 7, 0) // 8시간 뒤

    @Test
    fun `시간당 사용량은 쓴 배터리를 밤 시간으로 나눈 값`() {
        assertEquals(1.0, BatteryForecast.usagePerHour(record(hours = 8, start = 80, end = 72))!!, 0.001)
    }

    @Test
    fun `짧은 밤, 충전된 기록, 배터리를 모르는 기록은 쓰지 않는다`() {
        assertNull(BatteryForecast.usagePerHour(record(hours = 1)))
        assertNull(BatteryForecast.usagePerHour(record(start = 60, end = 70)))
        assertNull(BatteryForecast.usagePerHour(record(start = null)))
    }

    @Test
    fun `비슷한 기록 3건이면 사용량 최대·최소로 범위를 낸다`() {
        // 1.0, 1.5, 2.0 %/h
        val records = listOf(record(end = 72, day = 0), record(end = 68, day = 1), record(end = 64, day = 2))
        val forecast = BatteryForecast.forecast(records, currentBattery = 70, targetTempC = 22f, minTempC = 5f, now = now, wakeAt = wake)!!
        assertEquals(54, forecast.lowPercent) // 70 - 2.0 × 8
        assertEquals(62, forecast.highPercent) // 70 - 1.0 × 8
        assertEquals(3, forecast.basedOn)
        assertEquals(false, forecast.belowLimit)
    }

    @Test
    fun `비슷한 기록이 2건뿐이면 예측하지 않는다`() {
        val records = listOf(record(day = 0), record(day = 1))
        assertNull(BatteryForecast.forecast(records, 70, 22f, 5f, now, wake))
    }

    @Test
    fun `조건이 다른 기록은 비슷한 기록으로 세지 않는다`() {
        // 지금: 22 - 5 = 17°C 차이. 영하 10도 밤(32°C 차이)은 ±5°C 밖
        val records = listOf(record(day = 0), record(day = 1), record(minTemp = -10f, day = 2))
        assertNull(BatteryForecast.forecast(records, 70, 22f, 5f, now, wake))
    }

    @Test
    fun `바깥 최저기온이나 지금 잔량을 모르면 예측하지 않는다`() {
        val records = List(3) { record(day = it.toLong()) }
        assertNull(BatteryForecast.forecast(records, 70, 22f, null, now, wake))
        assertNull(BatteryForecast.forecast(records, null, 22f, 5f, now, wake))
    }

    @Test
    fun `낮은 쪽이 배터리 하한 아래면 알리고 0 아래로는 내려가지 않는다`() {
        val records = List(3) { record(start = 80, end = 40, day = it.toLong()) } // 5 %/h
        val forecast = BatteryForecast.forecast(records, 30, 22f, 5f, now, wake)!!
        assertEquals(0, forecast.lowPercent)
        assertTrue(forecast.belowLimit)
    }
}
