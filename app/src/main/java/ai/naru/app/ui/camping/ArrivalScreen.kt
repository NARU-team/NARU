package ai.naru.app.ui.camping

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ai.naru.app.camping.arrival.NearbyPlace
import ai.naru.app.camping.ArrivalState
import ai.naru.app.camping.NarrationState
import ai.naru.app.camping.arrival.ArrivalBadge
import ai.naru.app.camping.arrival.ArrivalCard
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
    /** [현재 위치로 찾기]: 권한이 없으면 요청한 뒤 위치로 찾는다 */
    val onLocate: () -> Unit = {},
    val onChoosePlaceType: (PlaceType) -> Unit = {},
    /** 근처 야영장 고르기 (이슈 #46) */
    val onChoosePlace: (NearbyPlace) -> Unit = {},
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
    locating: Boolean = false,
    /** 시연 지역이 아닌 현재 위치: 장소 유형을 물어본다 */
    askPlaceType: Boolean = false,
    /** 현재 위치 근처의 실제 야영장. 있으면 장소 유형 대신 먼저 보여준다 */
    nearbyPlaces: List<NearbyPlace> = emptyList(),
    loadingPlace: NearbyPlace? = null,
) {
    // [지금 이 자리]를 누르면 근처 야영장 대신 장소 유형을 묻는다 (화면 안 상태)
    var stayHere by remember(nearbyPlaces) { mutableStateOf(false) }
    FigmaFrame(modifier = modifier.fillMaxSize().background(NaruTheme.colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel),
            verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap),
        ) {
            NaruStepHeader(title = "도착 안내 1/3", onBack = actions.onBack)
            if (locating) {
                Message("현재 위치를 확인하고 있어요…", "차량 위치로 오늘 밤 날씨와 물때를 찾아요.")
            } else if (askPlaceType && nearbyPlaces.isNotEmpty() && !stayHere) {
                NearbyPicker(nearbyPlaces, loadingPlace, actions, onStayHere = { stayHere = true }, Modifier.weight(1f))
            } else if (askPlaceType) {
                PlaceTypeQuestion(actions, Modifier.weight(1f))
            } else if (arrival == null) {
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
    val p = ArrivalPresenter.present(arrival.site, arrival.conditions, arrival.assessment, arrival.retried, arrival.fetchedAt?.toLocalTime())
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(NaruIcons.Location, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
            val time = arrival.fetchedAt?.let { ", ${it.format(hhmm)}" } ?: ""
            Text(
                text = "${arrival.site.name.removeSuffix(" (시연)")}$time 조회",
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
            for (card in p.cards) ArrivalInfoCard(card, Modifier.weight(1f).fillMaxHeight())
        }
        NaruBottomBar(note = p.note) {
            if (p.buttons.confirmIsPrimary) {
                NaruPrimaryButton(text = p.buttons.confirmText, onClick = actions.onConfirm)
            } else {
                NaruSecondaryButton(text = p.buttons.confirmText, onClick = actions.onConfirm)
            }
            if (p.buttons.showRetry) NaruPrimaryButton(text = "한 번 더 불러오기", onClick = actions.onRetry)
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

/** 차량 위치 연동 전: 시연 지역 3곳 중에서 고른다 (디자인에 없는 화면, 같은 카드 스타일) */
@Composable
private fun SitePicker(sites: List<Site>, unavailable: Boolean, loading: Site?, actions: ArrivalActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "어디에 도착했나요?", style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Text(
                text = "현재 위치로 장소를 찾지 못했어요. 시연 지역에서 고르거나 [현재 위치로 찾기]를 눌러 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
            )
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
        NaruBottomBar(note = "고른 장소의 오늘 밤 날씨와 물때를 조회해요.") {
            NaruSecondaryButton(text = "현재 위치로 찾기", onClick = actions.onLocate)
        }
    }
}

/**
 * 시연 지역이 아닌 곳: 좌표만으로는 해변·숲·호수를 알 수 없어 한 번 묻는다 (테마·물때 판단에 필요).
 * 허용 여부·시설 데이터가 없어 판정은 "현장 확인 필요" 쪽으로 보수적으로 나온다.
 */
@Composable
private fun PlaceTypeQuestion(actions: ArrivalActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "어떤 곳인가요?", style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Text(
                text = "현재 위치의 오늘 밤 날씨를 조회했어요. 등록된 차박 장소가 아니라 허용 여부는 현장에서 확인해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
            )
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            for (type in PlaceType.entries) {
                NaruCard(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { actions.onChoosePlaceType(type) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
                        NaruIconCircle(type.icon())
                        Text(text = type.title(), style = MaterialTheme.typography.titleLarge, color = colors.text)
                        Text(text = type.hint(), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
            }
        }
        NaruBottomBar(note = "해변을 고르면 근처 조석 예보로 물때도 확인해요.") {
            NaruSecondaryButton(text = "시연 지역에서 고르기", onClick = actions.onChangeSite)
        }
    }
}

/** 근처 실제 야영장 (한국관광공사 고캠핑, 거리순 가까운 3곳) */
@Composable
private fun NearbyPicker(
    places: List<NearbyPlace>,
    loadingPlace: NearbyPlace?,
    actions: ArrivalActions,
    onStayHere: () -> Unit,
    modifier: Modifier,
) {
    val colors = NaruTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "근처 차박 장소", style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Text(
                text = "차량 위치에서 가까운 등록 야영장이에요. 고르면 그곳의 오늘 밤 날씨와 물때를 조회해요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
            )
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            for (place in places.take(NEARBY_SHOWN)) {
                val loading = loadingPlace == place
                NaruCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { if (loadingPlace == null) actions.onChoosePlace(place) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
                        NaruIconCircle((place.placeType ?: PlaceType.FOREST).icon())
                        Text(text = place.name, style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = listOfNotNull("%.1fkm".format(place.distanceKm), place.kind?.split(",")?.firstOrNull()?.trim()).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = if (loading) "불러오는 중…" else if (place.permission == CampingPermission.ALLOWED) "자동차야영장" else "차박 가능 여부 확인 필요",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (place.permission == CampingPermission.ALLOWED || loading) colors.accent else colors.textTertiary,
                        )
                        if (place.facilities.isNotEmpty()) {
                            Text(
                                text = "시설: ${place.facilities.take(3).joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        NaruBottomBar(note = "출처: 한국관광공사 고캠핑 정보. 오늘 밤 이용 가능 여부는 현장에서 확인해 주세요.") {
            NaruSecondaryButton(text = "지금 이 자리", onClick = onStayHere)
            NaruSecondaryButton(text = "시연 지역에서 고르기", onClick = actions.onChangeSite)
        }
    }
}

private const val NEARBY_SHOWN = 3

@Composable
private fun Message(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = NaruTheme.colors.text)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = NaruTheme.colors.textTertiary)
    }
}

private fun PlaceType.title() = when (this) {
    PlaceType.BEACH -> "해변"
    PlaceType.FOREST -> "숲"
    PlaceType.LAKE -> "호수"
}

private fun PlaceType.hint() = when (this) {
    PlaceType.BEACH -> "바닷가, 해수욕장"
    PlaceType.FOREST -> "산, 캠핑장, 휴양림"
    PlaceType.LAKE -> "호숫가, 강가, 저수지"
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
