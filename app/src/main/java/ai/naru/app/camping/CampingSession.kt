package ai.naru.app.camping

import ai.naru.app.vehicle.Gear

/** 차박 세션 단계. 설계 문서의 `대기 → 도착_점검 → 취침_준비 → 밤 → 아침 → 대기`. */
enum class CampingStage {
    /** 대기: 세션 없음 */
    IDLE,

    /** 도착_점검: 장소·날씨·물때로 3단계 안내 */
    ARRIVAL_CHECK,

    /** 취침_준비: 온도·기상 시각·테마 설정 */
    BEDTIME_PREP,

    /** 밤: 테마 적용, 배터리 감시 */
    NIGHT,

    /** 아침: 알람, 브리핑, 설정 복구 */
    MORNING,
}

/** 세션 단계를 바꾸는 사건. */
sealed interface CampingEvent {
    /** 사용자가 차박 모드 시작을 눌렀다. 시작 시점의 기어를 함께 받는다. */
    data class Start(val gear: Gear) : CampingEvent

    /** 사용자가 도착 안내를 확인했다. */
    data object ConfirmArrival : CampingEvent

    /** 사용자가 취침 준비를 마치고 시작 버튼을 눌렀다. */
    data object StartNight : CampingEvent

    /** 기상 시각이 됐다 (일출 연계 또는 직접 설정). */
    data object WakeTimeReached : CampingEvent

    /** 배터리가 하한에 도달했다. 하한 판정은 규칙 엔진이 하고, 여기서는 결과만 받는다. */
    data object BatteryLow : CampingEvent

    /** 기어가 바뀌었다. */
    data class GearChanged(val gear: Gear) : CampingEvent

    /** 아침 단계에서 차량 설정 복구가 끝났다. */
    data object Restored : CampingEvent
}

/** 전이 결과. */
sealed interface CampingTransition {
    /** 다음 단계로 이동한다. */
    data class MoveTo(val stage: CampingStage, val reason: EndReason? = null) : CampingTransition

    /** 이 단계에서는 이 사건을 받을 수 없다. 단계는 그대로다. */
    data class Rejected(val why: String) : CampingTransition
}

/** 세션이 끝나거나 아침으로 넘어간 이유. 화면 안내와 기록에 쓴다. */
enum class EndReason {
    /** 기상 시각 도달 */
    WAKE_TIME,

    /** 배터리 하한 도달 */
    BATTERY_LOW,

    /** 기어가 P를 벗어남 */
    GEAR_LEFT_PARK,

    /** 정상 종료 (설정 복구 완료) */
    COMPLETED,
}

/**
 * 차박 세션 전이 규칙. 상태를 갖지 않는 순수 함수라 테스트하기 쉽다.
 *
 * 안전 정책 (기획서 "예외 및 안전 정책"):
 * - 기어가 P일 때만 세션을 시작한다.
 * - 세션 중 어느 단계든 기어가 P를 벗어나면 세션을 끝낸다.
 *   설계 문서 다이어그램은 `밤`만 다루지만, 기획서의 "P 상태에서만 휴식 세션" 정책을 따라 전 단계에 적용한다.
 */
object CampingSessionRules {

    fun next(stage: CampingStage, event: CampingEvent): CampingTransition {
        // 기어 이탈은 단계와 관계없이 가장 먼저 처리한다.
        if (event is CampingEvent.GearChanged && event.gear != Gear.PARK && stage != CampingStage.IDLE) {
            return CampingTransition.MoveTo(CampingStage.IDLE, EndReason.GEAR_LEFT_PARK)
        }

        return when (stage) {
            CampingStage.IDLE -> when (event) {
                is CampingEvent.Start ->
                    if (event.gear == Gear.PARK) {
                        CampingTransition.MoveTo(CampingStage.ARRIVAL_CHECK)
                    } else {
                        CampingTransition.Rejected("주차(P) 상태에서만 차박 모드를 시작할 수 있습니다.")
                    }
                else -> reject(stage, event)
            }

            CampingStage.ARRIVAL_CHECK -> when (event) {
                CampingEvent.ConfirmArrival -> CampingTransition.MoveTo(CampingStage.BEDTIME_PREP)
                else -> reject(stage, event)
            }

            CampingStage.BEDTIME_PREP -> when (event) {
                CampingEvent.StartNight -> CampingTransition.MoveTo(CampingStage.NIGHT)
                else -> reject(stage, event)
            }

            CampingStage.NIGHT -> when (event) {
                CampingEvent.WakeTimeReached -> CampingTransition.MoveTo(CampingStage.MORNING, EndReason.WAKE_TIME)
                CampingEvent.BatteryLow -> CampingTransition.MoveTo(CampingStage.MORNING, EndReason.BATTERY_LOW)
                else -> reject(stage, event)
            }

            CampingStage.MORNING -> when (event) {
                CampingEvent.Restored -> CampingTransition.MoveTo(CampingStage.IDLE, EndReason.COMPLETED)
                else -> reject(stage, event)
            }
        }
    }

    private fun reject(stage: CampingStage, event: CampingEvent) =
        CampingTransition.Rejected("$stage 단계에서는 ${event::class.simpleName} 사건을 처리하지 않습니다.")
}
