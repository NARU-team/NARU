package ai.naru.app.comma

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Visible only behind Debug + DEMO_MODE. No real vehicle operations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommaDemoControls(
    ui:RestUiState,onEnter:()->Unit,onExit:()->Unit,onLastThirty:()->Unit,
    onDeadline:()->Unit,onExpire:()->Unit,onException:(VehicleIssue)->Unit,
    onEnvironment:(Boolean)->Unit
) {
    // 일반 사용자 화면에서는 촬영용 진입점과 조작 패널을 노출하지 않는다.
    if (!ui.isDemo) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    CommaDesignTheme {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp)) {
            if(!ui.isDemo) {
                TextButton(onClick=onEnter,enabled=!ui.active) {Text("촬영 DEMO · 설정부터 시작")}
            } else {
                val virtual=ui.demoVehicle
                Text("DEMO 가상 차량 · 실제 제어/측정 아님 · 실제 Room 저장 제외",
                    color=CommaDesign.Blue,style=MaterialTheme.typography.labelLarge)
                Text("${parkingLabel(ui.vehicle.parkingStatus)} · 실내 ${ui.vehicle.indoorCelsius}°C · 실외 ${ui.vehicle.outdoorCelsius}°C · 배터리 ${ui.vehicle.batteryPercent}%",
                    style=MaterialTheme.typography.bodyMedium)
                Text("가상 공조: ${if(virtual?.climateApplied==true) "${virtual.climateTarget}°C 적용" else "미적용"} · 가상 조명 ${((virtual?.lightingApplied?:0f)*100).toInt()}%"+
                    if(virtual?.mode==DemoRestMode.ALARM_ONLY)" · 알람 전용(설정 변경 없음)" else "",
                    style=MaterialTheme.typography.bodyMedium)
                Text(virtual?.controlDisplay()?.message.orEmpty(),style=MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(ui.session.phase==RestPhase.RESTING) {
                        TextButton(onClick=onLastThirty) {Text("종료 30초 전")}
                        TextButton(onClick=onDeadline) {Text("종료 시점으로")}
                    }
                    if(ui.session.phase==RestPhase.WAKING) {
                        TextButton(onClick=onExpire) {Text("확인 시간 만료")}
                    }
                    TextButton(onClick={onExit();onEnter()}) {Text("다시 촬영 · 초기화")}
                    TextButton(onClick={expanded=!expanded}) {Text(if(expanded)"도구 접기" else "예외/차량 시연")}
                    TextButton(onClick=onExit) {Text("DEMO 나가기")}
                }
                if(expanded) FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick={onEnvironment(true)}) {Text("가상 더운 날 · 28°C/75%")}
                    TextButton(onClick={onEnvironment(false)}) {Text("가상 쾌적 · 23°C/80%")}
                    TextButton(onClick={onException(VehicleIssue.FIRST_USE)}) {Text("최초 안내")}
                    TextButton(onClick={onException(VehicleIssue.PARKING_UNKNOWN)}) {Text("주차 UNKNOWN")}
                    TextButton(onClick={onException(VehicleIssue.NOT_PARKED)}) {Text("주차 아님")}
                    TextButton(onClick={onException(VehicleIssue.UNSUPPORTED)}) {Text("가상 미지원")}
                    TextButton(onClick={onException(VehicleIssue.CONTROL_FAILURE)}) {Text("가상 제어 실패")}
                    TextButton(onClick={onException(VehicleIssue.DRIVING_STOPPED)},enabled=ui.active) {Text("가상 주행 시작")}
                }
            }
        }
    }
}
