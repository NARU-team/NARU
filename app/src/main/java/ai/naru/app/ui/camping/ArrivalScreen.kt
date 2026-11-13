package ai.naru.app.ui.camping

import ai.naru.app.camping.ArrivalState
import ai.naru.app.camping.NarrationState
import ai.naru.app.camping.arrival.ArrivalBadge
import ai.naru.app.camping.arrival.ArrivalCard
import ai.naru.app.camping.arrival.ArrivalCardKind
import ai.naru.app.camping.arrival.ArrivalPresenter
import ai.naru.app.camping.arrival.CampingPermission
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.arrival.Site
import ai.naru.app.camping.narration.NarrationMoment
import ai.naru.app.ui.components.BadgeTone
import ai.naru.app.ui.components.CardTone
import ai.naru.app.ui.components.NaruBadge
import ai.naru.app.ui.components.NaruBottomBar
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruChip
import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.components.NaruInfoCard
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruSecondaryButton
import ai.naru.app.ui.components.NaruStepHeader
import ai.naru.app.ui.theme.FigmaFrame
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 도착 안내에서 누를 수 있는 동작 */
data class ArrivalActions(
    val onBack: () -> Unit = {},
    val onSelectSite: (Site) -> Unit = {},
    val onRetrySites: () -> Unit = {},
    val onChangeSite: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onConfirm: () -> Unit = {},
)

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 피그마 v2 "도착 안내 1/3" (이슈 #35). 장소를 고르기 전에는 시연 지역 고르기, 고른 뒤에는 판정 결과.
 * 판정·문구는 [ArrivalPresenter]가 정하고, 이 화면은 그리기만 한다.
 */
@Composable
fun ArrivalScreen(
    sites: List<Site>,
    sitesUnavailable: Boolean,
    loadingSite: Site?,
    arrival: ArrivalState?,
    narration: NarrationState?,
    actions: ArrivalActions,
    modifier: Modifier = Modifier,
) {
    FigmaFrame(modifier = modifier.fillMaxSize().background(NaruTheme.colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel),
            verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap),
        ) {
            NaruStepHeader(title = "도착 안내 1/3", onBack = actions.onBack)
            if (arrival == null) {
                SitePicker(sites, sitesUnavailable, loadingSite, actions, Modifier.weight(1f))
            } else {
                ArrivalResult(arrival, narration?.takeIf { it.moment == NarrationMoment.ARRIVAL }, actions, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ArrivalResult(arrival: ArrivalState, narration: NarrationState?, actions: ArrivalActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    val p = ArrivalPresenter.present(arrival.site, arrival.conditions, arrival.assessment, arrival.retried)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(NaruIcons.Location, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
            val time = arrival.fetchedAt?.let { " ${it.format(hhmm)}에" } ?: ""
            Text(
                text = "${arrival.site.name.removeSuffix(" (시연)")}에서$time 조회했어요",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
            )
            NaruChip(text = "장소 바꾸기", onClick = actions.onChangeSite)
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = p.title, style = MaterialTheme.typography.headlineMedium, color = colors.text)
                p.subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary) }
            }
            Badge(p.badge, p.badgeText)
        }
        narration?.let { NarrationLine(it) }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            for (card in p.cards) {
                NaruInfoCard(
                    icon = card.kind.icon(),
                    label = card.label,
                    value = card.value,
                    description = card.description,
                    source = card.source,
                    tone = if (card.warning) CardTone.WARNING else CardTone.DEFAULT,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        NaruBottomBar(note = p.note) {
            if (p.buttons.confirmIsPrimary) {
                NaruPrimaryButton(text = p.buttons.confirmText, onClick = actions.onConfirm)
            } else {
                NaruSecondaryButton(text = p.buttons.confirmText, onClick = actions.onConfirm)
            }
            if (p.buttons.showRetry) NaruPrimaryButton(text = "다시 시도", onClick = actions.onRetry)
        }
    }
}

/** 나루 AI 멘트 (판정은 위의 제목·배지가 규칙으로 정한 값) */
@Composable
private fun NarrationLine(narration: NarrationState) {
    val colors = NaruTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(NaruIcons.Sparkle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        Text(
            text = if (narration.pending) "나루가 안내를 다듬고 있어요…" else narration.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun Badge(badge: ArrivalBadge, text: String) {
    when (badge) {
        ArrivalBadge.INFO -> NaruBadge(text, BadgeTone.INFO, icon = NaruIcons.Check)
        ArrivalBadge.CHECK -> NaruBadge(text, BadgeTone.NEUTRAL, icon = NaruIcons.Location)
        ArrivalBadge.MOVE -> NaruBadge(text, BadgeTone.WARNING, icon = NaruIcons.Wind)
    }
}

@Composable
private fun ArrivalCardKind.icon(): ImageVector = when (this) {
    ArrivalCardKind.WEATHER -> NaruIcons.Moon
    ArrivalCardKind.TIDE -> NaruIcons.Tide
    ArrivalCardKind.SUNRISE -> NaruIcons.Sunrise
    ArrivalCardKind.USAGE -> NaruIcons.Warning
}

/** 차량 위치 연동 전: 시연 지역 3곳 중에서 고른다 (디자인에 없는 화면, 같은 카드 스타일) */
@Composable
private fun SitePicker(sites: List<Site>, unavailable: Boolean, loading: Site?, actions: ArrivalActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "어디에 도착했나요?", style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Text(text = "차량 위치 연동 전이라 시연 지역에서 골라 주세요.", style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
        }
        if (unavailable && sites.isEmpty()) {
            Text(text = "장소 정보를 불러오지 못했어요. 네트워크 연결을 확인해 주세요.", style = MaterialTheme.typography.bodyLarge, color = colors.text)
            NaruSecondaryButton(text = "다시 불러오기", onClick = actions.onRetrySites)
            return
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            for (site in sites) {
                NaruCard(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { actions.onSelectSite(site) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
                        NaruIconCircle(site.placeType.icon())
                        Text(text = site.name.removeSuffix(" (시연)"), style = MaterialTheme.typography.titleLarge, color = colors.text)
                        Text(text = site.permission.text(), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        if (site.facilities.isNotEmpty()) {
                            Text(text = "시설: ${site.facilities.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
                        }
                        Spacer(Modifier.weight(1f))
                        if (loading == site) Text(text = "오늘 밤 정보를 불러오는 중…", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                    }
                }
            }
        }
        NaruBottomBar(note = "고른 장소의 오늘 밤 날씨와 물때를 조회해요.") {}
    }
}

@Composable
private fun PlaceType.icon(): ImageVector = when (this) {
    PlaceType.BEACH -> NaruIcons.Tide
    PlaceType.FOREST -> NaruIcons.Tent
    PlaceType.LAKE -> NaruIcons.Moon
}

private fun CampingPermission.text() = when (this) {
    CampingPermission.ALLOWED -> "등록된 차박 장소"
    CampingPermission.UNKNOWN -> "차박 허용 여부 확인 필요"
    CampingPermission.PROHIBITED -> "차박 금지 장소"
}

@Preview(widthDp = 850, heightDp = 590)
@Composable
private fun ArrivalScreenMovePreview() {
    val site = Site("demo-beach", "태안 꽃지해수욕장 (시연)", PlaceType.BEACH, CampingPermission.UNKNOWN, listOf("화장실"))
    val conditions = ai.naru.app.camping.arrival.SiteConditions(
        weather = ai.naru.app.camping.arrival.NightWeather(3f, ai.naru.app.camping.arrival.Precipitation.NONE, strongWind = true, maxWindMs = 14f),
        highTides = listOf(LocalTime.of(2, 14)),
        lowTides = listOf(LocalTime.of(8, 31)),
    )
    val assessment = ai.naru.app.camping.arrival.ArrivalRules.assess(site, conditions)
    NaruTheme {
        ArrivalScreen(
            sites = listOf(site),
            sitesUnavailable = false,
            loadingSite = null,
            arrival = ArrivalState(site, conditions, assessment, fetchedAt = LocalDateTime.of(2026, 11, 13, 16, 20)),
            narration = null,
            actions = ArrivalActions(),
        )
    }
}
