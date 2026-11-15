package com.naru.app.comma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Temporary functional layout. Styling can change independently of state and actions. */
@Composable
internal fun LegacyCommaScreen(ui: RestUiState, actions: RestUiActions, onBack: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    var direct by rememberSaveable { mutableStateOf(false) }
    val customMinutes = custom.toIntOrNull()
    val valid = !direct || (customMinutes != null && customMinutes in 1..180)
    val background = if (ui.active) androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.background, Color(0xFFFFC580), ui.cabin.lighting * 0.25f)
        else MaterialTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack) { Text("‹ 쉼표 모드 나가기") }
            Text(if (ui.isDemo) "[DEMO] 쉼표 모드" else "쉼표 모드",
                style = MaterialTheme.typography.headlineLarge)
            Text(if (ui.isMock) "모의 차량 데이터 · 실제 차량 제어 없음"
                else "차량 연동 · 제어 결과는 차량 응답으로 확인")
            Text(if (ui.isDemo) "가상 시간 시연 중 · 실제 기록과 분리되어 있습니다."
                else "앱 강제 종료·재부팅 후 진행 중 타이머는 복구하지 않습니다.",
                style = MaterialTheme.typography.bodySmall)
            val controls: @Composable () -> Unit = {
                RestPreparationCard(ui, custom, direct, valid, onCustom = {
                    custom = it.filter(Char::isDigit).take(3)
                    custom.toIntOrNull()?.takeIf { n -> n in 1..180 }?.let(actions.selectMinutes)
                }, onDirect = { direct = it }, actions = actions)
                ClimateRecommendationCard(ui, actions.requestClimate)
            }
            val status: @Composable () -> Unit = {
                RestTimerCard(ui, valid, onStart = {
                    if (direct && customMinutes != null && !ui.isDemo) actions.selectMinutes(customMinutes)
                    actions.start()
                }, onExtend = actions.extend, onFinish = actions.finish)
                RestHistoryCard(ui.history, ui.isDemo, actions.retrySave)
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { controls() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { status() }
                }
            } else { controls(); status() }
        }
    }
}

@Composable
internal fun RestPreparationCard(
    ui: RestUiState, custom: String, direct: Boolean, valid: Boolean,
    onCustom: (String) -> Unit, onDirect: (Boolean) -> Unit, actions: RestUiActions
) {
    val session = ui.session
    val editable = !ui.active && !ui.isDemo
    RestSection("휴식 준비") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45).forEach { minutes ->
                FilterChip(selected = !direct && session.selectedMinutes == minutes,
                    enabled = editable,
                    onClick = { onDirect(false); actions.selectMinutes(minutes) },
                    label = { Text("${minutes}분") })
            }
        }
        FilterChip(selected = direct, enabled = editable, onClick = { onDirect(true) },
            label = { Text("직접 설정 (1~180분)") })
        if (direct) OutlinedTextField(value = custom, enabled = editable,
            onValueChange = onCustom, label = { Text("휴식 시간 (분)") }, isError = !valid,
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { if (!valid) Text("1~180분을 입력하세요.") })
        val planned = if (ui.active) session.expectedEndMillis else ui.nowMillis + session.selectedMinutes * 60_000L
        Text("${if (ui.isDemo) "가상 " else ""}종료 예정: ${formatRestTime(planned)}")
        Text("앱 화면 밝기 ${(session.settings.brightness * 100).toInt()}%")
        Slider(value = session.settings.brightness, enabled = editable, valueRange = 0.05f..1f,
            onValueChange = { actions.configure(session.settings.copy(brightness = it)) })
        Text("앱 무드 조명 ${(session.settings.lighting * 100).toInt()}%")
        Slider(value = session.settings.lighting, enabled = editable,
            onValueChange = { actions.configure(session.settings.copy(lighting = it)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = session.settings.sound == RestSound.SOFT_TONE, enabled = editable,
                onCheckedChange = { actions.configure(session.settings.copy(sound =
                    if (it) RestSound.SOFT_TONE else RestSound.SILENT)) })
            Text("15초 간격의 잔잔한 톤", Modifier.padding(start = 8.dp))
        }
        Text("밝기·조명·사운드는 앱 내부에만 적용됩니다. 종료 알림음은 기기의 음량 설정을 따릅니다.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ClimateRecommendationCard(ui: RestUiState, onClimate: () -> Unit) {
    val vehicle = ui.vehicle
    val recommendation = recommendClimate(vehicle)
    RestSection("공조 추천") {
        Text("실내 ${vehicle.indoorCelsius?.let { "$it°C" } ?: "정보 없음"} / 실외 ${vehicle.outdoorCelsius?.let { "$it°C" } ?: "정보 없음"}")
        Text("차량: ${parkingLabel(vehicle.parkingStatus)} · 배터리 ${vehicle.batteryPercent?.let { "$it%" } ?: "정보 없음"}")
        Text(recommendation.message)
        OutlinedButton(onClick = onClimate, enabled = recommendation.targetCelsius != null && !ui.isDemo) {
            Text(if (ui.isMock) "공조 연동 상태 확인" else "권장 공조 요청")
        }
    }
}

@Composable
internal fun RestTimerCard(
    ui: RestUiState, validDuration: Boolean, onStart: () -> Unit,
    onExtend: (Int) -> Unit, onFinish: () -> Unit
) {
    RestSection("휴식") {
        Text(when (ui.session.phase) {
            RestPhase.READY -> "준비 완료"
            RestPhase.RESTING -> "편안히 쉬세요"
            RestPhase.WAKING -> "휴식 종료 시간"
            RestPhase.FINISHED -> "휴식 종료 · 앱 설정 복구 완료"
        })
        val seconds = (ui.session.remainingMillis + 999) / 1000
        Text("%02d:%02d".format(seconds / 60, seconds % 60),
            style = MaterialTheme.typography.displayLarge)
        if (ui.active) {
            Text("연장 포함 계획: ${ui.session.plannedMinutes}분 · 현재 밝기 ${(ui.cabin.brightness * 100).toInt()}%")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExtend(5) }) { Text("+5분") }
                Button(onClick = { onExtend(10) }) { Text("+10분") }
            }
            OutlinedButton(onClick = onFinish, modifier = Modifier.heightIn(min = 56.dp)) { Text("즉시 종료") }
            Text("마지막 30초 동안 앱 화면이 점차 밝아집니다.")
        } else Button(onClick = onStart, enabled = validDuration && (ui.isDemo || ui.canStartRest),
            modifier = Modifier.heightIn(min = 56.dp)) { Text(if (ui.isDemo) "데모 다시 시작" else "휴식 시작") }
    }
}

@Composable
internal fun RestHistoryCard(history: RestHistoryState, isDemo: Boolean, onRetry: () -> Unit) {
    RestSection("최근 휴식 기록 (최대 50개)") {
        if (isDemo) Text("데모 세션은 실제 기록에 저장하지 않습니다.") else {
            if (history.pendingCount > 0) Text("미저장 기록 ${history.pendingCount}개")
            history.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onRetry) { Text("다시 저장/조회") }
            if (history.records.isEmpty()) Text("아직 저장된 기록이 없습니다.")
            history.records.forEach { record ->
                val reason = when (record.reason) {
                    EndReason.TIMER -> "시간 종료"
                    EndReason.MANUAL -> "직접 종료"
                    EndReason.VIEWMODEL_CLEARED -> "화면 수명 종료"
                    EndReason.DRIVING -> "주행 신호 확인으로 종료"
                }
                Text("${formatRestTime(record.startedAt)} · ${record.elapsedMillis / 60_000}분 ${record.elapsedMillis / 1000 % 60}초 · $reason")
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun RestSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun formatRestTime(millis: Long): String =
    SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
