package ai.naru.app.comma

import ai.naru.app.NaruApplication
import ai.naru.app.vehicle.PleosVehicleGateway
import ai.naru.app.vehicle.ReadOnlyCarPropertyManagerProvider
import ai.naru.app.vehicle.VehicleResult
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/** Pleos Emulator HVAC VHAL 수동 진단. 앱의 기존 차량 연결만 재사용한다. */
@RunWith(AndroidJUnit4::class)
class CommaHvacPropertyDiagnosticInstrumentedTest {
    @Test
    fun inspectAndExerciseHvacProperties() {
        val application = ApplicationProvider.getApplicationContext<NaruApplication>()
        inspectPleosGateway(application)
        val provider = application.vehicleGateway as? ReadOnlyCarPropertyManagerProvider
        assertNotNull("실제 Pleos VehicleGateway가 필요합니다.", provider)
        val manager = requireNotNull(provider?.readOnlyCarPropertyManager)

        logConfig(manager, VehiclePropertyIds.HVAC_POWER_ON)
        logConfig(manager, VehiclePropertyIds.HVAC_TEMPERATURE_SET)
        logConfig(manager, VehiclePropertyIds.HVAC_FAN_SPEED)

        val powerArea = firstArea(manager, VehiclePropertyIds.HVAC_POWER_ON)
        val temperatureAreas = areas(manager, VehiclePropertyIds.HVAC_TEMPERATURE_SET)
        val fanAreas = areas(manager, VehiclePropertyIds.HVAC_FAN_SPEED)
        Log.i(TAG, "resolved areas power=${powerArea.hex()} temperature=${temperatureAreas.hex()} fan=${fanAreas.hex()}")

        val originalPower = read<Boolean>(manager, VehiclePropertyIds.HVAC_POWER_ON, powerArea)
        val originalTemperatures = temperatureAreas.associateWith {
            read<Float>(manager, VehiclePropertyIds.HVAC_TEMPERATURE_SET, it)
        }
        fanAreas.forEach { read<Int>(manager, VehiclePropertyIds.HVAC_FAN_SPEED, it) }

        try {
            write(manager, VehiclePropertyIds.HVAC_POWER_ON, powerArea, true)
            Thread.sleep(500)
            val poweredOn = read<Boolean>(manager, VehiclePropertyIds.HVAC_POWER_ON, powerArea)
            if (poweredOn == true) {
                temperatureAreas.firstOrNull()?.let { area ->
                    val requested = originalTemperatures[area]?.coerceIn(15f, 30f) ?: 23f
                    write(manager, VehiclePropertyIds.HVAC_TEMPERATURE_SET, area, requested)
                    Thread.sleep(500)
                    read<Float>(manager, VehiclePropertyIds.HVAC_TEMPERATURE_SET, area)
                }
            } else {
                Log.w(TAG, "HVAC power did not become ON; temperature write intentionally skipped")
            }
        } finally {
            originalTemperatures.forEach { (area, value) ->
                if (value != null) write(manager, VehiclePropertyIds.HVAC_TEMPERATURE_SET, area, value)
            }
            if (originalPower != null) write(manager, VehiclePropertyIds.HVAC_POWER_ON, powerArea, originalPower)
        }
    }

    private fun inspectPleosGateway(application: NaruApplication) = runBlocking {
        val gateway = application.vehicleGateway
        if (gateway !is PleosVehicleGateway) {
            Log.w(TAG, "PleosVehicleGateway unavailable: ${gateway.javaClass.name}")
            return@runBlocking
        }
        val capabilities = gateway.capabilities()
        Log.i(TAG, "SDK capabilities=$capabilities")
        val originalPower = gateway.getHvacPower().also { Log.i(TAG, "SDK getPower=$it") }
        val originalTemperature = gateway.getTargetTemperature().also { Log.i(TAG, "SDK getTemperatureBefore=$it") }
        try {
            val powerWrite = gateway.setHvacPower(true)
            Log.i(TAG, "SDK setPowerOn=$powerWrite")
            Thread.sleep(500)
            val poweredOn = gateway.getHvacPower()
            Log.i(TAG, "SDK getPowerAfterOn=$poweredOn")
            if (poweredOn == VehicleResult.Success(true)) {
                val original = (originalTemperature as? VehicleResult.Success)?.value ?: 23f
                val requested = if (original <= 29.5f) original + 0.5f else original - 0.5f
                val temperatureWrite = gateway.setTargetTemperature(requested)
                Log.i(TAG, "SDK setTemperature($requested)=$temperatureWrite")
                Thread.sleep(3_000)
                Log.i(TAG, "SDK getTemperatureAfter3s=${gateway.getTargetTemperature()}")
            } else {
                Log.w(TAG, "SDK power did not become ON; SDK temperature write skipped")
            }
        } finally {
            (originalTemperature as? VehicleResult.Success)?.value?.let {
                Log.i(TAG, "SDK restoreTemperature=${gateway.setTargetTemperature(it)}")
            }
            (originalPower as? VehicleResult.Success)?.value?.let {
                Log.i(TAG, "SDK restorePower=${gateway.setHvacPower(it)}")
            }
        }
    }

    private fun logConfig(manager: CarPropertyManager, propertyId: Int) {
        val config = manager.getCarPropertyConfig(propertyId)
        if (config == null) {
            Log.w(TAG, "CONFIG property=${propertyId.hex()} unsupported")
            return
        }
        val details = config.areaIds.joinToString { area -> area.hex() }
        Log.i(TAG, "CONFIG property=${propertyId.hex()} access=${config.access} changeMode=${config.changeMode} areas=$details configArray=${config.configArray}")
    }

    private fun areas(manager: CarPropertyManager, propertyId: Int): IntArray =
        manager.getCarPropertyConfig(propertyId)?.areaIds ?: intArrayOf()

    private fun firstArea(manager: CarPropertyManager, propertyId: Int): Int =
        areas(manager, propertyId).firstOrNull() ?: 0

    private inline fun <reified T> read(manager: CarPropertyManager, propertyId: Int, areaId: Int): T? =
        runCatching { manager.getProperty<T>(propertyId, areaId).value }
            .onSuccess { Log.i(TAG, "READ property=${propertyId.hex()} area=${areaId.hex()} value=$it") }
            .onFailure { Log.e(TAG, "READ_FAILED property=${propertyId.hex()} area=${areaId.hex()} type=${it.javaClass.name} message=${it.message}", it) }
            .getOrNull()

    private inline fun <reified T> write(manager: CarPropertyManager, propertyId: Int, areaId: Int, value: T): Boolean =
        runCatching { manager.setProperty(T::class.java, propertyId, areaId, value) }
            .onSuccess { Log.i(TAG, "WRITE property=${propertyId.hex()} area=${areaId.hex()} value=$value") }
            .onFailure { Log.e(TAG, "WRITE_FAILED property=${propertyId.hex()} area=${areaId.hex()} value=$value type=${it.javaClass.name} message=${it.message}", it) }
            .isSuccess

    private fun Int.hex(): String = "0x" + toUInt().toString(16)
    private fun IntArray.hex(): String = joinToString(prefix = "[", postfix = "]") { it.hex() }

    private companion object {
        const val TAG = "CommaHvacDiagnostic"
    }
}
