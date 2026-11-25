package com.naru.app.comma

/** The single real-session completion path, including ViewModel disposal. */
class RestSessionLifecycle(
    private val manager: RestSessionManager,
    private val history: RestHistoryStore
) {
    fun tick() { manager.tick()?.let(history::enqueue) }
    fun finish() { manager.finish()?.let(history::enqueue) }
    fun finishForDriving() { manager.finish(EndReason.DRIVING)?.let(history::enqueue) }
    fun close() { manager.finish(EndReason.VIEWMODEL_CLEARED)?.let(history::enqueue) }
}
