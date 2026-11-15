package ai.naru.app.camping.morning

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.MorningWeather
import ai.naru.app.camping.arrival.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningRulesTest {

    @Test
    fun `밤사이 배터리 사용량은 밤 시작 잔량에서 지금 잔량을 뺀 값이다`() {
        assertEquals(12, MorningRules.batteryUsed(atNightStart = 80, now = 68))
    }

    @Test
    fun `배터리가 늘었으면 사용량은 0이다`() {
        assertEquals(0, MorningRules.batteryUsed(atNightStart = 60, now = 65))
    }

    @Test
    fun `잔량을 하나라도 모르면 사용량도 모른다`() {
        assertNull(MorningRules.batteryUsed(atNightStart = null, now = 70))
        assertNull(MorningRules.batteryUsed(atNightStart = 80, now = null))
    }

    @Test
    fun `기상 시각에 깨면 경고 없이 날씨와 차량 상태를 알려준다`() {
        val briefing = MorningRules.briefing(
            reason = EndReason.WAKE_TIME,
            weather = MorningWeather(tempC = 8f, sky = Sky.CLEAR),
            batteryAtNightStart = 80,
            batteryNow = 68,
            cabinTempC = 21.5f,
        )
        assertNull(briefing.warning)
        assertEquals(
            listOf(
                "지금 바깥은 맑음, 8°C예요.",
                "배터리는 68% 남았어요. 밤사이 12%를 썼어요.",
                "차 안은 21.5°C예요.",
            ),
            briefing.lines,
        )
        assertEquals(12, briefing.batteryUsedPercent)
    }

    @Test
    fun `배터리 하한으로 깨우면 경고를 먼저 보여준다`() {
        val briefing = MorningRules.briefing(EndReason.BATTERY_LOW, null, 50, 20, null)
        assertNotNull(briefing.warning)
        assertTrue(briefing.warning!!.contains("20%"))
    }

    @Test
    fun `추운 아침에는 옷차림을 주의시킨다`() {
        val briefing = MorningRules.briefing(EndReason.WAKE_TIME, MorningWeather(0f, Sky.CLOUDY), null, 70, null)
        assertEquals("지금 바깥은 구름 많음, 0°C예요. 쌀쌀하니 겉옷을 챙기세요.", briefing.lines[0])
        assertEquals("배터리는 70% 남았어요.", briefing.lines[1])
    }

    @Test
    fun `모르는 값은 모른다고 알려준다`() {
        val briefing = MorningRules.briefing(EndReason.WAKE_TIME, null, null, null, null)
        assertEquals(listOf("아침 날씨를 불러오지 못했어요.", "배터리 잔량을 확인하지 못했어요."), briefing.lines)
    }
}
