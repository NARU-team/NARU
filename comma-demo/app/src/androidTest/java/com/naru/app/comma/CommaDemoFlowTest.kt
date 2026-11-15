package com.naru.app.comma

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compile-checked here; requires a teammate's emulator/device to execute. */
class CommaDemoFlowTest {
    @get:Rule val compose=createComposeRule()

    @Test fun settingsRestAlarmExtendFinish() {
        val clock=object:RestClock {
            override fun elapsedMillis()=0L
            override fun wallMillis()=1_800_000_000_000L
        }
        val demo=DemoSessionController(clock).apply {prepare(15,CabinSettings())}
        fun snapshot()=RestUiState(isDemo=true,session=demo.state.value,
            cabin=demo.cabin.settings.value,vehicle=demo.vehicle.vehicle,
            demoVehicle=demo.vehicle,nowMillis=demo.wallMillis())
        var ui by mutableStateOf(snapshot())
        fun publish() {ui=snapshot()}
        val actions=RestUiActions(
            selectMinutes={demo.selectMinutes(it);publish()},
            configure={demo.configure(it);publish()},
            start={demo.startPrepared();publish()},
            extend={demo.extend(it);publish()},
            finish={demo.finish();publish()},
            requestClimate={demo.requestClimate();publish()},
            retrySave={})
        compose.setContent {CommaScreen(ui,actions,{})}
        compose.onNodeWithText("15분").performClick()
        compose.onNodeWithText("이 설정으로 쉬기").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(RestPhase.RESTING,demo.state.value.phase);demo.advanceToDeadline();publish()}
        compose.onNodeWithText("5분 더").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(RestPhase.RESTING,demo.state.value.phase);demo.advanceToDeadline();publish()}
        compose.onNodeWithText("휴식 종료").performScrollTo().performClick()
        compose.onNodeWithText("[DEMO] 휴식 완료").assertExists()
    }
}
