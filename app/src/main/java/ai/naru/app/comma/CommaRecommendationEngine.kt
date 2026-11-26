package ai.naru.app.comma

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

enum class EnergyForecastConfidence { HIGH, LIMITED }

data class EnergyForecast(
    val minimumEndPercent: Int,
    val maximumEndPercent: Int,
    val sampleCount: Int,
    val limitation: String,
    val confidence: EnergyForecastConfidence,
)

fun forecastEnergy(
    currentBatteryPercent: Int?,
    plannedMinutes: Int,
    records: List<RestRecord>,
    currentOutdoorCelsius: Float? = null,
    targetClimateCelsius: Float? = null,
): EnergyForecast? {
    val current = currentBatteryPercent?.takeIf { it in 0..100 } ?: return null
    if (plannedMinutes !in 1..180) return null

    val comparable = records.asSequence()
        .filter { it.source == RecordSource.REAL }
        .filter { it.reason == EndReason.TIMER || it.reason == EndReason.MANUAL }
        .filter { it.chargingDetectedDuringRest == false }
        .filter { it.chargePortConnectedDuringRest == false }
        .filter { record ->
            currentOutdoorCelsius == null || record.outdoorCelsius == null ||
                kotlin.math.abs(record.outdoorCelsius - currentOutdoorCelsius) <= MAX_OUTDOOR_DELTA_C
        }
        .filter { record ->
            targetClimateCelsius == null || record.targetClimateCelsius == null ||
                kotlin.math.abs(record.targetClimateCelsius - targetClimateCelsius) <= MAX_TARGET_DELTA_C
        }
        .toList()

    val highRates = comparable.asSequence()
        .filter { it.energyDataQuality == EnergyDataQuality.VERIFIED_NOT_CHARGING }
        .filter { it.chargingObservationComplete }
        .filter { it.chargingStateAtStart == ChargingState.NOT_CHARGING }
        .filter { it.chargingStateAtEnd == ChargingState.NOT_CHARGING }
        .mapNotNull(::hourlySocDrop)
        .toList()
    if (highRates.size >= MIN_ENERGY_SAMPLES) {
        return energyForecast(
            current = current,
            plannedMinutes = plannedMinutes,
            rates = highRates,
            confidence = EnergyForecastConfidence.HIGH,
            limitation = "전 구간 비충전이 확인된 유사 외기·공조 실차 기록 기준이며 SOC 1% 측정 오차를 포함합니다.",
        )
    }

    val limitedRates = comparable.asSequence()
        .filter { it.energyDataQuality == EnergyDataQuality.PORT_ONLY_VERIFIED }
        .filter { it.chargingStateAtStart == ChargingState.UNKNOWN }
        .filter { it.chargingStateAtEnd == ChargingState.UNKNOWN }
        .mapNotNull(::hourlySocDrop)
        .toList()
    if (limitedRates.size < MIN_ENERGY_SAMPLES) return null
    return energyForecast(
        current = current,
        plannedMinutes = plannedMinutes,
        rates = limitedRates,
        confidence = EnergyForecastConfidence.LIMITED,
        limitation = "충전 상태 API 미지원 차량에서 전 구간 포트 미연결만 확인한 참고값이며, 충전 여부를 확정하지 않습니다.",
    )
}

private fun hourlySocDrop(record: RestRecord): Double? {
    val start = record.startBatteryPercent ?: return null
    val end = record.endBatteryPercent ?: return null
    if (record.elapsedMillis < MIN_MEASURED_DURATION_MILLIS || start !in 0..100 || end !in 0..100) {
        return null
    }
    val drop = start - end
    if (drop < 0 || drop > MAX_REASONABLE_DROP_PERCENT) return null
    return drop.toDouble() * MILLIS_PER_HOUR / record.elapsedMillis
}

private fun energyForecast(
    current: Int,
    plannedMinutes: Int,
    rates: List<Double>,
    confidence: EnergyForecastConfidence,
    limitation: String,
): EnergyForecast {
    val projectedDrops = rates.map { it * plannedMinutes / 60.0 }
    return EnergyForecast(
        minimumEndPercent = (current - ceil(projectedDrops.max() + SOC_RESOLUTION_MARGIN))
            .toInt().coerceIn(0, 100),
        maximumEndPercent = (current - floor(projectedDrops.min() - SOC_RESOLUTION_MARGIN))
            .toInt().coerceIn(0, 100),
        sampleCount = rates.size,
        limitation = limitation,
        confidence = confidence,
    )
}

fun personalizedClimateTarget(records: List<RestRecord>): Float? {
    val preferred = records.asSequence()
        .filter { it.source == RecordSource.REAL }
        .filter { (it.feedback ?: 0) >= POSITIVE_FEEDBACK_MIN }
        .mapNotNull(RestRecord::targetClimateCelsius)
        .filter { it in MIN_CLIMATE_C..MAX_CLIMATE_C }
        .take(MAX_PERSONALIZATION_SAMPLES)
        .toList()
    if (preferred.size < MIN_PERSONALIZATION_SAMPLES) return null
    return (preferred.average() * 2.0).roundToInt().toFloat() / 2f
}

private const val MIN_ENERGY_SAMPLES = 3
private const val MIN_PERSONALIZATION_SAMPLES = 2
private const val MAX_PERSONALIZATION_SAMPLES = 5
private const val POSITIVE_FEEDBACK_MIN = 4
private const val MIN_MEASURED_DURATION_MILLIS = 10 * 60_000L
private const val MILLIS_PER_HOUR = 60 * 60_000.0
private const val MAX_REASONABLE_DROP_PERCENT = 50
private const val MAX_OUTDOOR_DELTA_C = 8f
private const val MAX_TARGET_DELTA_C = 3f
private const val SOC_RESOLUTION_MARGIN = 1.0
private const val MIN_CLIMATE_C = 16f
private const val MAX_CLIMATE_C = 30f
