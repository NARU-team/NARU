package ai.naru.app.comma

enum class RecoveryKind {
    SAME_BOOT,
    PROCESS_RESTART,
    DEVICE_REBOOT,
    SYSTEM_TIME_CHANGED,
    EXPIRED,
    INVALID,
}

data class RestRecoveryDecision(
    val kind: RecoveryKind,
    val restoredDeadlineElapsed: Long? = null,
    val shouldRestoreSettingsOnly: Boolean = false,
)

fun decideRestRecovery(
    session: PersistedRestSession,
    nowWallMillis: Long,
    nowElapsedMillis: Long,
): RestRecoveryDecision {
    if (session.sessionId.isBlank() ||
        session.phase !in setOf(RestPhase.RESTING, RestPhase.WAKING) ||
        session.endAtWallMillis <= session.startedAtWallMillis ||
        session.endAtWallMillis - session.startedAtWallMillis > MAX_SESSION_MILLIS
    ) {
        return RestRecoveryDecision(RecoveryKind.INVALID, shouldRestoreSettingsOnly = true)
    }

    val wallRemaining = session.endAtWallMillis - nowWallMillis
    if (wallRemaining < -CONFIRMATION_WINDOW_MILLIS) {
        return RestRecoveryDecision(RecoveryKind.EXPIRED, shouldRestoreSettingsOnly = true)
    }

    val rebooted = nowElapsedMillis + ELAPSED_TOLERANCE_MILLIS < session.savedAtElapsedMillis
    if (rebooted) {
        return RestRecoveryDecision(
            kind = RecoveryKind.DEVICE_REBOOT,
            restoredDeadlineElapsed = nowElapsedMillis + wallRemaining.coerceAtLeast(0L),
        )
    }

    val savedBootOrigin = session.savedAtWallMillis - session.savedAtElapsedMillis
    val currentBootOrigin = nowWallMillis - nowElapsedMillis
    val originShift = kotlin.math.abs(savedBootOrigin - currentBootOrigin)
    if (originShift > CLOCK_SHIFT_TOLERANCE_MILLIS) {
        return RestRecoveryDecision(
            kind = RecoveryKind.SYSTEM_TIME_CHANGED,
            shouldRestoreSettingsOnly = true,
        )
    }

    val deadline = session.deadlineElapsedMillis
    if (nowElapsedMillis >= deadline + CONFIRMATION_WINDOW_MILLIS) {
        return RestRecoveryDecision(RecoveryKind.EXPIRED, shouldRestoreSettingsOnly = true)
    }
    return RestRecoveryDecision(
        kind = if (nowElapsedMillis == session.savedAtElapsedMillis) {
            RecoveryKind.SAME_BOOT
        } else {
            RecoveryKind.PROCESS_RESTART
        },
        restoredDeadlineElapsed = deadline,
    )
}

private const val CONFIRMATION_WINDOW_MILLIS = 30_000L
private const val ELAPSED_TOLERANCE_MILLIS = 2_000L
private const val CLOCK_SHIFT_TOLERANCE_MILLIS = 2 * 60_000L
private const val MAX_SESSION_MILLIS = 4 * 60 * 60_000L
