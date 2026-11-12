package ai.naru.app.camping

import ai.naru.app.camping.CampingSessionRules.next
import ai.naru.app.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampingSessionRulesTest {

    // --- 정상 흐름 ---

    @Test
    fun `정상 흐름은 대기에서 시작해 아침을 거쳐 대기로 돌아온다`() {
        var stage = CampingStage.IDLE
        val events = listOf(
            CampingEvent.Start(Gear.PARK),
            CampingEvent.ConfirmArrival,
            CampingEvent.StartNight,
            CampingEvent.WakeTimeReached,
            CampingEvent.Restored,
        )
        val visited = mutableListOf(stage)
        for (event in events) {
            stage = (next(stage, event) as CampingTransition.MoveTo).stage
            visited += stage
        }
        assertEquals(
            listOf(
                CampingStage.IDLE,
                CampingStage.ARRIVAL_CHECK,
                CampingStage.BEDTIME_PREP,
                CampingStage.NIGHT,
                CampingStage.MORNING,
                CampingStage.IDLE,
            ),
            visited,
        )
    }

    // --- 시작 조건: P에서만 ---

    @Test
    fun `P가 아니면 세션을 시작할 수 없다`() {
        for (gear in listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL, Gear.UNKNOWN)) {
            val result = next(CampingStage.IDLE, CampingEvent.Start(gear))
            assertTrue("$gear 에서 시작이 거절돼야 한다", result is CampingTransition.Rejected)
        }
    }

    @Test
    fun `P에서는 도착 점검으로 시작한다`() {
        assertEquals(
            CampingTransition.MoveTo(CampingStage.ARRIVAL_CHECK),
            next(CampingStage.IDLE, CampingEvent.Start(Gear.PARK)),
        )
    }

    // --- 기어 이탈: 어느 단계든 종료 ---

    @Test
    fun `세션 중 어느 단계든 기어가 P를 벗어나면 대기로 끝난다`() {
        val activeStages = CampingStage.entries - CampingStage.IDLE
        for (stage in activeStages) {
            for (gear in listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL, Gear.UNKNOWN)) {
                assertEquals(
                    "$stage 에서 $gear",
                    CampingTransition.MoveTo(CampingStage.IDLE, EndReason.GEAR_LEFT_PARK),
                    next(stage, CampingEvent.GearChanged(gear)),
                )
            }
        }
    }

    @Test
    fun `기어가 P로 그대로면 단계가 바뀌지 않는다`() {
        val result = next(CampingStage.NIGHT, CampingEvent.GearChanged(Gear.PARK))
        assertTrue(result is CampingTransition.Rejected)
    }

    @Test
    fun `대기 상태에서 기어가 바뀌어도 아무 일도 없다`() {
        val result = next(CampingStage.IDLE, CampingEvent.GearChanged(Gear.DRIVE))
        assertTrue(result is CampingTransition.Rejected)
    }

    // --- 밤 → 아침 ---

    @Test
    fun `밤에 기상 시각이 되면 아침으로 넘어간다`() {
        assertEquals(
            CampingTransition.MoveTo(CampingStage.MORNING, EndReason.WAKE_TIME),
            next(CampingStage.NIGHT, CampingEvent.WakeTimeReached),
        )
    }

    @Test
    fun `밤에 배터리 하한에 도달하면 아침으로 넘어간다`() {
        assertEquals(
            CampingTransition.MoveTo(CampingStage.MORNING, EndReason.BATTERY_LOW),
            next(CampingStage.NIGHT, CampingEvent.BatteryLow),
        )
    }

    @Test
    fun `밤이 아닌 단계에서는 배터리 하한 사건을 처리하지 않는다`() {
        for (stage in listOf(CampingStage.IDLE, CampingStage.ARRIVAL_CHECK, CampingStage.BEDTIME_PREP, CampingStage.MORNING)) {
            assertTrue("$stage", next(stage, CampingEvent.BatteryLow) is CampingTransition.Rejected)
        }
    }

    // --- 순서 건너뛰기 금지 ---

    @Test
    fun `도착 확인 없이 밤을 시작할 수 없다`() {
        assertTrue(next(CampingStage.ARRIVAL_CHECK, CampingEvent.StartNight) is CampingTransition.Rejected)
    }

    @Test
    fun `설정 복구가 끝나야 세션이 정상 종료된다`() {
        assertEquals(
            CampingTransition.MoveTo(CampingStage.IDLE, EndReason.COMPLETED),
            next(CampingStage.MORNING, CampingEvent.Restored),
        )
        assertTrue(next(CampingStage.NIGHT, CampingEvent.Restored) is CampingTransition.Rejected)
    }
}
