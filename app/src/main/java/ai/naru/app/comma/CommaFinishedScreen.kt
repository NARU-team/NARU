package ai.naru.app.comma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    ui: RestUiState,
    onRetry: () -> Unit,
    onSubmitFeedback: (String, Int, TemperatureFeedback) -> Unit,
    onHome: () -> Unit,
    onNewRest: () -> Unit,
) {
    if(ui.session.phase != RestPhase.FINISHED) return
    val record=ui.session.completed
    val restoration=ui.session.restoration ?: RestorationReport()
    var satisfaction by rememberSaveable(record?.id) { mutableIntStateOf(0) }
    var temperatureFeedback by rememberSaveable(record?.id) {
        mutableStateOf<TemperatureFeedback?>(null)
    }
    var feedbackSubmitted by rememberSaveable(record?.id) { mutableStateOf(false) }
    var restorationExpanded by rememberSaveable(record?.id) { mutableStateOf(false) }
    val restorationAttentionCount = listOf(
        restoration.hvacPower,
        restoration.climateTemperature,
        restoration.ambientLight,
        restoration.screenBrightness,
        restoration.natureSound,
        restoration.alarm,
    ).count { it in setOf(
        RestorationOutcome.FAILURE,
        RestorationOutcome.UNKNOWN,
        RestorationOutcome.MANUAL_CHECK,
    ) }
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
                    if (ui.history.pendingCount > 0 || ui.history.error != null) {
                        CommaPanel(Modifier.fillMaxWidth()) {
                            Text("휴식 기록 저장 확인",style=MaterialTheme.typography.titleMedium)
                            Text(finishedSaveStatus(ui))
                            ui.history.error?.let {Text("기록 저장을 확인하지 못했습니다.",color=MaterialTheme.colorScheme.error)}
                            if(ui.history.pendingCount>0) Text("미저장 기록 ${ui.history.pendingCount}개")
                            CommaButton("다시 확인",onRetry)
                        }
                    }
                }
                val results: @Composable () -> Unit = {
                    CommaPanel(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("설정 복구 결과",style=MaterialTheme.typography.titleLarge)
                                Text(
                                    if (restorationAttentionCount == 0) "확인이 필요한 항목이 없습니다."
                                    else "확인 필요한 항목 ${restorationAttentionCount}개",
                                    color = if (restorationAttentionCount == 0) CommaDesign.Muted
                                        else MaterialTheme.colorScheme.error,
                                )
                            }
                            TextButton(onClick = { restorationExpanded = !restorationExpanded }) {
                                Text(if (restorationExpanded) "상세 접기" else "상세 보기")
                            }
                        }
                        if (restorationExpanded) {
                            RestorationRow("공조 전원", restoration.hvacPower,
                                "백업값 요청 후 실제 전원 상태 재조회 기준")
                            RestorationRow("공조 목표 온도", restoration.climateTemperature,
                                "백업 온도 요청 후 실제 목표 온도 재조회 기준")
                            RestorationRow("차량 앰비언트 조명", restoration.ambientLight,
                                "켜짐·밝기·색상 재조회 기준")
                            RestorationRow("화면 밝기", restoration.screenBrightness,
                                "앱 창 원래 밝기 복구 상태")
                            RestorationRow("자연음", restoration.natureSound,
                                "플레이어 중단과 오디오 자원 해제 상태")
                            RestorationRow("알람", restoration.alarm,
                                "예약 취소·재생 중단·오디오 포커스 반환 상태")
                        }
                        if (restorationAttentionCount > 0) {
                            Text(
                                "확인 불가 또는 직접 확인 필요 항목은 차량에서 다시 확인해 주세요.",
                                color=MaterialTheme.colorScheme.error,
                                style=MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    CommaPanel(Modifier.fillMaxWidth()) {
                        Text("추가 측정",style=MaterialTheme.typography.titleMedium)
                        Text(record?.let { item ->
                            val start = item.startIndoorCelsius
                            val end = item.endIndoorCelsius
                            if (start != null && end != null) "실내 온도 ${start}°C → ${end}°C"
                            else "실내 온도 변화 · 측정 정보 없음"
                        } ?: "실내 온도 변화 · 측정 정보 없음")
                        Text(record?.let { item ->
                            val start = item.startBatteryPercent
                            val end = item.endBatteryPercent
                            if (start != null && end != null) "배터리 ${start}% → ${end}%"
                            else "배터리 사용량 · 측정 정보 없음"
                        } ?: "배터리 사용량 · 측정 정보 없음")
                        if (ui.isDemo || ui.isMock) {
                            Text("DEMO·Mock 기록은 개인화 추천에서 제외합니다.")
                        } else if (feedbackSubmitted || record?.feedbackSubmittedAt != null) {
                            Text("피드백 저장 완료 · 같은 휴식에는 한 번만 저장합니다.")
                        } else {
                            Text("휴식 만족도")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..5).forEach { score ->
                                    FilterChip(
                                        selected = satisfaction == score,
                                        onClick = { satisfaction = score },
                                        label = { Text("$score") },
                                    )
                                }
                            }
                            Text("온도는 어땠나요?")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    TemperatureFeedback.TOO_COLD to "추웠어요",
                                    TemperatureFeedback.COMFORTABLE to "적당했어요",
                                    TemperatureFeedback.TOO_WARM to "더웠어요",
                                ).forEach { (value, label) ->
                                    FilterChip(
                                        selected = temperatureFeedback == value,
                                        onClick = { temperatureFeedback = value },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            CommaButton(
                                "피드백 저장",
                                onClick = {
                                    val id = record?.id ?: return@CommaButton
                                    val temperature = temperatureFeedback ?: return@CommaButton
                                    onSubmitFeedback(id, satisfaction, temperature)
                                    feedbackSubmitted = true
                                },
                                enabled = record != null && satisfaction in 1..5 && temperatureFeedback != null,
                            )
                        }
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
internal fun CommaFinishedPreview() {CommaFinishedScreen(finishedPreview,{},{ _, _, _ -> },{},{})}
