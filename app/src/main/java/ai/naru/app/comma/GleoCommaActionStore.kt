package ai.naru.app.comma

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GleoCommaAction(
    val action: String,
    val requestId: String,
    val parameters: Map<String, Any?>,
    val expiresAtWallMillis: Long = System.currentTimeMillis() + ACTION_TTL_MILLIS,
) {
    fun isExpired(nowWallMillis: Long = System.currentTimeMillis()): Boolean =
        nowWallMillis >= expiresAtWallMillis

    private companion object {
        // Gleo callback는 Activity에서 7초 안에 asking으로 응답한다. 이후 앱 내 승인은 별도 유효 시간이다.
        const val ACTION_TTL_MILLIS = 2 * 60_000L
    }
}

enum class GleoNavigationDecision { OPEN_COMMA, DISCARD }

fun gleoNavigationDecision(
    action: GleoCommaAction,
    campingIdle: Boolean,
    activeRestPhase: RestPhase? = null,
    nowWallMillis: Long = System.currentTimeMillis(),
): GleoNavigationDecision {
    val validExtension = action.action != GleoCommaContract.ACTION_EXTEND_REST ||
        activeRestPhase in setOf(RestPhase.RESTING, RestPhase.WAKING)
    return if (!action.isExpired(nowWallMillis) && campingIdle && validExtension) {
        GleoNavigationDecision.OPEN_COMMA
    } else {
        GleoNavigationDecision.DISCARD
    }
}

object GleoCommaActionStore {
    private val mutableActions = MutableStateFlow<GleoCommaAction?>(null)
    val actions = mutableActions.asStateFlow()
    private val acceptedIds = LinkedHashSet<String>()

    @Synchronized
    fun accept(action: GleoCommaAction): Boolean {
        if (action.isExpired() || !acceptedIds.add(action.requestId)) return false
        while (acceptedIds.size > MAX_RECENT_IDS) {
            acceptedIds.remove(acceptedIds.first())
        }
        mutableActions.value = action
        return true
    }

    @Synchronized
    fun consume(requestId: String) {
        if (mutableActions.value?.requestId == requestId) mutableActions.value = null
    }

    private const val MAX_RECENT_IDS = 32
}
