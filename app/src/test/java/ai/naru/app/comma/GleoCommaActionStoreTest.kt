package ai.naru.app.comma

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoCommaActionStoreTest {
    @Test
    fun `같은 요청 ID는 중복 수신하지 않는다`() {
        val id = "test-" + System.nanoTime()
        val action = GleoCommaAction(
            GleoCommaContract.ACTION_EXTEND_REST,
            id,
            mapOf("minutes" to 5),
        )

        assertTrue(GleoCommaActionStore.accept(action))
        assertFalse(GleoCommaActionStore.accept(action))
        GleoCommaActionStore.consume(id)
        assertNull(GleoCommaActionStore.actions.value)
    }

    @Test
    fun `만료된 요청은 쉼표 화면을 열지 않는다`() {
        val action = GleoCommaAction(
            GleoCommaContract.ACTION_EXTEND_REST,
            "expired-" + System.nanoTime(),
            mapOf("minutes" to 5),
            expiresAtWallMillis = 100L,
        )

        assertEquals(
            GleoNavigationDecision.DISCARD,
            gleoNavigationDecision(action, campingIdle = true, nowWallMillis = 100L),
        )
    }

    @Test
    fun `차박 진행 중인 요청은 쉼표 화면을 열지 않는다`() {
        val action = GleoCommaAction(
            GleoCommaContract.ACTION_PROPOSE_SETTINGS,
            "camping-" + System.nanoTime(),
            mapOf("durationMinutes" to 20),
            expiresAtWallMillis = 1_000L,
        )

        assertEquals(
            GleoNavigationDecision.DISCARD,
            gleoNavigationDecision(action, campingIdle = false, nowWallMillis = 100L),
        )
    }

    @Test
    fun `활성 휴식이 없는 연장 요청은 쉼표 화면을 열지 않는다`() {
        val action = GleoCommaAction(
            GleoCommaContract.ACTION_EXTEND_REST,
            "extension-" + System.nanoTime(),
            mapOf("minutes" to 5),
            expiresAtWallMillis = 1_000L,
        )

        assertEquals(
            GleoNavigationDecision.DISCARD,
            gleoNavigationDecision(
                action,
                campingIdle = true,
                activeRestPhase = null,
                nowWallMillis = 100L,
            ),
        )
        assertEquals(
            GleoNavigationDecision.OPEN_COMMA,
            gleoNavigationDecision(
                action,
                campingIdle = true,
                activeRestPhase = RestPhase.RESTING,
                nowWallMillis = 100L,
            ),
        )
    }

    @Test
    fun `유효한 요청은 차박 대기 상태에서만 쉼표 화면을 연다`() {
        val action = GleoCommaAction(
            GleoCommaContract.ACTION_PROPOSE_SETTINGS,
            "valid-" + System.nanoTime(),
            mapOf("durationMinutes" to 20),
            expiresAtWallMillis = 1_000L,
        )

        assertEquals(
            GleoNavigationDecision.OPEN_COMMA,
            gleoNavigationDecision(action, campingIdle = true, nowWallMillis = 100L),
        )
    }
}
