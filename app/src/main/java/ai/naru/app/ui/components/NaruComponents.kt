package ai.naru.app.ui.components

import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 나루 공통 컴포넌트 (피그마 v2, 이슈 #29). 화면은 색·모양을 직접 정하지 않고 이 컴포넌트를 조합한다.
 */

/** 단계 헤더: 뒤로가기 + 제목("취침 준비 2/3") + 닫기(X, 선택) */
@Composable
fun NaruStepHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = NaruTheme.colors.text)
            }
            Spacer(Modifier.size(4.dp))
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = NaruTheme.colors.text, modifier = Modifier.weight(1f))
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = NaruTheme.colors.text)
            }
        }
    }
}

/** 카드 분위기: 기본(흰색) / 주의(연한 빨강, 이동 권장·조회 실패) / 강조(선택됨) */
enum class CardTone { DEFAULT, WARNING, SELECTED }

/** 흰 카드 바탕. 선택되면 강조 테두리와 연한 파랑 바탕. */
@Composable
fun NaruCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.DEFAULT,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(NaruSpacing.card),
    content: @Composable () -> Unit,
) {
    val colors = NaruTheme.colors
    val background = when (tone) {
        CardTone.DEFAULT -> colors.card
        CardTone.WARNING -> colors.warningCard
        CardTone.SELECTED -> colors.accentContainer
    }
    var m = modifier.clip(NaruShapes.card).background(background)
    if (tone == CardTone.SELECTED) m = m.border(2.dp, colors.accent, NaruShapes.card)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(modifier = m.padding(contentPadding)) { content() }
}

/** 아이콘 원 (카드 이름 앞) */
@Composable
fun NaruIconCircle(icon: ImageVector, tone: CardTone = CardTone.DEFAULT, size: Int = 36) {
    val colors = NaruTheme.colors
    val (bg, tint) = when (tone) {
        CardTone.WARNING -> colors.warningIconCircle to colors.warningIcon
        CardTone.SELECTED -> colors.accentSurface to colors.accent
        CardTone.DEFAULT -> colors.iconCircle to colors.textSecondary
    }
    Box(
        modifier = Modifier.size(size.dp).clip(NaruShapes.pill).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/**
 * 정보 카드: 아이콘 원 + 이름 → 큰 값 → 설명 → (구분선) 출처.
 * 예: 도착 안내의 "오늘 밤 날씨 / 강한 바람 14m/s / 밤새 강한 바람이… / 기상청 단기예보"
 */
@Composable
fun NaruInfoCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    source: String? = null,
    tone: CardTone = CardTone.DEFAULT,
) {
    val colors = NaruTheme.colors
    NaruCard(modifier = modifier, tone = tone) {
        Column(verticalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
                NaruIconCircle(icon, tone = if (tone == CardTone.WARNING) CardTone.WARNING else CardTone.DEFAULT)
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            }
            Text(text = value, style = MaterialTheme.typography.headlineLarge, color = colors.text)
            description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
            source?.let {
                Spacer(Modifier.height(NaruSpacing.small))
                HorizontalDivider(color = colors.segment)
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
            }
        }
    }
}

/** 정보 칩: 아이콘 + 짧은 글자 ("배터리 72%", "실외 최저 6°C") */
@Composable
fun NaruChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val colors = NaruTheme.colors
    var m = modifier.clip(NaruShapes.pill).background(if (onClick != null) colors.secondaryButton else colors.card)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        modifier = m.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)) }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.text)
    }
}

/** 배지 분위기 */
enum class BadgeTone { ACCENT, WARNING, NEUTRAL }

/** 배지: "나루 추천 21°C"(강조), "이동 권장"(주의), "현장 확인 필요"(보통) */
@Composable
fun NaruBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = NaruTheme.colors
    val (bg, fg) = when (tone) {
        BadgeTone.ACCENT -> colors.accent to Color.White
        BadgeTone.WARNING -> colors.warningBadge to colors.text
        BadgeTone.NEUTRAL -> colors.secondaryButton to colors.text
    }
    Row(
        modifier = modifier.clip(if (tone == BadgeTone.ACCENT) NaruShapes.button else NaruShapes.pill).background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = if (tone == BadgeTone.WARNING) colors.warningIcon else fg, modifier = Modifier.size(16.dp)) }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** 주요 버튼 (진회색): 화면마다 하나, 다음 단계로 가는 동작 */
@Composable
fun NaruPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = NaruShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = NaruTheme.colors.primaryButton,
            contentColor = NaruTheme.colors.onPrimaryButton,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) { Text(text = text, style = MaterialTheme.typography.labelLarge) }
}

/** 보조 버튼 (연회색): "그래도 준비하기", "더 따뜻하게", "직접 설정" */
@Composable
fun NaruSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = NaruShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = NaruTheme.colors.secondaryButton,
            contentColor = NaruTheme.colors.text,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text = text, style = MaterialTheme.typography.labelLarge) }
}

/** 세그먼트 버튼: "30분 후 / 60분 후 / 90분 후" */
@Composable
fun <T> NaruSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NaruTheme.colors
    Row(
        modifier = modifier.clip(NaruShapes.button).background(colors.segment).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in options) {
            val isSelected = option == selected
            Box(
                modifier = Modifier.weight(1f).clip(NaruShapes.button)
                    .background(if (isSelected) colors.card else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colors.text else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 토글 (켜짐 초록) */
@Composable
fun NaruSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = NaruTheme.colors.toggleOn,
            checkedThumbColor = Color.White,
            checkedBorderColor = NaruTheme.colors.toggleOn,
        ),
    )
}

/** 하단 줄: 왼쪽 안내 문구(아이콘) + 오른쪽 버튼들 */
@Composable
fun NaruBottomBar(
    note: String?,
    modifier: Modifier = Modifier,
    noteIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = NaruTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap),
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            noteIcon?.let { Icon(it, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp)) }
            note?.let { Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

/** 확인 팝업: "차박을 끝낼까요?" 왼쪽 주요(실행) / 오른쪽 보조(취소) — 피그마 배치 */
@Composable
fun NaruConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.clip(NaruShapes.panel).background(NaruTheme.colors.card).padding(NaruSpacing.panel),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = NaruTheme.colors.text)
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = NaruTheme.colors.textSecondary, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NaruPrimaryButton(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
                NaruSecondaryButton(text = dismissText, onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}
