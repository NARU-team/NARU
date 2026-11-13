package ai.naru.app.ui.components

import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** 공통 컴포넌트 미리보기 (피그마 도착 안내·취침 준비의 요소를 모아 비교용) */
@Preview(widthDp = 1280, heightDp = 760)
@Composable
private fun ComponentGalleryPreview() {
    NaruTheme {
        var minutes by remember { mutableStateOf(60) }
        Column(
            modifier = Modifier.background(NaruTheme.colors.background).padding(NaruSpacing.panel),
            verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap),
        ) {
            NaruStepHeader(title = "도착 안내 1/3", onBack = {}, onClose = {})
            Row(horizontalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
                NaruChip("배터리 72%", icon = NaruIcons.Battery)
                NaruChip("실외 최저 6°C", icon = NaruIcons.Moon)
                NaruBadge("이동 권장", BadgeTone.WARNING, icon = NaruIcons.Wind)
                NaruBadge("나루 추천 21°C", BadgeTone.ACCENT)
                NaruBadge("현장 확인 필요", BadgeTone.NEUTRAL, icon = NaruIcons.Location)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
                NaruInfoCard(NaruIcons.Wind, "오늘 밤 날씨 (주의)", "강한 바람 14m/s", Modifier.weight(1f),
                    description = "밤새 강한 바람이 예보돼 있어요. 최저 3°C예요", source = "기상청 단기예보", tone = CardTone.WARNING)
                NaruInfoCard(NaruIcons.Tide, "내일 새벽 물때", "만조 02:14", Modifier.weight(1f),
                    description = "자는 동안 물이 가장 높아져요", source = "국립해양조사원 조석예보")
                NaruInfoCard(NaruIcons.Warning, "이용 정보 (확인 필요)", "화장실 있어요", Modifier.weight(1f),
                    description = "현장 안내판을 확인해 주세요", source = "한국관광공사 시설 정보")
            }
            NaruSegmented(listOf(30, 60, 90), minutes, { "${it}분 후" }, { minutes = it })
            Row(horizontalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
                NaruSecondaryButton("더 따뜻하게", {})
                NaruSwitch(checked = true, onCheckedChange = {})
            }
            NaruBottomBar(note = "나루는 조회한 정보만 알려 드려요. 머무를지는 직접 판단해 주세요.", noteIcon = NaruIcons.Check) {
                NaruSecondaryButton("그래도 준비하기", {})
                NaruPrimaryButton("밤 분위기 고르기", {})
            }
        }
    }
}
