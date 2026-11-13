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
            CampingEvent.Restored(),
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
    fun `차량을 바꾸기 전 단계에서 기어가 P를 벗어나면 바로 대기로 끝난다`() {
        val activeStages = listOf(CampingStage.ARRIVAL_CHECK, CampingStage.BEDTIME_PREP)
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
            CampingTransition.RestoreThenEnd(EndReason.BATTERY_LOW),
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
            next(CampingStage.MORNING, CampingEvent.Restored()),
        )
        assertTrue(next(CampingStage.BEDTIME_PREP, CampingEvent.Restored()) is CampingTransition.Rejected)
    }

    @Test
    fun `밤에 직접 종료하면 복구를 먼저 하고 직접 종료 이유로 끝난다`() {
        assertEquals(CampingTransition.RestoreThenEnd(EndReason.USER_ENDED), next(CampingStage.NIGHT, CampingEvent.EndNow))
        assertEquals(
            CampingTransition.MoveTo(CampingStage.IDLE, EndReason.USER_ENDED),
            next(CampingStage.NIGHT, CampingEvent.Restored(EndReason.USER_ENDED)),
        )
        for (stage in listOf(CampingStage.IDLE, CampingStage.ARRIVAL_CHECK, CampingStage.BEDTIME_PREP, CampingStage.MORNING)) {
            assertTrue(next(stage, CampingEvent.EndNow) is CampingTransition.Rejected)
        }
    }

    @Test
    fun `취침 준비에서만 도착 안내로 돌아갈 수 있다`() {
        assertEquals(CampingTransition.MoveTo(CampingStage.ARRIVAL_CHECK), next(CampingStage.BEDTIME_PREP, CampingEvent.BackToArrival))
        for (stage in listOf(CampingStage.IDLE, CampingStage.ARRIVAL_CHECK, CampingStage.NIGHT, CampingStage.MORNING)) {
            assertTrue(next(stage, CampingEvent.BackToArrival) is CampingTransition.Rejected)
        }
    }

    @Test
    fun `밤 전에는 그만둘 수 있고, 밤부터는 그만두기로 끝내지 않는다`() {
        for (stage in listOf(CampingStage.ARRIVAL_CHECK, CampingStage.BEDTIME_PREP)) {
            assertEquals(CampingTransition.MoveTo(CampingStage.IDLE, EndReason.CANCELED), next(stage, CampingEvent.Cancel))
        }
        for (stage in listOf(CampingStage.IDLE, CampingStage.NIGHT, CampingStage.MORNING)) {
            assertTrue(next(stage, CampingEvent.Cancel) is CampingTransition.Rejected)
        }
    }

    @Test
    fun `밤과 아침에 기어가 P를 벗어나면 바로 끝내지 않고 복구를 먼저 한다`() {
        for (stage in listOf(CampingStage.NIGHT, CampingStage.MORNING)) {
            for (gear in listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL, Gear.UNKNOWN)) {
                assertEquals(
                    "$stage 에서 $gear",
                    CampingTransition.RestoreThenEnd(EndReason.GEAR_LEFT_PARK),
                    next(stage, CampingEvent.GearChanged(gear)),
                )
            }
        }
    }

    @Test
    fun `기어 이탈로 복구를 마치면 기어 이탈 이유로 대기로 돌아간다`() {
        for (stage in listOf(CampingStage.NIGHT, CampingStage.MORNING)) {
            assertEquals(
                "$stage",
                CampingTransition.MoveTo(CampingStage.IDLE, EndReason.GEAR_LEFT_PARK),
                next(stage, CampingEvent.Restored(EndReason.GEAR_LEFT_PARK)),
            )
        }
    }
}
