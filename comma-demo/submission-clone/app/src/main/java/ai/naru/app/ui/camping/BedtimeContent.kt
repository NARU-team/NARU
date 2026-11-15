package ai.naru.app.ui.camping

import ai.naru.app.camping.BedtimeState
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.BedtimeSettings
import ai.naru.app.camping.bedtime.RecommendationNote
import ai.naru.app.camping.bedtime.TemperatureRecommendation
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.ui.theme.NaruTheme
import ai.naru.app.camping.night.NightThresholds
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
            val forecast = bedtime.forecast
            if (forecast == null) {
                Text(text = "실측 데이터가 쌓이면 예상 범위를 보여드릴게요.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(text = "${forecast.lowPercent}~${forecast.highPercent}%", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "비슷한 조건의 지난 기록 ${forecast.basedOn}건으로 계산했어요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (forecast.belowLimit) {
                    Text(
                        text = "아침에 배터리가 ${NightThresholds.BATTERY_LIMIT_PERCENT}%보다 낮아질 수 있어요. 설정 온도를 조금 낮추는 걸 권해요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
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

internal val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun recommendationText(recommendation: TemperatureRecommendation): String {
    val base = "추천 ${recommendation.celsius.formatTemp()}°C"
    val reasons = recommendation.notes.map {
        when (it) {
            RecommendationNote.WEATHER_UNKNOWN -> "날씨 정보가 없어 기본값으로 추천했어요"
            RecommendationNote.LOW_BATTERY_SAVING -> "배터리가 적어 1°C 낮췄어요"
            RecommendationNote.CLAMPED_TO_VEHICLE_RANGE -> "차량이 지원하는 범위로 맞췄어요"
            RecommendationNote.FEEDBACK_APPLIED -> "지난 비슷한 밤의 온도 느낌을 반영했어요"
        }
    }
    return if (reasons.isEmpty()) "$base · 야간 최저기온 기준" else "$base · ${reasons.joinToString(" · ")}"
}

internal fun PlaceType.themeLabel() = when (this) {
    PlaceType.BEACH -> "해변"
    PlaceType.FOREST -> "숲"
    PlaceType.LAKE -> "호수"
}

internal fun Float.formatTemp(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

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
