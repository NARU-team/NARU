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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** RESTING only. All time and cabin values come from the existing ViewModel snapshot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommaRestingScreen(ui: RestUiState, actions: RestUiActions, onBack: () -> Unit) {
    if (ui.session.phase != RestPhase.RESTING) return
    val indoor = ui.vehicle.indoorCelsius?.let { "${it}°C" } ?: "정보 없음"
    val battery = ui.vehicle.batteryPercent?.let { "${it}%" } ?: "정보 없음"
    val sound = soundLabel(ui.cabin.sound)
    val mock = if (ui.isMock) " · 모의" else ""
    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Based on the actual allocated app width, including split-screen windows.
            val wide = maxWidth >= 900.dp
            val padding = if (wide) 32.dp else 20.dp
            val background = androidx.compose.ui.graphics.lerp(
                Color.White, Color(0xFFFFC580), ui.cabin.lighting * 0.25f)
            Column(Modifier.fillMaxSize().background(background)
                .verticalScroll(rememberScrollState()).padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick=onBack,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                        Text("‹",fontSize=32.sp,modifier=Modifier.semantics { contentDescription="휴식 나가기 확인" })
                    }
                    Text(if(ui.isDemo) "[DEMO] 휴식 중" else "휴식 중",
                        style=MaterialTheme.typography.headlineLarge)
                }
                Text(if(ui.isMock) "모의 차량 데이터 · 실제 공조·차량 조명 제어 없음"
                    else "차량 상태 표시 · 공조 유지 성공을 의미하지 않습니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                if(wide) {
                    Row(Modifier.fillMaxWidth().height(448.dp),
                        horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        RestingTimer(ui,Modifier.weight(1068f).fillMaxHeight())
                        Column(Modifier.weight(520f).fillMaxHeight(),
                            verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            RestingStatusCard(CommaGlyph.TEMPERATURE,"실내$mock",indoor,
                                ui.selectedClimateCelsius?.let { "${it}°C로 맞춰 두는 중" }
                                    ?: "공조 유지 여부 미확인",Modifier.weight(1f))
                            RestingStatusCard(CommaGlyph.SOUND,"앱 음향",sound,
                                "기기 음량 설정에 따름",Modifier.weight(1f))
                            RestingStatusCard(CommaGlyph.BATTERY,"차량 배터리$mock",battery,
                                "예측값 아님",Modifier.weight(1f))
                        }
                    }
                } else {
                    RestingTimer(ui,Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        RestingStatusPill(CommaGlyph.TEMPERATURE,"실내 $indoor$mock")
                        RestingStatusPill(CommaGlyph.SOUND,sound)
                        RestingStatusPill(CommaGlyph.BATTERY,"배터리 $battery$mock")
                    }
                }
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    CommaButton("5분 더",{actions.extend(5)})
                    CommaButton("그만 쉬기",actions.finishResting,filled=true)
                }
                val alarmOnly=ui.isDemo && ui.demoVehicle?.mode==DemoRestMode.ALARM_ONLY
                Text(if(alarmOnly) "연장 포함 계획 ${ui.session.plannedMinutes}분 · 알람 전용"
                    else "연장 포함 계획 ${ui.session.plannedMinutes}분 · 앱 화면 밝기 ${(ui.cabin.brightness*100).toInt()}%",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                Text(if(alarmOnly) "알람 전용: 앱 밝기·음향 및 가상 공조·조명을 변경하지 않습니다."
                    else "마지막 30초에는 앱 화면이 점차 밝아집니다. 종료 시 휴식 전 앱 설정으로 복구합니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                if(ui.isDemo) Text("가상 시간 시연 · 실제 기록 저장 없음",color=CommaDesign.Blue)
            }
        }
    }
}

@Composable
private fun RestingTimer(ui: RestUiState, modifier: Modifier) {
    Surface(modifier,shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=3.dp) {
        BoxWithConstraints(Modifier.padding(24.dp),contentAlignment=Alignment.Center) {
            val diameter = maxWidth.coerceAtMost(400.dp)
            val fraction = restingRemainingFraction(ui.session)
            val seconds = (ui.session.remainingMillis.coerceAtLeast(0) + 999) / 1000
            val remaining = String.format(Locale.getDefault(),"%02d:%02d",seconds/60,seconds%60)
            val end = SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(ui.session.expectedEndMillis))
            Box(Modifier.size(diameter),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics {
                    progressBarRangeInfo=ProgressBarRangeInfo(fraction,0f..1f)
                    contentDescription="남은 휴식 시간 $remaining"
                }) {
                    val stroke=size.minDimension*0.04f
                    val inset=stroke/2
                    val arcSize=Size(size.width-stroke,size.height-stroke)
                    drawArc(Color(0xFFE0E1E6),-90f,360f,false,Offset(inset,inset),arcSize,
                        style=Stroke(stroke,cap=StrokeCap.Butt))
                    drawArc(CommaDesign.Blue,-90f,360f*fraction,false,Offset(inset,inset),arcSize,
                        style=Stroke(stroke,cap=StrokeCap.Butt))
                }
                Column(Modifier.padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("남은 시간",color=CommaDesign.Muted,style=MaterialTheme.typography.labelLarge)
                    Text(remaining,fontSize=(diameter.value*0.18f).coerceIn(28f,72f).sp,
                        fontWeight=FontWeight.Bold,maxLines=1)
                    Text("${if(ui.isDemo) "가상 " else ""}$end 종료 예정",
                        style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
                }
            }
        }
    }
}

private val restingPreviewState = RestUiState(
    session=RestSession(phase=RestPhase.RESTING,selectedMinutes=30,plannedMinutes=30,
        remainingMillis=1_104_000,expectedEndMillis=1_794_700_800_000),
    vehicle=VehicleState(28f,32f,true,75))
private val restingPreviewActions = RestUiActions({}, {}, {}, {}, {}, {}, {})

@Preview(name="Resting landscape",widthDp=1100,heightDp=780)
@Preview(name="Resting split",widthDp=560,heightDp=790)
@Preview(name="Resting phone",widthDp=360,heightDp=800)
@Composable
internal fun CommaRestingPreview() {
    CommaRestingScreen(restingPreviewState,restingPreviewActions,{})
}
