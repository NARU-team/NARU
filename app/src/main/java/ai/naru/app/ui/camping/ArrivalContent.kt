package ai.naru.app.ui.camping

import ai.naru.app.camping.ArrivalState
import ai.naru.app.camping.arrival.ArrivalGrade
import ai.naru.app.camping.arrival.ArrivalReason
import ai.naru.app.camping.arrival.CampingPermission
import ai.naru.app.camping.arrival.ArrivalAssessment
import ai.naru.app.camping.arrival.NightWeather
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.arrival.Precipitation
import ai.naru.app.camping.arrival.Site
import ai.naru.app.camping.arrival.SiteConditions
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/** 도착 단계: 장소를 고르고 3단계 안내 카드를 본다. */
@Composable
fun ArrivalContent(
    sites: List<Site>,
    arrival: ArrivalState?,
    onSelectSite: (Site) -> Unit,
    onConfirm: () -> Unit,
    sitesUnavailable: Boolean = false,
    onRetrySites: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "어디에서 머무르나요?", style = MaterialTheme.typography.titleLarge)
        if (sitesUnavailable && sites.isEmpty()) {
            Text(text = "장소 정보를 불러오지 못했어요. 네트워크 연결을 확인해 주세요.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onRetrySites) { Text("다시 불러오기") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (site in sites) {
                FilterChip(
                    selected = arrival?.site?.id == site.id,
                    onClick = { onSelectSite(site) },
                    label = { Text(text = site.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp)) },
                )
            }
        }

        if (arrival != null) {
            ArrivalCard(arrival)
            Button(onClick = onConfirm) {
                Text(text = "확인했어요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun ArrivalCard(arrival: ArrivalState) {
    val grade = arrival.assessment.grade
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = grade.containerColor()),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = grade.title(), style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(text = grade.guide(), style = MaterialTheme.typography.titleMedium, color = Color.White)

            if (arrival.assessment.reasons.isNotEmpty()) {
                Text(text = "판단 근거", style = MaterialTheme.typography.titleMedium, color = Color.White)
                for (reason in arrival.assessment.reasons) {
                    Text(text = "· ${reason.label()}", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            }

            Text(text = "확인된 정보", style = MaterialTheme.typography.titleMedium, color = Color.White)
            for (line in summaryLines(arrival.site, arrival.conditions)) {
                Text(text = "· $line", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        }
    }
}

/** 등급별 준비된 문장. 장소의 안전이나 휴식 효과를 보장하는 표현은 쓰지 않는다. */
private fun ArrivalGrade.title() = when (this) {
    ArrivalGrade.INFO -> "정보 확인"
    ArrivalGrade.CHECK -> "현장 확인 필요"
    ArrivalGrade.MOVE -> "주의 · 이동 권장"
}

private fun ArrivalGrade.guide() = when (this) {
    ArrivalGrade.INFO -> "확인된 정보를 정리했어요. 도착 후 주변 상황도 함께 살펴봐 주세요."
    ArrivalGrade.CHECK -> "확인되지 않은 정보가 있어요. 현장 안내판과 주변 상황을 직접 확인해 주세요."
    ArrivalGrade.MOVE -> "주의가 필요한 정보가 있어요. 다른 장소로 이동하는 것을 권장해요."
}

private fun ArrivalGrade.containerColor() = when (this) {
    ArrivalGrade.INFO -> Color(0xFF2E5E4E)
    ArrivalGrade.CHECK -> Color(0xFF7A5A12)
    ArrivalGrade.MOVE -> Color(0xFF8C2F2F)
}

private fun ArrivalReason.label() = when (this) {
    ArrivalReason.Prohibited -> "차박이 금지된 장소로 확인돼요"
    ArrivalReason.PermissionUnknown -> "차박 허용 여부가 확인되지 않았어요"
    is ArrivalReason.ExtremeCold -> "야간 최저기온 ${minTempC.format()}°C로 매우 추워요"
    is ArrivalReason.Cold -> "야간 최저기온 ${minTempC.format()}°C로 영하예요"
    ArrivalReason.HeavyRain -> "밤사이 강한 비 예보가 있어요"
    ArrivalReason.Rain -> "밤사이 비 예보가 있어요"
    ArrivalReason.StrongWind -> "밤사이 강한 바람 예보가 있어요"
    is ArrivalReason.NightHighTide -> "밤사이 ${at.format(timeFormat)}에 만조예요"
    ArrivalReason.WeatherUnavailable -> "날씨 정보를 불러오지 못했어요"
    ArrivalReason.TideUnavailable -> "물때 정보를 불러오지 못했어요"
}

private fun summaryLines(site: Site, conditions: SiteConditions): List<String> = buildList {
    add("${site.name} · ${site.placeType.label()}")
    add(
        when (site.permission) {
            CampingPermission.ALLOWED -> "차박 허용 장소"
            CampingPermission.UNKNOWN -> "차박 허용 여부 확인 필요"
            CampingPermission.PROHIBITED -> "차박 금지 장소"
        },
    )
    conditions.weather?.let { add("야간 최저기온 ${it.minTempC.format()}°C · ${it.precipitation.label()}") }
    if (site.placeType == PlaceType.BEACH) {
        conditions.highTides?.let { tides -> add("만조 ${tides.joinToString(", ") { it.format(timeFormat) }}") }
    }
    if (site.facilities.isNotEmpty()) add("시설: ${site.facilities.joinToString(", ")}")
}

private fun PlaceType.label() = when (this) {
    PlaceType.BEACH -> "해변"
    PlaceType.FOREST -> "숲"
    PlaceType.LAKE -> "호수"
}

private fun Precipitation.label() = when (this) {
    Precipitation.NONE -> "비 예보 없음"
    Precipitation.RAIN -> "비 예보"
    Precipitation.HEAVY_RAIN -> "강한 비 예보"
}

private fun Float.format(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ArrivalContentPreview() {
    val site = Site("p", "시연용 해변", PlaceType.BEACH, CampingPermission.ALLOWED, listOf("화장실", "샤워장"))
    val conditions = SiteConditions(NightWeather(9f, Precipitation.NONE, false), listOf(LocalTime.of(2, 40)))
    NaruTheme {
        ArrivalContent(
            sites = listOf(site),
            arrival = ArrivalState(site, conditions, ArrivalAssessment(ArrivalGrade.MOVE, listOf(ArrivalReason.NightHighTide(LocalTime.of(2, 40))))),
            onSelectSite = {},
            onConfirm = {},
        )
    }
}
