package ai.naru.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOwnershipManagerTest {
    @Test
    fun `다른 모드는 활성 소유권을 빼앗을 수 없다`() {
        val manager = SessionOwnershipManager()

        assertTrue(manager.acquire(SessionOwner.CAMPING))
        assertFalse(manager.acquire(SessionOwner.COMMA))
        assertEquals(SessionOwner.CAMPING, manager.owner.value)

        manager.release(SessionOwner.COMMA)
        assertEquals(SessionOwner.CAMPING, manager.owner.value)

        manager.release(SessionOwner.CAMPING)
        assertTrue(manager.acquire(SessionOwner.COMMA))
    }
}
