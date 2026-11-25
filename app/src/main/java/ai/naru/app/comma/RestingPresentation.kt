package ai.naru.app.comma

internal enum class CommaDestination { SETTINGS, RESTING, WAKING, FINISHED, LEGACY }

/** Pure routing: rendering never starts, extends or finishes a session. */
internal fun commaDestination(ui: RestUiState, settingsRequested: Boolean): CommaDestination =
    when {
        ui.session.phase == RestPhase.RESTING -> CommaDestination.RESTING
        ui.session.phase == RestPhase.WAKING -> CommaDestination.WAKING
        ui.session.phase == RestPhase.FINISHED -> CommaDestination.FINISHED
        !ui.active && (ui.session.phase == RestPhase.READY || (!ui.isDemo && settingsRequested)) ->
            CommaDestination.SETTINGS
        else -> CommaDestination.LEGACY
    }

/**
 * Remaining fraction, not elapsed fraction. The denominator is the session's cumulative
 * planned minutes, including every accepted extension. Confirmation time is excluded.
 * Uses the manager's monotonic-deadline snapshot; never wall time or a Compose-local ticker.
 * Extending in WAKING adds the extension to planned minutes; the 30s confirmation window
 * is not counted as rest. Recomposition/window resizing cannot reset this value.
 */
internal fun restingRemainingFraction(session: RestSession): Float {
    if (session.phase != RestPhase.RESTING || session.plannedMinutes <= 0) return 0f
    return (session.remainingMillis.toDouble() / (session.plannedMinutes * 60_000.0))
        .coerceIn(0.0, 1.0).toFloat()
}

/** Check live state at event delivery, so a stale RESTING callback cannot end WAKING. */
internal fun dispatchRestingFinish(currentPhase: () -> RestPhase, finish: () -> Unit): Boolean {
    if (currentPhase() != RestPhase.RESTING) return false
    finish() // ViewModel and manager are main-thread confined and finish synchronously.
    return true
}
