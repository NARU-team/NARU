package ai.naru.app.comma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal data class VehicleIssueContent(
    val title:String,val detail:String,val primary:VehicleIssueAction,val primaryLabel:String
)

/** Pure projection. Intro acknowledgement is not an Android permission grant. */
internal fun vehicleIssueContent(ui:RestUiState):VehicleIssueContent {
    val base=baseVehicleIssueContent(ui)
    if(!ui.isDemo) return base
    return when(ui.vehicleIssue) {
        VehicleIssue.DRIVING_STOPPED -> VehicleIssueContent("DEMO 가상 주행 시작 · 종료",
            "버튼으로 가상 주행 신호를 입력해 데모만 종료했습니다. 실제 차량 안전 기능 동작이 아닙니다.",
            VehicleIssueAction.VIEW_COMPLETION,"DEMO 완료 보기")
        VehicleIssue.PARKING_UNKNOWN,VehicleIssue.NOT_PARKED -> base.copy(
            title="DEMO · "+base.title,
            detail="가상 주차 상태를 변경한 시연입니다. 실제 주차 신호가 아닙니다. 버튼으로 가상 PARKED 상태로 복귀합니다.",
            primary=VehicleIssueAction.CHECK_PARKING,primaryLabel="가상 주차로 복귀")
        else -> base.copy(title="DEMO · "+base.title,
            detail="가상 시나리오 · 실제 차량 API 호출 없음. "+base.detail)
    }
}

private fun baseVehicleIssueContent(ui:RestUiState):VehicleIssueContent = when(ui.vehicleIssue) {
    VehicleIssue.FIRST_USE -> VehicleIssueContent("쉼표 모드를 시작하기 전에",
        "앱 창 밝기·앱 배경색·짧은 톤·앱 내부 휴식 기록을 사용합니다. 현재 기능에는 Android 런타임 권한 요청이 없습니다. 차량 권한이나 Pleos 권한을 요청하지 않으며 실제 차량은 제어하지 않습니다.",
        VehicleIssueAction.ACKNOWLEDGE,"안내 확인")
    VehicleIssue.UNSUPPORTED -> VehicleIssueContent("차량 제어를 지원하지 않아요",
        "현재 연결에서는 실제 공조·차량 조명을 제어할 수 없습니다. 앱 내부 밝기·무드 배경·톤·타이머만 사용할 수 있습니다.",
        VehicleIssueAction.CONTINUE_APP_ONLY,"앱 내부 설정으로 계속")
    VehicleIssue.CONTROL_FAILURE -> VehicleIssueContent("차량 제어에 실패했어요",
        ui.vehicleControl.message+" 실패 응답과 미지원 상태를 구분하며 차량에 적용됐다고 표시하지 않습니다.",
        VehicleIssueAction.RETRY_CONTROL,"제어 다시 요청")
    VehicleIssue.CONTROL_UNKNOWN -> VehicleIssueContent("차량 제어 결과 확인 불가",
        ui.vehicleControl.message+" 응답이나 지원 정보가 확인되지 않아 성공으로 간주하지 않습니다.",
        VehicleIssueAction.RETRY_CONTROL,"연동 상태 다시 확인")
    VehicleIssue.NOT_PARKED -> VehicleIssueContent("주차 상태가 아니에요",
        "차량 상태의 주차 값이 NOT_PARKED입니다. 주차 확인 전 새 휴식을 시작할 수 없습니다. 이 값만으로 주행 시작을 추정하지 않습니다.",
        VehicleIssueAction.CHECK_PARKING,"현재 주차 상태 다시 확인")
    VehicleIssue.PARKING_UNKNOWN -> if(ui.isMock) VehicleIssueContent("실제 주차 신호를 확인할 수 없어요",
        "Mock 연결에는 실제 차량 주차·주행 신호가 없습니다. 직접 실제 주차 여부를 확인한 경우에만 앱 내부 휴식을 사용할 수 있습니다. 사용자 확인은 차량 신호로 저장하지 않으며 주행 시 자동 종료를 보장하지 않습니다.",
        VehicleIssueAction.CONFIRM_LOCAL_PARKING,"주차를 직접 확인했어요 · 앱만 사용")
        else VehicleIssueContent("주차 상태 확인 불가",
        "실제 차량의 주차 신호가 UNKNOWN입니다. 사용자 입력으로 차량 신호를 덮어쓰지 않으며 확인된 주차 신호 전에는 새 휴식을 시작할 수 없습니다.",
        VehicleIssueAction.CHECK_PARKING,"현재 주차 상태 다시 확인")
    VehicleIssue.DRIVING_STOPPED -> VehicleIssueContent("주행 신호로 휴식을 종료했어요",
        "실제 연결에서 지원되는 주행 신호를 수신해 기존 종료 경로로 앱 설정 복구와 기록 저장을 요청했습니다. 각 처리 결과는 완료 화면에서 확인하세요.",
        VehicleIssueAction.VIEW_COMPLETION,"종료 요약 보기")
    null -> VehicleIssueContent("연동 상태",ui.vehicleControl.message,VehicleIssueAction.RETURN,"돌아가기")
}

internal fun issueActionAllowed(ui:RestUiState,action:VehicleIssueAction):Boolean =
    action==VehicleIssueAction.RETURN ||
        (ui.vehicleIssue!=null && vehicleIssueContent(ui).primary==action)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommaExceptionScreen(ui:RestUiState,onAction:(VehicleIssueAction)->Unit) {
    val content=vehicleIssueContent(ui)
    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
            val wide=maxWidth>=900.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(if(wide)32.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text(if(ui.isDemo)"DEMO 쉼표 · 가상 연동 안내" else "쉼표 모드 · 연동 안내",style=MaterialTheme.typography.headlineLarge,
                    modifier=Modifier.semantics {heading();liveRegion=LiveRegionMode.Polite})
                val notice:@Composable ()->Unit = {
                    CommaPanel(Modifier.fillMaxWidth(),tint=CommaDesign.Suggestion) {
                        CommaFallbackIcon(CommaGlyph.SPARK)
                        Text(content.title,style=MaterialTheme.typography.titleLarge)
                        Text(content.detail,style=MaterialTheme.typography.bodyLarge)
                        if(ui.isMock) Text("Mock 차량 · 실제 제어/주행 신호 아님",color=CommaDesign.Blue)
                    }
                }
                val facts:@Composable ()->Unit = {
                    CommaPanel(Modifier.fillMaxWidth()) {
                        Text("확인된 범위",style=MaterialTheme.typography.titleMedium)
                        Text(parkingLabel(ui.vehicle.parkingStatus))
                        if(ui.localParkedConfirmed) Text("사용자가 직접 주차 확인 · 앱 휴식 한정")
                        Text("차량 제어: "+when(ui.vehicleControl.outcome) {
                            ControlOutcome.SUCCESS -> "실제 차량 응답 확인"
                            ControlOutcome.FAILURE -> "실패 응답"
                            ControlOutcome.UNSUPPORTED -> "미지원"
                            ControlOutcome.UNKNOWN -> "확인 불가"
                            ControlOutcome.PENDING -> "응답 대기"
                            ControlOutcome.NOT_REQUESTED -> "요청 없음"
                        })
                        Text(ui.vehicleControl.message,style=MaterialTheme.typography.bodyMedium)
                        Text(if(ui.isMock || ui.capabilities.drivingSignal!=ControlSupport.SUPPORTED)
                            "주행 시 자동 종료 · 현재 연결 미지원 / 실차 검증 필요"
                            else "실제 주행 신호 수신 시 종료 경로 연결 · 실차 검증 필요")
                        Text("앱 밝기/배경/음향은 실제 차량 공조·차량 조명과 다릅니다.",
                            color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                    }
                }
                if(wide) Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f)) {notice()}
                    Column(Modifier.weight(1f)) {facts()}
                } else {notice();facts()}
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    CommaButton("돌아가기",{onAction(VehicleIssueAction.RETURN)})
                    CommaButton(content.primaryLabel,{onAction(content.primary)},filled=true,
                        enabled=ui.vehicleControl.outcome!=ControlOutcome.PENDING)
                }
                Text("이 안내 화면은 타이머를 재시작하거나 종료·복구·저장을 반복하지 않습니다.",
                    style=MaterialTheme.typography.bodyMedium,color=CommaDesign.Muted)
            }
        }
    }
}

@Composable
internal fun CommaVehicleStatus(ui:RestUiState,onReview:()->Unit) {
    CommaDesignTheme {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp)) {
            Text((if(ui.isMock)"Mock · " else "")+parkingLabel(ui.vehicle.parkingStatus)+
                if(ui.localParkedConfirmed)" · 사용자 확인(앱 한정)" else "",
                style=MaterialTheme.typography.bodyMedium)
            Text(ui.vehicleControl.message,style=MaterialTheme.typography.bodyMedium,color=CommaDesign.Muted)
            Text(if(ui.isMock || ui.capabilities.drivingSignal!=ControlSupport.SUPPORTED)
                "실제 주행 감지 자동 종료 미지원 · 차량 신호 연동/검증 필요"
                else "실제 주행 신호에 한해 종료 · 실차 검증 필요",
                style=MaterialTheme.typography.bodyMedium,color=CommaDesign.Muted)
            TextButton(onClick=onReview) {Text("연동/주차 상태 확인")}
        }
    }
}

@Preview(name="Unsupported wide",widthDp=1100,heightDp=780)
@Composable
internal fun CommaUnsupportedPreview() {
    CommaExceptionScreen(RestUiState(vehicleIssue=VehicleIssue.UNSUPPORTED,
        vehicleControl=VehicleControlState(ControlOutcome.UNSUPPORTED,"Mock 연결 · 실제 차량 제어 없음")),{})
}
@Preview(name="Unknown split",widthDp=560,heightDp=790)
@Preview(name="Unknown phone",widthDp=360,heightDp=800)
@Composable
internal fun CommaParkingUnknownPreview() {
    CommaExceptionScreen(RestUiState(vehicleIssue=VehicleIssue.PARKING_UNKNOWN),{})
}
@Preview(name="Failed",widthDp=1100,heightDp=780)
@Composable
internal fun CommaControlFailedPreview() {
    CommaExceptionScreen(RestUiState(isMock=false,vehicleIssue=VehicleIssue.CONTROL_FAILURE,
        vehicleControl=VehicleControlState(ControlOutcome.FAILURE,"차량의 실패 응답")),{})
}
