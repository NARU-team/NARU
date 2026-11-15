package com.naru.app.comma

/** Uses the manager snapshot. No UI entry timestamp or local countdown is created. */
internal fun wakingConfirmationSeconds(session: RestSession): Long =
    if (session.phase == RestPhase.WAKING)
        (session.confirmationRemainingMillis.coerceIn(0,30_000) + 999) / 1000 else 0

/** Check live phase before delegating to existing extend/finish and history paths. */
internal fun dispatchWakingAction(currentPhase: () -> RestPhase, action: () -> Unit): Boolean {
    if (currentPhase() != RestPhase.WAKING) return false
    action()
    return true
}
