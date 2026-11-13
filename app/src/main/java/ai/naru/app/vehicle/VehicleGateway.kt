package ai.naru.app.vehicle

import kotlinx.coroutines.flow.Flow

/**
 * 앱이 차량과 대화하는 유일한 창구.
 *
 * 화면과 세션 로직은 이 인터페이스만 알고, Pleos SDK나 AAOS Car API는 모른다.
 * 실제 차량(에뮬레이터) 구현과 테스트용 Fake 구현을 바꿔 끼울 수 있다.
 */
interface VehicleGateway {

    /** 현재 기어. 값이 바뀔 때마다 새 값을 내보낸다. */
    val gear: Flow<Gear>

    /** 배터리 잔량(%). 알 수 없으면 null. 폴링하지 않고 구독으로 받는다. */
    val batteryPercent: Flow<Int?>

    /** 이 차량이 지원하는 기능과 값 범위. 제어 전에 반드시 확인한다. */
    suspend fun capabilities(): VehicleCapabilities

    /** 공조 설정 온도(°C). */
    suspend fun getTargetTemperature(): VehicleResult<Float>

    /** 실내 현재 온도(°C). */
    suspend fun getCabinTemperature(): VehicleResult<Float>

    /** 바깥 온도(°C). 차량이 주지 않으면 실패. 홈 화면 칩에 쓴다. */
    suspend fun getOutsideTemperature(): VehicleResult<Float>

    /** 공조 설정 온도를 바꾼다. 성공은 "요청이 전달됨"이지 실제 반영이 아니므로 다시 조회해서 확인한다. */
    suspend fun setTargetTemperature(celsius: Float): VehicleResult<Unit>

    /** 앰비언트 조명 상태. */
    suspend fun getAmbientLight(): VehicleResult<AmbientLight>

    /** 앰비언트 조명을 바꾼다. 성공의 의미는 [setTargetTemperature]와 같다. */
    suspend fun setAmbientLight(light: AmbientLight): VehicleResult<Unit>
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/**
 * 앰비언트 조명 설정.
 * @property brightness 0~100
 * @property colorRgb 0xRRGGBB 형식 (에뮬레이터 실측: 흰색 = 0xFFFFFF)
 */
data class AmbientLight(
    val on: Boolean,
    val brightness: Int,
    val colorRgb: Int,
)

/**
 * 지원 여부와 값 범위. null이면 그 기능은 미지원이다.
 * 미지원 기능은 끄고 세션은 계속 진행한다 (화면·음향·알람 중심).
 */
data class VehicleCapabilities(
    val targetTemperatureRange: ClosedFloatingPointRange<Float>?,
    val ambientBrightnessRange: IntRange?,
    val ambientColorSupported: Boolean,
)

/** 차량 호출 결과. 예외를 던지지 않고 실패를 값으로 돌려준다. */
sealed interface VehicleResult<out T> {
    data class Success<T>(val value: T) : VehicleResult<T>
    data class Failure(val reason: FailureReason, val message: String? = null) : VehicleResult<Nothing>
}

enum class FailureReason {
    /** 차량이 이 기능이나 영역을 지원하지 않음 */
    NOT_SUPPORTED,

    /** 매니페스트 퍼미션 누락 또는 런타임 권한 미승인 */
    PERMISSION,

    /** 차량 서비스 연결 실패 */
    CONNECTION,

    /** 주행 중이라 제한됨 */
    DRIVING_RESTRICTED,

    /** 그 밖의 실패 */
    UNKNOWN,
}
