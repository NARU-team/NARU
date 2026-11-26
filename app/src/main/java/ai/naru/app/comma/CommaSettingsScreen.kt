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
import kotlinx.coroutines.delay

/** UI-only convenience; does not alter the timer's accepted 1..180-minute range. */
internal fun stepCommaMinutes(current: Int, direction: Int): Int {
    require(current in 1..180)
    require(direction == -1 || direction == 1)
    return (current + direction * 5).coerceIn(1,180)
}

private fun intentBeforeValue(ui: RestUiState, change: CommaProposedChange): String = when (change) {
    is CommaProposedChange.Duration -> "현재 ${ui.session.selectedMinutes}분 → ${change.minutes}분"
    is CommaProposedChange.TemperatureAbsolute ->
        "현재 ${ui.selectedClimateCelsius?.let { "$it°C" } ?: "추천값"} → ${change.celsius}°C"
    is CommaProposedChange.TemperatureDelta ->
        "현재 ${ui.selectedClimateCelsius?.let { "$it°C" } ?: "추천값"} → ${change.deltaCelsius}°C 조정"
    is CommaProposedChange.Sound -> "현재 ${soundLabel(ui.session.settings.sound)} → ${soundLabel(change.sound)}"
    is CommaProposedChange.Brightness ->
        "현재 ${(ui.session.settings.brightness * 100).toInt()}% → ${change.percent}%"
    is CommaProposedChange.AmbientLight ->
        "현재 ${(ui.session.settings.lighting * 100).toInt()}% → ${change.percent}%"
    is CommaProposedChange.Extension -> "현재 계획 ${ui.session.plannedMinutes}분 → ${change.minutes}분 연장"
}

/** Only settings/home are restyled in phase one; existing session screens stay intact. */
@Composable
fun CommaScreen(ui: RestUiState, actions: RestUiActions, onBack: () -> Unit,
    wakingActions: RestUiActions = actions,
    onFinishedHome: () -> Unit = onBack, onNewRest: () -> Unit = {}) {
    var settingsRequested by rememberSaveable { mutableStateOf(true) }
    var soundRequested by rememberSaveable { mutableStateOf(false) }
    var showIntentInput by rememberSaveable { mutableStateOf(false) }
    var utterance by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ui.active) { if(ui.active) settingsRequested=false }
    Box(Modifier.fillMaxSize()) {
        when {
            ui.vehicleControl.outcome == ControlOutcome.PENDING -> CommaApplyingScreen(ui)
            soundRequested -> CommaSoundScreen(ui, actions) { soundRequested = false }
            commaDestination(ui, settingsRequested) == CommaDestination.SETTINGS ->
                CommaSettingsScreen(
                    ui, actions, onBack,
                    onChooseSound = { soundRequested = true },
                    onNaturalLanguage = { showIntentInput = true },
                )
            commaDestination(ui, settingsRequested) == CommaDestination.RESTING -> CommaRestingScreen(ui,actions,onBack)
            commaDestination(ui, settingsRequested) == CommaDestination.WAKING -> CommaWakingScreen(ui,wakingActions,onBack)
            commaDestination(ui, settingsRequested) == CommaDestination.FINISHED -> CommaFinishedScreen(ui,actions.retrySave,actions.submitFeedback,onFinishedHome,onNewRest)
            else -> Column(Modifier.fillMaxSize()) {
                if(!ui.active && !ui.isDemo) TextButton(onClick={settingsRequested=true}) { Text("휴식 설정으로 돌아가기") }
                Box(Modifier.weight(1f)) { LegacyCommaScreen(ui,actions,onBack) }
            }
        }
    }
    if (showIntentInput) {
        AlertDialog(
            onDismissRequest = { showIntentInput = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            title = { Text("설정 변경 요청") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("연결된 쉼표 서버를 먼저 사용하고, 실패하면 로컬 규칙으로 제안합니다. Gleo AI 연동은 아닙니다.")
                OutlinedTextField(
                    value = utterance,
                    onValueChange = { utterance = it },
                    label = { Text("예: 20분만 쉬고 싶어") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = actions.startSpeechInput,
                        enabled = ui.speechStatus !in setOf(CommaSpeechStatus.LISTENING, CommaSpeechStatus.RECOGNIZING),
                    ) { Text("음성으로 말하기") }
                    if (ui.speechStatus in setOf(CommaSpeechStatus.LISTENING, CommaSpeechStatus.RECOGNIZING)) {
                        TextButton(onClick = actions.stopSpeechInput) { Text("음성 입력 취소") }
                    }
                }
                ui.speechText?.let {
                    Text("인식 중: $it", color = CommaDesign.Muted)
                    LaunchedEffect(it) { utterance = it }
                }
                ui.speechMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ui.intentResultMessage?.let { Text(it, color = CommaDesign.Muted) }
            } },
            confirmButton = { TextButton(
                enabled = utterance.isNotBlank() && !ui.intentProcessing,
                onClick = { actions.proposeIntent(utterance); showIntentInput = false },
            ) { Text("제안 만들기") } },
            dismissButton = { TextButton(onClick = { showIntentInput = false }) { Text("취소") } },
        )
    }
    ui.pendingIntentProposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = actions.cancelIntent,
            containerColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            title = { Text("변경 내용을 확인하세요") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("요청: ${proposal.utterance}")
                Text("출처: ${when (proposal.source) {
                    CommaProposalSource.LLM_SERVER -> "쉼표 LLM 서버"
                    CommaProposalSource.LOCAL_RULE -> "로컬 규칙"
                    CommaProposalSource.GLEO -> "Gleo"
                    CommaProposalSource.UNAVAILABLE -> "사용 불가"
                }}", color = CommaDesign.Muted)
                Text("변경 예정: ${proposal.description}", fontWeight = FontWeight.Bold)
                proposal.changes.forEach { Text(intentBeforeValue(ui, it), color = CommaDesign.Muted) }
                Text("승인 시 현재 세션 상태와 차량 범위를 다시 검사합니다.")
            } },
            confirmButton = { TextButton(onClick = actions.approveIntent) { Text("승인하고 적용") } },
            dismissButton = { TextButton(onClick = actions.cancelIntent) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommaSettingsScreen(
    ui: RestUiState,
    actions: RestUiActions,
    onBack: () -> Unit,
    onChooseSound: () -> Unit = {},
    onNaturalLanguage: () -> Unit = {},
) {
    var direct by rememberSaveable { mutableStateOf(ui.session.selectedMinutes !in listOf(15,30,45)) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var previewSound by remember { mutableStateOf<RestSound?>(null) }
    DisposableEffect(previewSound) {
        previewSound?.let(RestAudioCoordinator::playNature)
        onDispose(RestAudioCoordinator::stopNature)
    }
    LaunchedEffect(previewSound) {
        if (previewSound != null) {
            delay(3_000)
            previewSound = null
        }
    }
    val recommendation = recommendClimate(ui.vehicle, ui.history.records)
    val lastRealTemperature = ui.history.records.firstOrNull {
        it.source == RecordSource.REAL && it.targetClimateCelsius != null
    }?.targetClimateCelsius
    val energyForecast = forecastEnergy(
        ui.vehicle.batteryPercent,
        ui.session.selectedMinutes,
        ui.history.records,
        currentOutdoorCelsius = ui.vehicle.outdoorCelsius,
        targetClimateCelsius = recommendation.targetCelsius,
    )
    val select: (Int) -> Unit = { actions.selectMinutes(it) }
    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
            // Reference sizes are Figma px. Layout uses window dp, bounded type and 48dp+ targets.
            val wide=maxWidth >= 900.dp && maxWidth > maxHeight
            val scale=(maxWidth.value / if(wide) 1700f else 840f).coerceIn(0.5f,1f)
            val padding=(if(wide) 28f else 24f)*scale
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onBack,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                        Text("‹",fontSize=32.sp,modifier=Modifier.semantics { contentDescription="쉼표 모드 나가기" })
                    }
                    Text("쉼표 설정",fontSize=(40*scale).coerceIn(24f,36f).sp,fontWeight=FontWeight.Bold)
                }
                if (ui.isMock) {
                    Text("모의 차량 데이터 · 실제 공조·차량 조명 제어 없음",
                        color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                }
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
                        Text(
                            listOfNotNull(
                                ui.vehicle.outdoorCelsius?.let { "실외 $it°C" },
                                lastRealTemperature?.let { "지난번 $it°C" },
                                when {
                                    ui.charging.state == ChargingState.CHARGING -> "현재 충전 중 · 방전 예측과 분리"
                                    ui.charging.portConnected == true -> "충전 포트 연결 · 방전 예측과 분리"
                                    energyForecast?.confidence == EnergyForecastConfidence.HIGH ->
                    "휴식 후 ${energyForecast.minimumEndPercent}~${energyForecast.maximumEndPercent}%"
                energyForecast != null ->
                    "참고 ${energyForecast.minimumEndPercent}~${energyForecast.maximumEndPercent}%"
                                    else -> "배터리 예측 기록 부족"
                                },
                            ).joinToString("  ·  "),
                            color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),
                            verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            CommaButton("더 따뜻하게", {
                                val range = ui.capabilities.climateTemperatureRange
                                val base = ui.selectedClimateCelsius ?: recommendation.targetCelsius
                                if (range != null && base != null) {
                                    actions.selectClimateTarget((base + 1f).coerceAtMost(range.endInclusive))
                                }
                            }, enabled = ui.capabilities.climate == ControlSupport.SUPPORTED)
                            CommaButton("소리 없이",{actions.configure(ui.session.settings.copy(sound=RestSound.SILENT))})
                            CommaButton("45분으로",{direct=false;select(45)})
                            CommaButton(if (ui.intentProcessing) "제안 확인 중" else "설정 말하기", onNaturalLanguage, enabled = !ui.intentProcessing)
                        }
                        ui.intentResultMessage?.let { Text(it, color = CommaDesign.Muted) }
                        Text(
                            energyForecast?.let {
                                val prefix = if (it.confidence == EnergyForecastConfidence.HIGH) "고신뢰도" else "제한적 참고"
            "$prefix 실측 ${it.sampleCount}회 기준 종료 예상 ${it.minimumEndPercent}~${it.maximumEndPercent}% · ${it.limitation}"
                            } ?: "충분한 실측 기록이 없어 종료 배터리는 예측하지 않습니다.",
                            color=CommaDesign.Muted,
                            style=MaterialTheme.typography.bodyMedium,
                        )
                    }
                    CommaTimeCard(ui,direct,scale,
                        onPreset={direct=false;select(it)},
                        onDirect={direct=true},
                        onStep={select(stepCommaMinutes(ui.session.selectedMinutes,it))},
                        onInput={draft=ui.session.selectedMinutes.toString();editor="time"})
                }
                val settings: @Composable () -> Unit = {
                    Text("쉬는 동안만 바꿔요",style=MaterialTheme.typography.titleMedium)
                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CommaSettingCard(CommaGlyph.TEMPERATURE,"온도",
                            ui.selectedClimateCelsius?.let {
                                "${if (ui.climateSelectionIsManual) "직접 선택" else "추천"} $it°C"
                            } ?: recommendation.targetCelsius?.let{"권장 $it°C"}?:"추천 없음",
                            if (ui.isDemo) "DEMO 가상 목표 설정" else when (ui.capabilities.climate) {
                                ControlSupport.SUPPORTED -> "승인 후 차량에 적용·재확인"
                                ControlSupport.UNSUPPORTED -> "차량 미지원 · 앱 휴식 계속"
                                ControlSupport.UNKNOWN -> "지원 여부 확인 필요"
                            },Modifier.weight(1f),
                            onClick={editor="climate"},enabled=ui.capabilities.climate!=ControlSupport.UNSUPPORTED)
                        CommaSettingCard(CommaGlyph.SCREEN,"화면",
                            "${(ui.session.settings.brightness*100).toInt()}%",
                            "앱 창 밝기 · 조절 가능",Modifier.weight(1f),onClick={editor="brightness"})
                        CommaSettingCard(CommaGlyph.SOUND,"소리",
                            when (ui.session.settings.sound) {
                                RestSound.SILENT -> "소리 없이"
                                RestSound.SOFT_TONE -> "잔잔한 톤"
                                RestSound.RAIN -> "빗소리"
                                RestSound.OCEAN -> "파도 소리"
                                RestSound.FOREST -> "숲 소리"
                                RestSound.LAKE -> "잔물결 소리"
                            },
                            "자연음 선택 · 미리 듣기",Modifier.weight(1f),onClick=onChooseSound)
                        CommaSettingCard(CommaGlyph.LIGHT,"조명",
                            "${(ui.session.settings.lighting*100).toInt()}%",
                            when (ui.capabilities.lighting) {
                                ControlSupport.SUPPORTED -> "앱 화면 + 승인 후 차량 조명"
                                ControlSupport.UNSUPPORTED -> "앱 화면만 적용 · 차량 미지원"
                                ControlSupport.UNKNOWN -> "앱 화면 적용 · 차량 지원 확인 필요"
                            },Modifier.weight(1f),onClick={editor="lighting"})
                    }
                }
                if(wide) {
                    Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                        Column(Modifier.weight(1.18f),verticalArrangement=Arrangement.spacedBy(12.dp)) { preparation() }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) { settings() }
                    }
                } else {
                    preparation()
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { settings() }
                }
                Text("종료 시 휴식 전 앱·지원 차량 설정으로 복구하며, 확인할 수 없는 항목은 완료 화면에 표시합니다.",
                    color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                if (this@BoxWithConstraints.maxWidth < 500.dp) {
                    Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if (ui.isDemo) {
                            CommaButton("알람만 맞추기",actions.startAlarmOnly,Modifier.fillMaxWidth(),enabled=ui.canStartRest)
                        }
                        CommaButton("이 설정으로 쉬기",actions.start,Modifier.fillMaxWidth(),filled=true,enabled=ui.canStartRest)
                    }
                } else {
                    Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End)) {
                        if (ui.isDemo) {
                            CommaButton("알람만 맞추기",actions.startAlarmOnly,enabled=ui.canStartRest)
                        }
                        CommaButton("이 설정으로 쉬기",actions.start,filled=true,enabled=ui.canStartRest)
                    }
                }
                if(!ui.canStartRest) Text(parkingLabel(ui.vehicle.parkingStatus)+" · 상단의 연동/주차 상태 확인을 이용하세요.",color=CommaDesign.Muted)
                ui.alarmTimingWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (ui.isDemo) {
                    Text("DEMO 알람 전용: 앱 밝기·음향·가상 공조·조명 변경 없이 타이머만 시작합니다.",
                        color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={showHistory=!showHistory}) {
                        Text("휴식 기록 ${ui.history.records.size}개 ${if(showHistory) "접기" else "보기"}")
                    }
                    if(showHistory) RestHistoryCard(ui.history,true,actions.retrySave)
                } else if (ui.history.pendingCount > 0 || ui.history.error != null) {
                    Text("일부 휴식 기록 저장을 확인해야 합니다.", color=MaterialTheme.colorScheme.error)
                }
            }
        }
        if(editor!=null) {
            val input=draft.toIntOrNull()
            AlertDialog(
                onDismissRequest={editor=null},
                containerColor = Color.White,
                shape = RoundedCornerShape(28.dp),
                title={Text(when(editor) {"time"->"휴식 시간 직접 입력";"climate"->"공조 온도 선택";"brightness"->"앱 화면 밝기";"lighting"->"앱 무드 조명";else->"앱 사운드"})},
                text={
                    when(editor) {
                        "time" -> OutlinedTextField(draft,{draft=it.filter(Char::isDigit).take(3)},
                            label={Text("1~180분")},singleLine=true,isError=input==null||input !in 1..180,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                            supportingText={Text("스테퍼 외에도 1분 단위로 직접 입력할 수 있습니다.")})
                        "climate" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val range = ui.capabilities.climateTemperatureRange
                            val target = ui.selectedClimateCelsius
                                ?: recommendation.targetCelsius
                                ?: range?.start
                            Text("실내 ${ui.vehicle.indoorCelsius?.let { "$it°C" } ?: "정보 없음"} · 외부 ${ui.vehicle.outdoorCelsius?.let { "$it°C" } ?: "정보 없음"}")
                            Text(recommendation.targetCelsius?.let { "규칙 기반 추천 $it°C" } ?: "추천 온도 없음")
                            if (range == null || target == null) {
                                Text("차량이 지원하는 공조 온도 범위를 확인할 수 없습니다.")
                            } else {
                                Text("${if (ui.climateSelectionIsManual) "직접 선택" else "추천 선택"} ${target}°C · 지원 ${range.start}~${range.endInclusive}°C")
                                Slider(
                                    value = target.coerceIn(range.start, range.endInclusive),
                                    onValueChange = { actions.selectClimateTarget((it * 2f).toInt() / 2f) },
                                    valueRange = range,
                                )
                                TextButton(onClick = { actions.selectClimateTarget(null) }) {
                                    Text("추천 온도로 되돌리기")
                                }
                                Text("확인을 누르기 전에는 차량을 제어하지 않습니다.", color = CommaDesign.Muted)
                            }
                        }
                        "brightness" -> Column {
                            Text("${(ui.session.settings.brightness*100).toInt()}% · 휴식 시작 시 앱 창에 적용")
                            Slider(ui.session.settings.brightness,{actions.configure(ui.session.settings.copy(brightness=it))},valueRange=0.05f..1f)
                        }
                        "lighting" -> Column {
                            Text("${(ui.session.settings.lighting*100).toInt()}% · 앱 배경색만 변경")
                            Slider(ui.session.settings.lighting,{actions.configure(ui.session.settings.copy(lighting=it))})
                        }
                        else -> Column {
                            Text("자연음을 선택하면 3초 동안 미리 들려드려요.")
                            listOf(
                                RestSound.SILENT to "소리 없이",
                                RestSound.RAIN to "빗소리",
                                RestSound.OCEAN to "파도 소리",
                                RestSound.FOREST to "숲 소리",
                                RestSound.LAKE to "잔물결 소리",
                                RestSound.SOFT_TONE to "15초 간격의 짧은 톤",
                            ).forEach { (sound, label) ->
                                TextButton(onClick = {
                                    actions.configure(ui.session.settings.copy(sound = sound))
                                    previewSound = sound.takeIf {
                                        it == RestSound.RAIN || it == RestSound.OCEAN || it == RestSound.FOREST ||
                                            it == RestSound.LAKE
                                    }
                                }) {
                                    Text("${if (ui.session.settings.sound == sound) "✓ " else ""}$label")
                                }
                            }
                        }
                    }
                },
                confirmButton={TextButton(
                    enabled = when (editor) {
                        "time" -> input != null && input in 1..180
                        "climate" -> ui.capabilities.climateTemperatureRange != null &&
                            (ui.selectedClimateCelsius ?: recommendation.targetCelsius) != null
                        else -> true
                    },
                    onClick={
                        if(editor=="time"&&input!=null&&input in 1..180) select(input)
                        if(editor=="climate") {
                            if (ui.selectedClimateCelsius == null) actions.selectClimateTarget(null)
                            actions.applySelectedClimate()
                        }
                        editor=null
                    }) {Text(if(editor=="climate") "승인하고 적용" else "확인")}},
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
        Spacer(Modifier.height(if(direct)4.dp else 8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("종료 예정",color=CommaDesign.Muted)
            Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(ui.nowMillis+ui.session.selectedMinutes*60_000L)),
                fontSize=(72*scale).coerceIn(40f,56f).sp,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable
private fun CommaSettingCard(
    glyph:CommaGlyph,label:String,value:String,detail:String,modifier:Modifier,
    onClick:()->Unit,enabled:Boolean=true
) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=108.dp),
        color=Color.White,shape=RoundedCornerShape(22.dp),shadowElevation=3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),
            verticalAlignment=Alignment.CenterVertically,
        ) {
            Surface(color=CommaDesign.Blue.copy(alpha=0.10f),shape=RoundedCornerShape(50)) {
                CommaFallbackIcon(glyph,Modifier.padding(9.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(label,color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                Text(value,style=MaterialTheme.typography.titleMedium)
                Text(detail,color=CommaDesign.Muted,style=MaterialTheme.typography.bodySmall)
            }
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
