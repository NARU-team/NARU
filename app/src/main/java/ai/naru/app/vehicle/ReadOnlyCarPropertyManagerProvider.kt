package ai.naru.app.vehicle

import android.car.hardware.property.CarPropertyManager

/** Shares the existing AAOS connection without transferring ownership or lifecycle. */
interface ReadOnlyCarPropertyManagerProvider {
    val readOnlyCarPropertyManager: CarPropertyManager?
}
