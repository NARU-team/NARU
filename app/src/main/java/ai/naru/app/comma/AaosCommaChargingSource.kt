package ai.naru.app.comma

import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.EvChargeState
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ChargingSourceAvailability {
    SUPPORTED, UNSUPPORTED, PERMISSION_DENIED, READ_FAILED, DISCONNECTED
}

internal fun chargingStateFromAaos(value: Int?): ChargingState = when (value) {
    EvChargeState.STATE_CHARGING -> ChargingState.CHARGING
    EvChargeState.STATE_NOT_CHARGING,
    EvChargeState.STATE_FULLY_CHARGED -> ChargingState.NOT_CHARGING
    else -> ChargingState.UNKNOWN
}

internal fun chargingStateFromRate(rateMilliwatts: Float?): ChargingState = when {
    rateMilliwatts == null || rateMilliwatts == 0f -> ChargingState.UNKNOWN
    rateMilliwatts > 0f -> ChargingState.CHARGING
    else -> ChargingState.NOT_CHARGING
}

interface CommaChargingSource {
    val state: StateFlow<ChargingSnapshot>
}

class AaosCommaChargingSource(
    private val manager: CarPropertyManager?,
    applicationScope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CommaChargingSource {
    private val mutableState = MutableStateFlow(
        ChargingSnapshot(
            observedAtMillis = nowMillis(),
            availability = if (manager == null) {
                ChargingSourceAvailability.DISCONNECTED
            } else {
                ChargingSourceAvailability.READ_FAILED
            },
        ),
    )
    override val state: StateFlow<ChargingSnapshot> = mutableState.asStateFlow()

    init {
        applicationScope.launch { connect() }
    }

    private fun connect() {
        val propertyManager = manager ?: return
        val supported = OBSERVED_PROPERTIES.filter { propertyId ->
            runCatching { propertyManager.getCarPropertyConfig(propertyId) != null }
                .getOrDefault(false)
        }
        Log.i(TAG, "지원 충전 property=" + supported.joinToString())
        mutableState.value = mutableState.value.copy(
            chargeStateSupported = VehiclePropertyIds.EV_CHARGE_STATE in supported ||
                VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE in supported,
            chargePortSupported = VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED in supported,
            observedAtMillis = nowMillis(),
        )
        if (supported.isEmpty()) {
            publish(availability = ChargingSourceAvailability.UNSUPPORTED)
            return
        }
        try {
            supported.forEach(::readInitial)
            supported.forEach { propertyId ->
                @Suppress("DEPRECATION")
                propertyManager.registerCallback(
                    callback,
                    propertyId,
                    if (propertyId == VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE ||
                        propertyId == VehiclePropertyIds.EV_BATTERY_LEVEL
                    ) {
                        CarPropertyManager.SENSOR_RATE_NORMAL
                    } else {
                        CarPropertyManager.SENSOR_RATE_ONCHANGE
                    },
                )
            }
            publish(availability = ChargingSourceAvailability.SUPPORTED)
        } catch (_: SecurityException) {
            publish(availability = ChargingSourceAvailability.PERMISSION_DENIED)
        } catch (_: Exception) {
            publish(availability = ChargingSourceAvailability.READ_FAILED)
        }
    }

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            applyValue(value.propertyId, value.value)
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            publish(availability = ChargingSourceAvailability.READ_FAILED)
        }
    }

    private fun readInitial(propertyId: Int) {
        val value = manager?.getProperty<Any>(propertyId, GLOBAL_AREA)?.value ?: return
        applyValue(propertyId, value)
    }

    private fun applyValue(propertyId: Int, value: Any?) {
        val old = mutableState.value
        val updated = when (propertyId) {
            VehiclePropertyIds.EV_CHARGE_STATE -> old.copy(
                state = chargingStateFromAaos(value as? Int),
            )
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED -> old.copy(portConnected = value as? Boolean)
            VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE -> {
                val rate = (value as? Number)?.toFloat()
                old.copy(
                    instantaneousChargeRateMilliwatts = rate,
                    state = if (old.state != ChargingState.UNKNOWN) {
                        old.state
                    } else {
                        chargingStateFromRate(rate)
                    },
                )
            }
            VehiclePropertyIds.EV_BATTERY_LEVEL -> old.copy(
                batteryEnergyWh = (value as? Number)?.toFloat(),
            )
            VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY -> old.copy(
                usableBatteryCapacityWh = (value as? Number)?.toFloat(),
            )
            else -> old
        }
        mutableState.value = updated.copy(
            observedAtMillis = nowMillis(),
            availability = ChargingSourceAvailability.SUPPORTED,
        )
        Log.i(TAG, "충전 property=" + propertyId + " state=" + mutableState.value)
    }

    private fun publish(availability: ChargingSourceAvailability) {
        mutableState.value = mutableState.value.copy(
            availability = availability,
            observedAtMillis = nowMillis(),
        )
    }

    private companion object {
        const val TAG = "CommaChargingSource"
        const val GLOBAL_AREA = 0
        val OBSERVED_PROPERTIES = listOf(
            VehiclePropertyIds.EV_CHARGE_STATE,
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
            VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
            VehiclePropertyIds.EV_BATTERY_LEVEL,
            VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY,
        )
    }
}
