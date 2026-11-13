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
import ai.naru.app.camping.arrival.guide
import ai.naru.app.camping.arrival.label
import ai.naru.app.camping.arrival.title
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

private fun ArrivalGrade.containerColor() = when (this) {
    ArrivalGrade.INFO -> Color(0xFF2E5E4E)
    ArrivalGrade.CHECK -> Color(0xFF7A5A12)
    ArrivalGrade.MOVE -> Color(0xFF8C2F2F)
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
    conditions.weather?.let { add("야간 최저기온 ${it.minTempC.formatTemp()}°C · ${it.precipitation.label()}") }
    if (site.placeType == PlaceType.BEACH) {
        conditions.highTides?.let { tides -> add("만조 ${tides.joinToString(", ") { it.format(timeFormat) }}") }
    }
    if (site.facilities.isNotEmpty()) add("시설: ${site.facilities.joinToString(", ")}")
}

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
