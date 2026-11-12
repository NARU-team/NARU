package ai.naru.app.vehicle

import android.car.VehicleGear

/**
 * Pleos SDK·AAOS 값을 앱의 값으로 바꾸는 순수 함수 모음.
 * 안드로이드 객체에 의존하지 않아서 단위 테스트할 수 있다.
 */
object PleosMapping {

    /** AAOS `GEAR_SELECTION` 값을 앱의 [Gear]로 바꾼다. 수동 단수(1~9단)는 주행 중이므로 DRIVE로 본다. */
    fun gearOf(aaosValue: Int?): Gear = when (aaosValue) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_DRIVE,
        VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND, VehicleGear.GEAR_THIRD,
        VehicleGear.GEAR_FOURTH, VehicleGear.GEAR_FIFTH, VehicleGear.GEAR_SIXTH,
        VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH, VehicleGear.GEAR_NINTH,
        -> Gear.DRIVE
        else -> Gear.UNKNOWN
    }

    /**
     * SDK 예외를 [FailureReason]으로 바꾼다.
     *
     * 배포된 Vehicle SDK는 `VehicleException`이 난독화되어 타입으로 구분할 수 없다(에뮬레이터 확인).
     * 그래서 예외 종류와 메시지 문자열로 판단한다. 실측 메시지 예:
     * - `java.lang.IllegalArgumentException: area ID: 0x1 not supported for property ID: HVAC_POWER_ON`
     * - `getCurrentGearSelectionState need Permission` (SecurityException)
     * - `ERROR_SERVICE_CONNECTION_FAILURE` (VehicleException의 errorCode 이름)
     */
    fun failureReasonOf(error: Throwable): FailureReason {
        val text = "${error.javaClass.name} ${error.message}".uppercase()
        return when {
            error is SecurityException || "PERMISSION" in text -> FailureReason.PERMISSION
            "NOT_SUPPORTED" in text || "NOT SUPPORTED" in text -> FailureReason.NOT_SUPPORTED
            "DRIVING_RESTRICTED" in text -> FailureReason.DRIVING_RESTRICTED
            "CONNECTION" in text || "INTERNAL_SERVICE" in text -> FailureReason.CONNECTION
            else -> FailureReason.UNKNOWN
        }
    }
}
