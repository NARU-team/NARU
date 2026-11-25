package ai.naru.app.camping.night

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ThemeTimerTest {

    private val start = LocalDateTime.of(2026, 11, 14, 23, 20)

    @Test
    fun `화살표는 30·60·90분 사이에서만 움직인다`() {
        assertEquals(90, ThemeTimer.move(60, 1))
        assertEquals(90, ThemeTimer.move(90, 1))
        assertEquals(30, ThemeTimer.move(60, -1))
        assertEquals(30, ThemeTimer.move(30, -1))
        // 목록에 없는 값이면 기본값에서 움직인다
        assertEquals(90, ThemeTimer.move(45, 1))
    }

    @Test
    fun `종료 시각은 시작 시각에 고른 분을 더한 값이고 그 시각부터 끝난 것으로 본다`() {
        val end = ThemeTimer.endsAt(start, 60)
        assertEquals(LocalDateTime.of(2026, 11, 15, 0, 20), end)
        assertFalse(ThemeTimer.isOver(end.minusSeconds(1), end))
        assertTrue(ThemeTimer.isOver(end, end))
        assertFalse(ThemeTimer.isOver(end, null))
    }

    @Test
    fun `남은 분은 올림하고 지나면 0`() {
        val end = ThemeTimer.endsAt(start, 60)
        assertEquals(60, ThemeTimer.minutesLeft(start, end))
        assertEquals(42, ThemeTimer.minutesLeft(end.minusMinutes(41).minusSeconds(30), end))
        assertEquals(0, ThemeTimer.minutesLeft(end.plusMinutes(1), end))
    }
}
