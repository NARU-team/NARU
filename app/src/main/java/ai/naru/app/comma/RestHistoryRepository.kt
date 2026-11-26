package ai.naru.app.comma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface RestHistoryRepository {
    suspend fun save(record: RestRecord)
    suspend fun recent(): List<RestRecord>
    suspend fun updateRestoration(sessionId: String, report: RestorationReport): Boolean = false
    suspend fun submitFeedback(
        sessionId: String,
        satisfaction: Int,
        temperatureFeedback: TemperatureFeedback,
        submittedAt: Long,
    ): Boolean = false
}
class RoomRestHistoryRepository(private val dao: RestDao) : RestHistoryRepository {
    override suspend fun save(record: RestRecord) = withContext(Dispatchers.IO) {
        dao.save(RestRecordEntity().apply {
            id = record.id
            startedAt = record.startedAt
            endedAt = record.endedAt
            plannedMinutes = record.plannedMinutes
            elapsedMillis = record.elapsedMillis
            reason = record.reason.name
            startBatteryPercent = record.startBatteryPercent
            endBatteryPercent = record.endBatteryPercent
            startIndoorCelsius = record.startIndoorCelsius
            endIndoorCelsius = record.endIndoorCelsius
            outdoorCelsius = record.outdoorCelsius
            targetClimateCelsius = record.targetClimateCelsius
            screenBrightness = record.screenBrightness
            lightingBrightness = record.lightingBrightness
            sound = record.sound?.name
            feedback = record.feedback
            temperatureFeedback = record.temperatureFeedback?.name
            feedbackSubmittedAt = record.feedbackSubmittedAt
            source = record.source.name
            restoreSucceeded = record.restoreSucceeded
            hvacPowerRestore = record.hvacPowerRestore.name
            climateTemperatureRestore = record.climateTemperatureRestore.name
            ambientLightRestore = record.ambientLightRestore.name
            screenBrightnessRestore = record.screenBrightnessRestore.name
            natureSoundRestore = record.natureSoundRestore.name
            alarmRestore = record.alarmRestore.name
            chargingStateAtStart = record.chargingStateAtStart.name
            chargingStateAtEnd = record.chargingStateAtEnd.name
            chargePortConnectedDuringRest = record.chargePortConnectedDuringRest
            chargingDetectedDuringRest = record.chargingDetectedDuringRest
            chargingObservationComplete = record.chargingObservationComplete
            energyDataQuality = record.energyDataQuality.name
        })
        Unit
    }
    override suspend fun updateRestoration(
        sessionId: String,
        report: RestorationReport,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.updateRestoration(
            sessionId,
            report.outcomes.all { it == RestorationOutcome.SUCCESS },
            report.hvacPower.name,
            report.climateTemperature.name,
            report.ambientLight.name,
            report.screenBrightness.name,
            report.natureSound.name,
            report.alarm.name,
        ) == 1
    }

    override suspend fun submitFeedback(
        sessionId: String,
        satisfaction: Int,
        temperatureFeedback: TemperatureFeedback,
        submittedAt: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.saveFeedbackOnce(sessionId, satisfaction, temperatureFeedback.name, submittedAt) == 1
    }

    override suspend fun recent(): List<RestRecord> = withContext(Dispatchers.IO) {
        dao.recent().map { entity ->
            RestRecord(
                id = entity.id,
                startedAt = entity.startedAt,
                endedAt = entity.endedAt,
                plannedMinutes = entity.plannedMinutes,
                elapsedMillis = entity.elapsedMillis,
                reason = EndReason.valueOf(entity.reason),
                startBatteryPercent = entity.startBatteryPercent,
                endBatteryPercent = entity.endBatteryPercent,
                startIndoorCelsius = entity.startIndoorCelsius,
                endIndoorCelsius = entity.endIndoorCelsius,
                outdoorCelsius = entity.outdoorCelsius,
                targetClimateCelsius = entity.targetClimateCelsius,
                screenBrightness = entity.screenBrightness,
                lightingBrightness = entity.lightingBrightness,
                sound = entity.sound?.let(RestSound::valueOf),
                feedback = entity.feedback,
                temperatureFeedback = entity.temperatureFeedback?.let {
                    runCatching { TemperatureFeedback.valueOf(it) }.getOrNull()
                },
                feedbackSubmittedAt = entity.feedbackSubmittedAt,
                source = runCatching { RecordSource.valueOf(entity.source) }
                    .getOrDefault(RecordSource.UNKNOWN),
                restoreSucceeded = entity.restoreSucceeded,
                hvacPowerRestore = entity.hvacPowerRestore.toRestorationOutcome(),
                climateTemperatureRestore = entity.climateTemperatureRestore.toRestorationOutcome(),
                ambientLightRestore = entity.ambientLightRestore.toRestorationOutcome(),
                screenBrightnessRestore = entity.screenBrightnessRestore.toRestorationOutcome(),
                natureSoundRestore = entity.natureSoundRestore.toRestorationOutcome(),
                alarmRestore = entity.alarmRestore.toRestorationOutcome(),
                chargingStateAtStart = entity.chargingStateAtStart.toChargingState(),
                chargingStateAtEnd = entity.chargingStateAtEnd.toChargingState(),
                chargePortConnectedDuringRest = entity.chargePortConnectedDuringRest,
                chargingDetectedDuringRest = entity.chargingDetectedDuringRest,
                chargingObservationComplete = entity.chargingObservationComplete,
                energyDataQuality = entity.energyDataQuality.toEnergyDataQuality(),
            )
        }
    }
}

private fun String.toRestorationOutcome(): RestorationOutcome =
    runCatching { RestorationOutcome.valueOf(this) }.getOrDefault(RestorationOutcome.UNKNOWN)

private fun String.toChargingState(): ChargingState =
    runCatching { ChargingState.valueOf(this) }.getOrDefault(ChargingState.UNKNOWN)

private fun String.toEnergyDataQuality(): EnergyDataQuality =
    runCatching { EnergyDataQuality.valueOf(this) }.getOrDefault(EnergyDataQuality.UNKNOWN)
