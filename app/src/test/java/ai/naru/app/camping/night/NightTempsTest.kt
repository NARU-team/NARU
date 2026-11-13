package ai.naru.app.camping.night

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NightTempsTest {
    private val t0 = LocalDateTime.of(2026, 11, 13, 22, 0)

    @Test
    fun `10분마다 기록한다`() {
        assertTrue(NightTemps.isSampleDue(null, t0))
        assertFalse(NightTemps.isSampleDue(t0, t0.plusMinutes(9)))
        assertTrue(NightTemps.isSampleDue(t0, t0.plusMinutes(10)))
    }

    @Test
    fun `실내 범위와 실외 최저, 기록이 하나뿐이면 범위는 모른다`() {
        val samples = listOf(TempSample(t0, 22f, 6f), TempSample(t0.plusMinutes(10), 20.5f, 4f), TempSample(t0.plusMinutes(20), null, 5f))
        val s = NightTemps.summarize(samples)
        assertEquals(20.5f..22f, s.cabinRange)
        assertEquals(4f, s.outsideMinC)
        assertEquals(2, s.cabinSamples.size)
        assertNull(NightTemps.summarize(listOf(TempSample(t0, 22f, null))).cabinRange)
    }
}
