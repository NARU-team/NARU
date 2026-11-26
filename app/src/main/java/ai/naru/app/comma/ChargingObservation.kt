package ai.naru.app.comma

enum class ChargingState { CHARGING, NOT_CHARGING, UNKNOWN }

enum class EnergyDataQuality {
    VERIFIED_NOT_CHARGING,
    PORT_ONLY_VERIFIED,
    CHARGING_DETECTED,
    PORT_CONNECTED,
    OBSERVATION_INCOMPLETE,
    UNSUPPORTED,
    PERMISSION_DENIED,
    READ_FAILED,
    UNKNOWN,
}

data class ChargingSnapshot(
    val state: ChargingState = ChargingState.UNKNOWN,
    val portConnected: Boolean? = null,
    val batteryEnergyWh: Float? = null,
    val usableBatteryCapacityWh: Float? = null,
    val instantaneousChargeRateMilliwatts: Float? = null,
    val availability: ChargingSourceAvailability = ChargingSourceAvailability.SUPPORTED,
    val chargeStateSupported: Boolean = false,
    val chargePortSupported: Boolean = false,
    val observedAtMillis: Long,
)

data class ChargingObservation(
    val stateAtStart: ChargingState = ChargingState.UNKNOWN,
    val stateAtEnd: ChargingState = ChargingState.UNKNOWN,
    val portConnectedDuringRest: Boolean? = null,
    val chargingDetectedDuringRest: Boolean? = null,
    val complete: Boolean = false,
    val quality: EnergyDataQuality = EnergyDataQuality.UNKNOWN,
)

class ChargingObservationTracker {
    private var first: ChargingSnapshot? = null
    private var last: ChargingSnapshot? = null
    private var chargingSeen = false
    private var portSeen = false
    private var hasUnknownState = false
    private var hasObservationGap = false

    fun start(snapshot: ChargingSnapshot) {
        first = snapshot
        observe(snapshot)
    }

    fun observe(snapshot: ChargingSnapshot) {
        if (first == null) first = snapshot
        if (last != null && snapshot.observedAtMillis < last!!.observedAtMillis) {
            hasObservationGap = true
        }
        last = snapshot
        chargingSeen = chargingSeen || snapshot.state == ChargingState.CHARGING
        portSeen = portSeen || snapshot.portConnected == true
        hasUnknownState = hasUnknownState ||
            (snapshot.chargeStateSupported && snapshot.state == ChargingState.UNKNOWN) ||
            (snapshot.chargePortSupported && snapshot.portConnected == null) ||
            snapshot.availability != ChargingSourceAvailability.SUPPORTED
    }

    fun markObservationGap() {
        hasObservationGap = true
    }

    fun finish(snapshot: ChargingSnapshot): ChargingObservation {
        observe(snapshot)
        val complete = first != null && last != null &&
            first?.chargeStateSupported == true && last?.chargeStateSupported == true &&
            first?.chargePortSupported == true && last?.chargePortSupported == true &&
            !hasUnknownState && !hasObservationGap
        val quality = when {
            last?.availability == ChargingSourceAvailability.UNSUPPORTED -> EnergyDataQuality.UNSUPPORTED
            last?.availability == ChargingSourceAvailability.PERMISSION_DENIED -> EnergyDataQuality.PERMISSION_DENIED
            last?.availability == ChargingSourceAvailability.READ_FAILED ||
                last?.availability == ChargingSourceAvailability.DISCONNECTED -> EnergyDataQuality.READ_FAILED
            chargingSeen -> EnergyDataQuality.CHARGING_DETECTED
            portSeen -> EnergyDataQuality.PORT_CONNECTED
            !hasObservationGap &&
                first?.availability == ChargingSourceAvailability.SUPPORTED &&
                last?.availability == ChargingSourceAvailability.SUPPORTED &&
                first?.chargeStateSupported == false &&
                last?.chargeStateSupported == false &&
                first?.chargePortSupported == true &&
                last?.chargePortSupported == true &&
                first?.portConnected == false &&
                last?.portConnected == false -> EnergyDataQuality.PORT_ONLY_VERIFIED
            !complete -> EnergyDataQuality.OBSERVATION_INCOMPLETE
            first?.state == ChargingState.NOT_CHARGING &&
                last?.state == ChargingState.NOT_CHARGING &&
                first?.portConnected == false &&
                last?.portConnected == false -> EnergyDataQuality.VERIFIED_NOT_CHARGING
            else -> EnergyDataQuality.UNKNOWN
        }
        return ChargingObservation(
            stateAtStart = first?.state ?: ChargingState.UNKNOWN,
            stateAtEnd = last?.state ?: ChargingState.UNKNOWN,
            portConnectedDuringRest = if (first == null) null else portSeen,
            chargingDetectedDuringRest = if (first == null) null else chargingSeen,
            complete = complete,
            quality = quality,
        )
    }
}
