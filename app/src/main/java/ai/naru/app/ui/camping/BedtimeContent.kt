package ai.naru.app.ui.camping

import ai.naru.app.camping.BedtimeState
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.BedtimeSettings
import ai.naru.app.camping.bedtime.RecommendationNote
import ai.naru.app.camping.bedtime.TemperatureApply
import ai.naru.app.camping.bedtime.TemperatureRecommendation
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.ui.theme.NaruTheme
import ai.naru.app.vehicle.FailureReason
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class BedtimeActions(
    val onAdjustTemperature: (Int) -> Unit = {},
    val onAdjustWakeTime: (Long) -> Unit = {},
    val onWakeAtSunrise: () -> Unit = {},
    val onSelectTheme: (PlaceType) -> Unit = {},
    val onStartNight: () -> Unit = {},
)

/** 취침 준비 단계: 설정 온도, 기상 시각, 밤 테마를 정하고 차박을 시작한다. */
@Composable
fun BedtimeContent(bedtime: BedtimeState?, actions: BedtimeActions) {
    if (bedtime == null) {
        Text(text = "오늘 밤 설정을 준비하고 있어요.", style = MaterialTheme.typography.titleMedium)
        return
    }
    val settings = bedtime.settings

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Section(title = "설정 온도") {
            Stepper(
                value = "${settings.targetTempC.formatTemp()}°C",
                onMinus = { actions.onAdjustTemperature(-1) },
                onPlus = { actions.onAdjustTemperature(+1) },
            )
            Text(text = recommendationText(bedtime.recommendation), style = MaterialTheme.typography.bodyLarge)
            if (bedtime.vehicleTempRange == null) {
                Text(text = "이 차량은 설정 온도 제어를 지원하지 않아요. 추천값을 참고해 직접 조절해 주세요.", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Section(title = "기상 시각") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper(
                    value = settings.wakeAt.format(timeFormat),
                    onMinus = { actions.onAdjustWakeTime(-30) },
                    onPlus = { actions.onAdjustWakeTime(+30) },
                )
                bedtime.sunrise?.let { sunrise ->
                    FilterChip(
                        selected = settings.wakeBasis == WakeBasis.SUNRISE,
                        onClick = actions.onWakeAtSunrise,
                        label = { Text("일출 ${sunrise.format(timeFormat)}에 깨우기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp)) },
                    )
                }
            }
        }

        Section(title = "밤 테마") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (theme in PlaceType.entries) {
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { actions.onSelectTheme(theme) },
                        label = { Text(theme.themeLabel(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp)) },
                    )
                }
            }
        }

        Section(title = "아침 예상 잔량") {
            Text(text = "실측 데이터가 쌓이면 예상 범위를 보여드릴게요.", style = MaterialTheme.typography.bodyLarge)
        }

        Button(onClick = actions.onStartNight, enabled = !bedtime.preparing) {
            Text(
                text = if (bedtime.preparing) "차량 설정을 준비하고 있어요" else "차박 시작하기",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** 밤 단계 상단에 보여줄 오늘 밤 설정 요약과 온도 적용 결과. */
@Composable
fun NightSummary(settings: BedtimeSettings?, temperatureApply: TemperatureApply?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        settings?.let {
            Text(
                text = "${it.theme.themeLabel()} 테마 · 기상 ${it.wakeAt.format(timeFormat)}${if (it.wakeBasis == WakeBasis.SUNRISE) " (일출)" else ""}",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        temperatureApply?.let { Text(text = it.notice(settings?.targetTempC), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 120.dp),
        )
        OutlinedButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun recommendationText(recommendation: TemperatureRecommendation): String {
    val base = "추천 ${recommendation.celsius.formatTemp()}°C"
    val reasons = recommendation.notes.map {
        when (it) {
            RecommendationNote.WEATHER_UNKNOWN -> "날씨 정보가 없어 기본값으로 추천했어요"
            RecommendationNote.LOW_BATTERY_SAVING -> "배터리가 적어 1°C 낮췄어요"
            RecommendationNote.CLAMPED_TO_VEHICLE_RANGE -> "차량이 지원하는 범위로 맞췄어요"
        }
    }
    return if (reasons.isEmpty()) "$base · 야간 최저기온 기준" else "$base · ${reasons.joinToString(" · ")}"
}

/** 온도 적용 결과 안내. 실패하면 자동으로 다시 시도하지 않고 직접 조작을 안내한다. */
private fun TemperatureApply.notice(targetC: Float?): String = when (this) {
    TemperatureApply.Applied -> "설정 온도를 ${targetC?.formatTemp() ?: "-"}°C로 맞췄어요."
    is TemperatureApply.Unverified -> "설정 온도 변경을 요청했지만 반영을 확인하지 못했어요. 차량 화면에서 확인해 주세요."
    TemperatureApply.NotSupported -> "이 차량은 설정 온도 제어를 지원하지 않아요. 필요하면 직접 조절해 주세요."
    is TemperatureApply.Failed -> when (reason) {
        FailureReason.PERMISSION -> "설정 온도를 바꿀 권한이 없어요. 차량에서 직접 조절해 주세요."
        else -> "설정 온도를 바꾸지 못했어요. 차량에서 직접 조절해 주세요."
    }
}

private fun PlaceType.themeLabel() = when (this) {
    PlaceType.BEACH -> "해변"
    PlaceType.FOREST -> "숲"
    PlaceType.LAKE -> "호수"
}

private fun Float.formatTemp(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun BedtimeContentPreview() {
    NaruTheme {
        BedtimeContent(
            bedtime = BedtimeState(
                settings = BedtimeSettings(22f, LocalTime.of(7, 0), WakeBasis.MANUAL, PlaceType.FOREST),
                recommendation = TemperatureRecommendation(22f, emptyList()),
                sunrise = LocalTime.of(7, 21),
                vehicleTempRange = 15f..30f,
            ),
            actions = BedtimeActions(),
        )
    }
}
