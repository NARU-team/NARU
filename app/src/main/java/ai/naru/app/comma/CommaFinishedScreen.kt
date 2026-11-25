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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Read-only completion projection. No finish/restore/save effects on composition or reentry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommaFinishedScreen(
    ui:RestUiState,onRetry:()->Unit,onHome:()->Unit,onNewRest:()->Unit
) {
    if(ui.session.phase != RestPhase.FINISHED) return
    val record=ui.session.completed
    val restoration=ui.session.restoration ?: RestorationReport()
    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
            val wide=maxWidth>=900.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(if(wide)32.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text(if(ui.isDemo)"[DEMO] 휴식 완료" else "휴식 완료",
                    style=MaterialTheme.typography.headlineLarge,
                    modifier=Modifier.semantics {heading();liveRegion=LiveRegionMode.Polite})
                Text("잠시 쉬어 가셨나요?",color=CommaDesign.Muted)
                val summary: @Composable () -> Unit = {
                    CommaPanel(Modifier.fillMaxWidth(),tint=CommaDesign.Suggestion) {
                        CommaFallbackIcon(CommaGlyph.CLOCK)
                        Text("이번 휴식",style=MaterialTheme.typography.titleLarge)
                        ui.demoVehicle?.let { virtual ->
                            Text("DEMO 가상 차량 복구: ${if(virtual.restored) "완료" else "대기"} · 실제 차량 아님",color=CommaDesign.Blue)
                            Text(if(virtual.mode==DemoRestMode.ALARM_ONLY) "알람 전용 · 설정 변경 없음" else "가상 설정 적용 휴식")
                        }
                        val duration=record?.elapsedMillis?.coerceAtLeast(0)
                        Text(duration?.let { "${it/60_000}분 ${it/1000%60}초" } ?: "기록 확인 불가",
                            fontSize=36.sp,fontWeight=FontWeight.Bold)
                        Text("실제 휴식 시간 · 종료 후 확인 대기 시간 제외",color=CommaDesign.Muted)
                        Text("계획 시간 ${record?.plannedMinutes ?: ui.session.plannedMinutes}분 · 연장 포함")
                        Text("휴식 종료 시각: "+(record?.let {
                            SimpleDateFormat("MM/dd HH:mm:ss",Locale.getDefault()).format(Date(it.endedAt))
                        } ?: "확인 불가"))
                        Text(record?.let { when(it.reason) {
                            EndReason.TIMER -> "시간 종료"
                            EndReason.MANUAL -> "직접 종료"
                            EndReason.VIEWMODEL_CLEARED -> "화면 수명 종료"
                            EndReason.DRIVING -> "주행 신호 확인으로 종료"
                        }} ?: "종료 사유 확인 불가",color=CommaDesign.Muted)
                        Text("종료 시각은 기록 기준이며 완료 화면 진입 시각과 다를 수 있습니다.",
                            style=MaterialTheme.typography.bodyMedium,color=CommaDesign.Muted)
                    }
                    CommaPanel(Modifier.fillMaxWidth()) {
                        Text("휴식 기록",style=MaterialTheme.typography.titleMedium)
                        Text(finishedSaveStatus(ui))
                        if(!ui.isDemo) {
                            ui.history.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                            if(ui.history.pendingCount>0) Text("전체 미저장 기록 ${ui.history.pendingCount}개")
                            CommaButton("다시 저장/조회",onRetry)
                        }
                        Text("저장 대기는 앱 범위 큐에서 계속됩니다. 강제 종료 전 저장 완료를 확인하세요.",
                            style=MaterialTheme.typography.bodyMedium,color=CommaDesign.Muted)
                    }
                }
                val results: @Composable () -> Unit = {
                    CommaPanel(Modifier.fillMaxWidth()) {
                        Text("설정 복구 결과",style=MaterialTheme.typography.titleLarge)
                        RestorationRow("앱 내부 밝기·조명·음향 설정",restoration.appSettings,
                            "앱 설정 상태값 확인 결과입니다.")
                        RestorationRow("기기 창 밝기·알림 정리",restoration.windowAndAudio,
                            "기존 수명주기 정리 사용 · 기기 반영 응답 없음")
                        RestorationRow("차량 공조·차량 조명",restoration.vehicle,
                            "차량 SDK 미연동 · 변경하거나 복구하지 않음")
                        Text(if(ui.isMock)"Mock 차량 연결 · 앱 내부 설정과 실제 차량 제어를 구분합니다."
                            else "차량 복구 응답 데이터 없음",color=CommaDesign.Muted,
                            style=MaterialTheme.typography.bodyMedium)
                        if(ui.isDemo) Text("데모의 독립 앱 설정에 대한 복구 결과입니다.",color=CommaDesign.Blue)
                    }
                    CommaPanel(Modifier.fillMaxWidth()) {
                        Text("추가 측정",style=MaterialTheme.typography.titleMedium)
                        Text("실내 온도 변화 · 측정 정보 없음")
                        Text("배터리 사용량 · 측정 정보 없음")
                        Text("사용자 피드백 · 미지원")
                    }
                }
                if(wide) Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp)) {summary()}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp)) {results()}
                } else {summary();results()}
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    CommaButton("홈으로 돌아가기",onHome)
                    CommaButton("새로운 휴식 설정",onNewRest,filled=true)
                }
                Text("새 휴식은 설정 화면에서 시작합니다. 저장 중인 이전 기록은 유지됩니다.",
                    style=MaterialTheme.typography.bodyMedium,color=CommaDesign.Muted)
            }
        }
    }
}

@Composable
private fun RestorationRow(title:String,outcome:RestorationOutcome,detail:String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(restorationLabel(outcome),fontWeight=FontWeight.Bold,
            color=if(outcome==RestorationOutcome.FAILURE)MaterialTheme.colorScheme.error else CommaDesign.Ink)
        Text(detail,color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}

private val finishedPreview=RestUiState(
    session=RestSession(phase=RestPhase.FINISHED,selectedMinutes=30,plannedMinutes=30,
        completed=RestRecord("preview",1_794_700_800_000,1_794_702_600_000,30,1_800_000,EndReason.TIMER),
        restoration=RestorationReport(appSettings=RestorationOutcome.SUCCESS)),
    history=RestHistoryState(pendingCount=1))

@Preview(name="Finished landscape",widthDp=1100,heightDp=780)
@Preview(name="Finished split",widthDp=560,heightDp=790)
@Preview(name="Finished phone",widthDp=360,heightDp=800)
@Composable
internal fun CommaFinishedPreview() {CommaFinishedScreen(finishedPreview,{},{},{})}
