package ai.naru.app.comma

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presentation only: CabinEffects remains owned by CommaRoute, outside this screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommaWakingScreen(ui: RestUiState, actions: RestUiActions, onBack: () -> Unit) {
    if (ui.session.phase != RestPhase.WAKING) return
    val confirmation = wakingConfirmationSeconds(ui.session)
    val alarmOnly=ui.isDemo && ui.demoVehicle?.mode==DemoRestMode.ALARM_ONLY
    val brightness = if(alarmOnly) "변경 없음" else "${(ui.cabin.brightness*100).toInt()}%"
    val battery = ui.vehicle.batteryPercent?.let { "$it%" } ?: "정보 없음"
    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 900.dp
            val background = androidx.compose.ui.graphics.lerp(
                Color.White, Color(0xFFFFC580), ui.cabin.lighting*0.25f)
            Column(Modifier.fillMaxSize().background(background)
                .verticalScroll(rememberScrollState()).padding(if(wide)32.dp else 20.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onBack,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                        Text("‹",fontSize=32.sp,modifier=Modifier.semantics {contentDescription="휴식 나가기 확인"})
                    }
                    Text(if(ui.isDemo)"[DEMO] 휴식 끝" else "휴식 끝",
                        style=MaterialTheme.typography.headlineLarge,
                        modifier=Modifier.semantics {heading(); liveRegion=LiveRegionMode.Polite})
                }
                if(wide) {
                    Row(Modifier.fillMaxWidth().height(448.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        WakingAlarm(ui,Modifier.weight(1068f).fillMaxHeight())
                        Column(Modifier.weight(520f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            RestingStatusCard(CommaGlyph.SCREEN,"앱 화면",brightness,
                                if(alarmOnly) "알람 전용 · 밝기 변경 안 함" else "종료 전 밝기 변화 적용",Modifier.weight(1f))
                            RestingStatusCard(CommaGlyph.CLOCK,"종료 알림","짧은 알림 톤",
                                "기기 음량 설정에 따름",Modifier.weight(1f))
                            RestingStatusCard(CommaGlyph.BATTERY,
                                if(ui.isMock)"차량 배터리 · 모의" else "차량 배터리",battery,
                                "휴식 전후 변화량 미측정",Modifier.weight(1f))
                        }
                    }
                } else {
                    WakingAlarm(ui,Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        RestingStatusPill(CommaGlyph.SCREEN,"앱 화면 $brightness")
                        RestingStatusPill(CommaGlyph.CLOCK,"계획 휴식 ${ui.session.plannedMinutes}분")
                    }
                }
                Text("확인 시간 ${confirmation}초 · 연장하지 않으면 자동 종료하고 앱 설정을 복구합니다.",
                    color=CommaDesign.Ink,style=MaterialTheme.typography.bodyLarge)
                Text("더 쉬고 싶다면 시간을 연장해 보세요.",color=CommaDesign.Muted)
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    CommaButton("5분 더",{actions.extend(5)},enabled=confirmation>0)
                    CommaButton("10분 더",{actions.extend(10)},enabled=confirmation>0)
                    CommaButton("휴식 종료",actions.finish,filled=true,enabled=confirmation>0)
                }
                Text("종료 알림은 짧은 톤이며 기기의 음량 설정을 따릅니다. 실제 차량은 제어하지 않습니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                if(ui.isMock) Text("모의 차량 데이터 사용 · 배터리 변화량이나 예측을 표시하지 않습니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                if(ui.isDemo) Text("가상 시간 시연 · 실제 휴식 기록 저장 없음",color=CommaDesign.Blue)
            }
        }
    }
}

@Composable
private fun WakingAlarm(ui:RestUiState,modifier:Modifier) {
    // expectedEndMillis is the planned deadline, not nowMillis or the UI entry time.
    val end=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(ui.session.expectedEndMillis))
    Surface(modifier,shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=3.dp) {
        BoxWithConstraints(Modifier.padding(24.dp),contentAlignment=Alignment.Center) {
            val diameter=maxWidth.coerceAtMost(400.dp)
            Box(Modifier.size(diameter),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    val stroke=size.minDimension*0.04f
                    drawCircle(CommaDesign.Blue,radius=(size.minDimension-stroke)/2,
                        style=Stroke(stroke))
                }
                Column(Modifier.padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("알람 · 종료 예정 시각",color=CommaDesign.Muted,
                        style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
                    Text(end,fontSize=(diameter.value*0.18f).coerceIn(28f,72f).sp,fontWeight=FontWeight.Bold,
                        maxLines=1)
                    Text("쉴 시간이 끝났어요",style=MaterialTheme.typography.labelLarge,
                        textAlign=TextAlign.Center)
                }
            }
        }
    }
}

private val wakingPreview = RestUiState(
    session=RestSession(phase=RestPhase.WAKING,selectedMinutes=30,plannedMinutes=30,
        expectedEndMillis=1_794_700_800_000,confirmationRemainingMillis=25_000),
    vehicle=VehicleState(28f,32f,true,75),cabin=CabinSettings(0.7f,0.15f))
private val wakingPreviewActions=RestUiActions({}, {}, {}, {}, {}, {}, {})

@Preview(name="Waking landscape",widthDp=1100,heightDp=780)
@Preview(name="Waking split",widthDp=560,heightDp=790)
@Preview(name="Waking phone",widthDp=360,heightDp=800)
@Composable
internal fun CommaWakingPreview() { CommaWakingScreen(wakingPreview,wakingPreviewActions,{}) }
