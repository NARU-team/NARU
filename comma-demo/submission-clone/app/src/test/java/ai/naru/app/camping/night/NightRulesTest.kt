package ai.naru.app.camping.night

import ai.naru.app.camping.arrival.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class NightRulesTest {

    // --- 배터리 하한 ---

    @Test
    fun `배터리 하한 경계값`() {
        assertFalse(NightRules.isBatteryAtLimit(21))
        assertTrue(NightRules.isBatteryAtLimit(20))
        assertTrue(NightRules.isBatteryAtLimit(0))
    }

    @Test
    fun `배터리 잔량을 모르면 하한으로 보지 않는다`() {
        assertFalse(NightRules.isBatteryAtLimit(null))
    }

    // --- 기상 일시 ---

    @Test
    fun `밤에 시작하면 다음 날 아침에 깨운다`() {
        val now = LocalDateTime.of(2026, 11, 13, 23, 0)
        assertEquals(LocalDateTime.of(2026, 11, 14, 7, 0), NightRules.nextWakeDateTime(now, LocalTime.of(7, 0)))
    }

    @Test
    fun `자정 이후에 시작하면 같은 날 아침에 깨운다`() {
        val now = LocalDateTime.of(2026, 11, 14, 1, 30)
        assertEquals(LocalDateTime.of(2026, 11, 14, 7, 0), NightRules.nextWakeDateTime(now, LocalTime.of(7, 0)))
    }

    @Test
    fun `기상 시각과 같은 시각에 시작하면 다음 날로 잡는다`() {
        val now = LocalDateTime.of(2026, 11, 14, 7, 0)
        assertEquals(LocalDateTime.of(2026, 11, 15, 7, 0), NightRules.nextWakeDateTime(now, LocalTime.of(7, 0)))
    }

    @Test
    fun `기상 일시가 되거나 지나면 도달이다`() {
        val wake = LocalDateTime.of(2026, 11, 14, 7, 0)
        assertFalse(NightRules.isWakeTimeReached(wake.minusSeconds(1), wake))
        assertTrue(NightRules.isWakeTimeReached(wake, wake))
        assertTrue(NightRules.isWakeTimeReached(wake.plusMinutes(5), wake))
    }

    // --- 테마 조명 ---

    @Test
    fun `테마 조명은 켜짐, 낮은 밝기, 테마마다 다른 색이다`() {
        val lights = PlaceType.entries.map { NightThemes.ambientFor(it) }
        assertTrue(lights.all { it.on && it.brightness == NightThresholds.THEME_BRIGHTNESS })
        assertNotEquals(lights[0].colorRgb, lights[1].colorRgb)
        assertNotEquals(lights[1].colorRgb, lights[2].colorRgb)
    }
}
