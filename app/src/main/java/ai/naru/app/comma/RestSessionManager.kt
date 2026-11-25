package ai.naru.app.comma

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface RestClock {
    fun elapsedMillis(): Long
    fun wallMillis(): Long
}
enum class RestPhase { READY, RESTING, WAKING, FINISHED }
enum class EndReason { TIMER, MANUAL, VIEWMODEL_CLEARED, DRIVING }
data class RestRecord(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val elapsedMillis: Long,
    val reason: EndReason
)
data class RestSession(
    val phase: RestPhase = RestPhase.READY,
    val selectedMinutes: Int = 15,
    val plannedMinutes: Int = 0,
    val remainingMillis: Long = 0,
    val expectedEndMillis: Long = 0,
    val confirmationRemainingMillis: Long = 0,
    val settings: CabinSettings = CabinSettings(),
    val completed: RestRecord? = null,
    val restoration: RestorationReport? = null
)

/** Main-thread confined; deadline uses monotonic time, independent of ticker delays. */
class RestSessionManager(
    private val clock: RestClock,
    private val cabin: CabinController,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() }
) {
    private val mutable = MutableStateFlow(RestSession())
    val state: StateFlow<RestSession> = mutable
    private var deadline = 0L
    private var startedElapsed = 0L
    private var startedWall = 0L
    private var id = ""
    private var previous: CabinSettings? = null
    val active get() = state.value.phase == RestPhase.RESTING || state.value.phase == RestPhase.WAKING

    fun selectMinutes(minutes: Int) {
        require(minutes in 1..180)
        if (!active) mutable.value = state.value.copy(selectedMinutes = minutes)
    }
    fun configure(settings: CabinSettings) {
        if (!active) mutable.value = state.value.copy(settings = settings.copy(
            brightness = settings.brightness.coerceIn(0.05f, 1f),
            lighting = settings.lighting.coerceIn(0f, 1f)))
    }
    fun start(): Boolean {
        if (active) return false
        previous = cabin.settings.value
        startedElapsed = clock.elapsedMillis()
        startedWall = clock.wallMillis()
        id = newId()
        deadline = startedElapsed + state.value.selectedMinutes * 60_000L
        cabin.apply(state.value.settings)
        mutable.value = state.value.copy(phase = RestPhase.RESTING,
            plannedMinutes = state.value.selectedMinutes, completed = null, restoration = null)
        tick()
        return true
    }
    fun tick(): RestRecord? {
        if (!active) return null
        val now = clock.elapsedMillis()
        val remaining = (deadline - now).coerceAtLeast(0)
        if (now >= deadline + 30_000L) return finish(EndReason.TIMER)
        val progress = (1f - remaining / 30_000f).coerceIn(0f, 1f)
        val base = state.value.settings
        val target = maxOf(base.brightness, previous?.brightness ?: base.brightness)
        cabin.apply(base.copy(brightness = base.brightness + (target - base.brightness) * progress))
        mutable.value = state.value.copy(
            phase = if (remaining == 0L) RestPhase.WAKING else RestPhase.RESTING,
            remainingMillis = remaining,
            expectedEndMillis = clock.wallMillis() + (deadline - now),
            confirmationRemainingMillis = if (remaining == 0L) (deadline + 30_000L - now) else 0)
        return null
    }
    /** At zero, a 30-second confirmation window permits extending the same session. */
    fun extend(minutes: Int): Boolean {
        require(minutes == 5 || minutes == 10)
        if (!active || clock.elapsedMillis() >= deadline + 30_000L) return false
        deadline = maxOf(deadline, clock.elapsedMillis()) + minutes * 60_000L
        mutable.value = state.value.copy(plannedMinutes = state.value.plannedMinutes + minutes)
        tick()
        return true
    }
    /** Completed record was already submitted by lifecycle; never clears application history. */
    fun prepareNext(): Boolean {
        if (state.value.phase != RestPhase.FINISHED) return false
        mutable.value = RestSession(selectedMinutes=state.value.selectedMinutes,settings=state.value.settings)
        return true
    }

    fun finish(reason: EndReason = EndReason.MANUAL): RestRecord? {
        if (!active) return null
        val elapsed = (clock.elapsedMillis() - startedElapsed).coerceAtLeast(0)
        val effectiveReason = if (clock.elapsedMillis() >= deadline) EndReason.TIMER else reason
        val actual = if (effectiveReason == EndReason.TIMER) minOf(elapsed, deadline - startedElapsed) else elapsed
        val record = RestRecord(id, startedWall, startedWall + actual,
            state.value.plannedMinutes, actual, effectiveReason)
        val restoration = restoreAppSettings(cabin, previous)
        previous = null
        mutable.value = state.value.copy(phase = RestPhase.FINISHED, remainingMillis = 0,
            confirmationRemainingMillis = 0, completed = record, restoration = restoration)
        return record
    }
}
