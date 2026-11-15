package ai.naru.app.camping.record

import ai.naru.app.camping.bedtime.BedtimeRules
import ai.naru.app.camping.bedtime.FeedbackRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime

class SampleRecordsTest {

    private val now = LocalDateTime.of(2026, 11, 20, 22, 0)

    @Test
    fun `시연 기록은 데모 시나리오의 추천 21도와 지난 차박 칩, 예상 잔량을 만든다`() {
        val records = SampleRecords.demo(now, minTempC = 7f)
        val tonightMin = 8f

        val hint = FeedbackRules.hint(records, tonightMin)
        assertEquals("지난 차박 22°C는 더웠음", FeedbackRules.chipText(hint!!))
        assertEquals(21f, BedtimeRules.recommendTemperature(tonightMin, 70, 15f..30f, hint).celsius)

        // 22시에 배터리 70%로 시작해 06:50 기상
        val forecast = BatteryForecast.forecast(records, 70, 21f, tonightMin, now, now.plusDays(1).withHour(6).withMinute(50))
        assertNotNull(forecast)
        assertEquals(4, forecast!!.basedOn)
        assertEquals(56 to 62, forecast.lowPercent to forecast.highPercent)
    }

    @Test
    fun `가장 최근 시연 기록이 홈 칩에 쓰인다`() {
        val latest = SampleRecords.demo(now, 7f).maxBy { it.nightStartedAt }
        assertEquals("자라섬 캠핑장", latest.siteName)
    }
}
