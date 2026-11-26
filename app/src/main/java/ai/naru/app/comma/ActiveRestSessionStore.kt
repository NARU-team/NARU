package ai.naru.app.comma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PersistedRestSession(
    val sessionId: String,
    val phase: RestPhase,
    val startedAtWallMillis: Long,
    val endAtWallMillis: Long,
    val startedAtElapsedMillis: Long,
    val deadlineElapsedMillis: Long,
    val savedAtWallMillis: Long,
    val savedAtElapsedMillis: Long,
    val selectedMinutes: Int,
    val plannedMinutes: Int,
    val extensionMinutes: Int,
    val settings: CabinSettings,
    val approvedClimateCelsius: Float?,
    val restoreState: String,
    val source: RecordSource,
    val alarmIssued: Boolean,
    val completionRecorded: Boolean,
    val vehicleBackup: VehicleBackup? = null,
)

class ActiveRestSessionStore(private val dao: RestDao) {
    suspend fun save(session: PersistedRestSession) = withContext(Dispatchers.IO) {
        dao.saveActiveSession(session.toEntity())
    }

    suspend fun load(): PersistedRestSession? = withContext(Dispatchers.IO) {
        dao.getActiveSession()?.toDomain()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.deleteActiveSession()
    }

    suspend fun markAlarmIssuedOnce(): Boolean = withContext(Dispatchers.IO) {
        dao.markAlarmIssuedOnce() == 1
    }

    suspend fun markCompletionRecordedOnce(): Boolean = withContext(Dispatchers.IO) {
        dao.markCompletionRecordedOnce() == 1
    }
}

private fun PersistedRestSession.toEntity() = ActiveRestSessionEntity().also { entity ->
    entity.sessionId = sessionId
    entity.phase = phase.name
    entity.startedAtWallMillis = startedAtWallMillis
    entity.endAtWallMillis = endAtWallMillis
    entity.startedAtElapsedMillis = startedAtElapsedMillis
    entity.deadlineElapsedMillis = deadlineElapsedMillis
    entity.savedAtWallMillis = savedAtWallMillis
    entity.savedAtElapsedMillis = savedAtElapsedMillis
    entity.selectedMinutes = selectedMinutes
    entity.plannedMinutes = plannedMinutes
    entity.extensionMinutes = extensionMinutes
    entity.approvedBrightness = settings.brightness
    entity.approvedLighting = settings.lighting
    entity.sound = settings.sound.name
    entity.approvedClimateCelsius = approvedClimateCelsius
    entity.restoreState = restoreState
    entity.source = source.name
    entity.alarmIssued = alarmIssued
    entity.completionRecorded = completionRecorded
    vehicleBackup?.let { backup ->
        entity.backupHvacPower = backup.hvacPower
        entity.backupClimateCelsius = backup.targetCelsius
        entity.backupAmbientOn = backup.ambientOn
        entity.backupAmbientBrightness = backup.ambientBrightness
        entity.backupAmbientColor = backup.ambientColorRgb
    }
}

private fun ActiveRestSessionEntity.toDomain() = PersistedRestSession(
    sessionId = sessionId,
    phase = runCatching { RestPhase.valueOf(phase) }.getOrDefault(RestPhase.RESTING),
    startedAtWallMillis = startedAtWallMillis,
    endAtWallMillis = endAtWallMillis,
    startedAtElapsedMillis = startedAtElapsedMillis,
    deadlineElapsedMillis = deadlineElapsedMillis,
    savedAtWallMillis = savedAtWallMillis,
    savedAtElapsedMillis = savedAtElapsedMillis,
    selectedMinutes = selectedMinutes,
    plannedMinutes = plannedMinutes,
    extensionMinutes = extensionMinutes,
    settings = CabinSettings(
        brightness = approvedBrightness,
        lighting = approvedLighting,
        sound = runCatching { RestSound.valueOf(sound) }.getOrDefault(RestSound.SILENT),
    ),
    approvedClimateCelsius = approvedClimateCelsius,
    restoreState = restoreState,
    source = runCatching { RecordSource.valueOf(source) }.getOrDefault(RecordSource.UNKNOWN),
    alarmIssued = alarmIssued,
    completionRecorded = completionRecorded,
    vehicleBackup = VehicleBackup(
        hvacPower = backupHvacPower,
        targetCelsius = backupClimateCelsius,
        ambientOn = backupAmbientOn,
        ambientBrightness = backupAmbientBrightness,
        ambientColorRgb = backupAmbientColor,
    ).takeIf { it.hasAnyValue },
)
