package ai.naru.app.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionOwner { NONE, CAMPING, COMMA }

/**
 * 프로세스 안에서 차량 제어 세션의 단일 소유자를 관리한다.
 * 다른 모드의 세션을 종료하지 않으며 acquire 실패만 반환한다.
 */
class SessionOwnershipManager {
    private val mutableOwner = MutableStateFlow(SessionOwner.NONE)
    val owner: StateFlow<SessionOwner> = mutableOwner.asStateFlow()

    @Synchronized
    fun acquire(requester: SessionOwner): Boolean {
        require(requester != SessionOwner.NONE)
        val current = mutableOwner.value
        if (current != SessionOwner.NONE && current != requester) return false
        mutableOwner.value = requester
        return true
    }

    @Synchronized
    fun release(requester: SessionOwner) {
        if (mutableOwner.value == requester) mutableOwner.value = SessionOwner.NONE
    }
}
