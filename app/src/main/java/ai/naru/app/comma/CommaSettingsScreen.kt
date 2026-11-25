package ai.naru.app.comma

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-only convenience; does not alter the timer's accepted 1..180-minute range. */
internal fun stepCommaMinutes(current: Int, direction: Int): Int {
    require(current in 1..180)
    require(direction == -1 || direction == 1)
    return (current + direction * 5).coerceIn(1,180)
}

/** Only settings/home are restyled in phase one; existing session screens stay intact. */
@Composable
fun CommaScreen(ui: RestUiState, actions: RestUiActions, onBack: () -> Unit,
    wakingActions: RestUiActions = actions,
    onFinishedHome: () -> Unit = onBack, onNewRest: () -> Unit = {}) {
    var settingsRequested by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(ui.active) { if(ui.active) settingsRequested=false }
    when (commaDestination(ui, settingsRequested)) {
        CommaDestination.SETTINGS -> CommaSettingsScreen(ui,actions,onBack)
        CommaDestination.RESTING -> CommaRestingScreen(ui,actions,onBack)
        CommaDestination.WAKING -> CommaWakingScreen(ui,wakingActions,onBack)
        CommaDestination.FINISHED -> CommaFinishedScreen(ui,actions.retrySave,onFinishedHome,onNewRest)
        CommaDestination.LEGACY -> Column(Modifier.fillMaxSize()) {
            if(!ui.active && !ui.isDemo) TextButton(onClick={settingsRequested=true}) { Text("휴식 설정으로 돌아가기") }
            Box(Modifier.weight(1f)) { LegacyCommaScreen(ui,actions,onBack) }
        }
    }
}

@Composable
internal fun CommaSettingsScreen(ui: RestUiState, actions: RestUiActions, onBack: () -> Unit) {
    var direct by rememberSaveable { mutableStateOf(ui.session.selectedMinutes !in listOf(15,30,45)) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val recommendation=recommendClimate(ui.vehicle)
    val select: (Int) -> Unit = { actions.selectMinutes(it) }
    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
            // Reference sizes are Figma px. Layout uses window dp, bounded type and 48dp+ targets.
            val wide=maxWidth >= 900.dp && maxWidth > maxHeight
            val scale=(maxWidth.value / if(wide) 1700f else 840f).coerceIn(0.5f,1f)
            val padding=(if(wide) 56f else 40f)*scale
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onBack,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                        Text("‹",fontSize=32.sp,modifier=Modifier.semantics { contentDescription="쉼표 모드 나가기" })
                    }
                    Text("쉼표 설정",fontSize=(40*scale).coerceIn(24f,36f).sp,fontWeight=FontWeight.Bold)
                }
                Text(if(ui.isMock) "모의 차량 데이터 · 실제 공조·차량 조명 제어 없음" else "차량 제어 여부는 응답 확인 후 표시합니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                val preparation: @Composable () -> Unit = {
                    CommaPanel(tint=CommaDesign.Suggestion) {
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            CommaFallbackIcon(CommaGlyph.SPARK)
                            Column {
                                Text("나루 제안",color=CommaDesign.Blue,style=MaterialTheme.typography.labelLarge)
                                Text("${ui.session.selectedMinutes}분 휴식 · ${recommendation.targetCelsius?.let { "$it°C 공조 권장" } ?: "공조 상태 확인 필요"}",
                                    style=MaterialTheme.typography.titleMedium)
                            }
                        }
                        Text(recommendation.message,style=MaterialTheme.typography.bodyMedium)
                        Text("실외 ${ui.vehicle.outdoorCelsius?.let { "$it°C" } ?: "정보 없음"}  ·  배터리 ${ui.vehicle.batteryPercent?.let { "$it%" } ?: "정보 없음"}",
                            color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            CommaButton("소리 없이",{actions.configure(ui.session.settings.copy(sound=RestSound.SILENT))},Modifier.weight(1f))
                            CommaButton("45분으로",{direct=false;select(45)},Modifier.weight(1f))
                        }
                        Text("차량 온도 조절·글레오·배터리 예측은 미연동입니다.",color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                    }
                    CommaTimeCard(ui,direct,scale,
                        onPreset={direct=false;select(it)},
                        onDirect={direct=true},
                        onStep={select(stepCommaMinutes(ui.session.selectedMinutes,it))},
                        onInput={draft=ui.session.selectedMinutes.toString();editor="time"})
                }
                val settings: @Composable () -> Unit = {
                    Text("쉬는 동안만 바꿔요",style=MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CommaSettingCard(CommaGlyph.TEMPERATURE,"공조",
                            recommendation.targetCelsius?.let{"권장 $it°C"}?:"추천 없음",
                            if(ui.isDemo) "DEMO 가상 목표 설정" else "차량 제어 미연동",Modifier.weight(1f),
                            onClick=actions.requestClimate,enabled=true)
                        CommaSettingCard(CommaGlyph.SCREEN,"나루 화면",
                            "${(ui.session.settings.brightness*100).toInt()}%",
                            "앱 창 밝기 · 조절 가능",Modifier.weight(1f),onClick={editor="brightness"})
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CommaSettingCard(CommaGlyph.SOUND,"음향",
                            if(ui.session.settings.sound==RestSound.SILENT) "소리 없이" else "잔잔한 톤",
                            "자연음·미리 듣기 미구현",Modifier.weight(1f),onClick={editor="sound"})
                        CommaSettingCard(CommaGlyph.LIGHT,"앱 무드 조명",
                            "${(ui.session.settings.lighting*100).toInt()}%",
                            "앱 배경색 · 차량 조명 아님",Modifier.weight(1f),onClick={editor="lighting"})
                    }
                }
                if(wide) {
                    Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                        Column(Modifier.weight(1004f),verticalArrangement=Arrangement.spacedBy(18.dp)) { preparation() }
                        Column(Modifier.weight(560f),verticalArrangement=Arrangement.spacedBy(12.dp)) { settings() }
                    }
                } else {
                    preparation()
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { settings() }
                }
                Text("종료 시 휴식 전 앱 설정으로 복구합니다. 차량 설정은 변경하지 않습니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                if (this@BoxWithConstraints.maxWidth < 500.dp) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        CommaButton("알람만 맞추기",actions.startAlarmOnly,Modifier.fillMaxWidth(),enabled=ui.isDemo && ui.canStartRest)
                        CommaButton("이 설정으로 쉬기",actions.start,Modifier.fillMaxWidth(),filled=true,enabled=ui.canStartRest)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End)) {
                        CommaButton("알람만 맞추기",actions.startAlarmOnly,enabled=ui.isDemo && ui.canStartRest)
                        CommaButton("이 설정으로 쉬기",actions.start,filled=true,enabled=ui.canStartRest)
                    }
                }
                if(!ui.canStartRest) Text(parkingLabel(ui.vehicle.parkingStatus)+" · 상단의 연동/주차 상태 확인을 이용하세요.",color=CommaDesign.Muted)
                Text(if(ui.isDemo) "DEMO 알람 전용: 앱 밝기·음향·가상 공조·조명 변경 없이 타이머만 시작합니다."
                    else "‘알람만 맞추기’는 일반 모드에서 아직 사용할 수 없습니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={showHistory=!showHistory}) {
                    Text("휴식 기록 ${ui.history.records.size}개${if(ui.history.pendingCount>0) " · 미저장 ${ui.history.pendingCount}개" else ""} ${if(showHistory) "접기" else "보기"}")
                }
                if(showHistory) RestHistoryCard(ui.history,ui.isDemo,actions.retrySave)
                Text("앱 강제 종료·재부팅 후 진행 중 타이머는 복구하지 않습니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(editor!=null) {
            val input=draft.toIntOrNull()
            AlertDialog(onDismissRequest={editor=null},
                title={Text(when(editor) {"time"->"휴식 시간 직접 입력";"brightness"->"앱 화면 밝기";"lighting"->"앱 무드 조명";else->"앱 사운드"})},
                text={
                    when(editor) {
                        "time" -> OutlinedTextField(draft,{draft=it.filter(Char::isDigit).take(3)},
                            label={Text("1~180분")},singleLine=true,isError=input==null||input !in 1..180,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                            supportingText={Text("스테퍼 외에도 1분 단위로 직접 입력할 수 있습니다.")})
                        "brightness" -> Column {
                            Text("${(ui.session.settings.brightness*100).toInt()}% · 휴식 시작 시 앱 창에 적용")
                            Slider(ui.session.settings.brightness,{actions.configure(ui.session.settings.copy(brightness=it))},valueRange=0.05f..1f)
                        }
                        "lighting" -> Column {
                            Text("${(ui.session.settings.lighting*100).toInt()}% · 앱 배경색만 변경")
                            Slider(ui.session.settings.lighting,{actions.configure(ui.session.settings.copy(lighting=it))})
                        }
                        else -> Column {
                            Text("빗소리·해변 등 자연음은 아직 제공하지 않습니다.")
                            TextButton(onClick={actions.configure(ui.session.settings.copy(sound=RestSound.SILENT))}) {
                                Text("${if(ui.session.settings.sound==RestSound.SILENT) "✓ " else ""}소리 없이")
                            }
                            TextButton(onClick={actions.configure(ui.session.settings.copy(sound=RestSound.SOFT_TONE))}) {
                                Text("${if(ui.session.settings.sound==RestSound.SOFT_TONE) "✓ " else ""}15초 간격의 짧은 톤")
                            }
                        }
                    }
                },
                confirmButton={TextButton(enabled=editor!="time"||(input!=null&&input in 1..180),
                    onClick={if(editor=="time"&&input!=null&&input in 1..180)select(input);editor=null}) {Text("확인")}},
                dismissButton={TextButton(onClick={editor=null}) {Text("닫기")}})
        }
    }
}

@Composable
private fun CommaTimeCard(
    ui: RestUiState,direct:Boolean,scale:Float,onPreset:(Int)->Unit,
    onDirect:()->Unit,onStep:(Int)->Unit,onInput:()->Unit
) {
    CommaPanel {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            CommaFallbackIcon(CommaGlyph.CLOCK,color=CommaDesign.Ink)
            Text("휴식 시간",style=MaterialTheme.typography.titleMedium)
        }
        Surface(color=CommaDesign.Ink.copy(alpha=0.05f),shape=RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(5.dp)) {
                listOf(15,30,45,0).forEach { value ->
                    val selected=if(value==0) direct else !direct&&ui.session.selectedMinutes==value
                    Surface(onClick={if(value==0)onDirect() else onPreset(value)},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp),
                        color=if(selected)Color.White else Color.Transparent,
                        shape=RoundedCornerShape(16.dp),shadowElevation=if(selected)3.dp else 0.dp) {
                        Box(Modifier.padding(horizontal=4.dp,vertical=14.dp),contentAlignment=Alignment.Center) {
                            Text(if(value==0)"직접 설정" else "${value}분",
                                fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=2)
                        }
                    }
                }
            }
        }
        if(direct) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onStep(-1)},enabled=ui.session.selectedMinutes>1,
                    modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {Text("−",Modifier.semantics {contentDescription="휴식 시간 5분 줄이기"})}
                TextButton(onClick=onInput,modifier=Modifier.heightIn(min=48.dp)) {Text("${ui.session.selectedMinutes}분",fontSize=22.sp)}
                OutlinedButton(onClick={onStep(1)},enabled=ui.session.selectedMinutes<180,
                    modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {Text("+",Modifier.semantics {contentDescription="휴식 시간 5분 늘리기"})}
            }
            Text("±5분 조절 · 시간을 누르면 1~180분 직접 입력",style=MaterialTheme.typography.bodyMedium,color=CommaDesign.Muted)
        }
        Spacer(Modifier.height(if(direct)8.dp else 36.dp))
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.End) {
            Text("종료 예정",Modifier.align(Alignment.Start),color=CommaDesign.Muted)
            Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(ui.nowMillis+ui.session.selectedMinutes*60_000L)),
                fontSize=(112*scale).coerceIn(48f,80f).sp,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable
private fun CommaSettingCard(
    glyph:CommaGlyph,label:String,value:String,detail:String,modifier:Modifier,
    onClick:()->Unit,enabled:Boolean=true
) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=170.dp),
        color=Color.White,shape=RoundedCornerShape(22.dp),shadowElevation=3.dp) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(color=CommaDesign.Blue.copy(alpha=0.10f),shape=RoundedCornerShape(50)) {
                CommaFallbackIcon(glyph,Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(label,color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
            Text(value,style=MaterialTheme.typography.titleMedium)
            Text(detail,color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun CommaHomeScreen(ui:RestUiState,onEnter:()->Unit) {
    CommaDesignTheme {
        Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text(if(ui.isDemo)"[DEMO] 나루" else "나루",style=MaterialTheme.typography.titleLarge)
            Text("어떻게 쉬어 갈까요?",style=MaterialTheme.typography.headlineLarge)
            Text("${if(ui.isDemo) "DEMO 가상 차량" else "모의 차량 데이터"} · 배터리 ${ui.vehicle.batteryPercent?.let{"$it%"}?:"정보 없음"}",
                color=CommaDesign.Muted)
            Surface(Modifier.widthIn(max=760.dp).fillMaxWidth(),
                shape=RoundedCornerShape(24.dp),color=Color.White,shadowElevation=3.dp) {
                Column {
                    Box(Modifier.fillMaxWidth().height(160.dp).background(CommaDesign.Warm),
                        contentAlignment=Alignment.Center) {
                        CommaFallbackIcon(CommaGlyph.CLOCK,Modifier.size(88.dp),CommaDesign.Ink)
                    }
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("쉼표 모드",style=MaterialTheme.typography.labelLarge)
                        Text("잠깐 쉬기",style=MaterialTheme.typography.headlineLarge)
                        Text("15분에서 45분, 짧은 낮잠과 대기 시간 · 직접 설정 가능")
                        ui.history.records.firstOrNull()?.let {
                            Text("최근 휴식 ${it.elapsedMillis/60_000}분",color=CommaDesign.Muted)
                        }
                        CommaButton("쉼표 모드 시작하기",onEnter,Modifier.align(Alignment.End),filled=true)
                    }
                }
            }
            Text("현재는 앱 화면·무드 조명·톤만 적용합니다. 실제 차량은 제어하지 않습니다.",color=CommaDesign.Muted)
        }
    }
}
