package ai.naru.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 나루 디자인 토큰 (피그마 v2 "고도화 v2 핵심 화면", 이슈 #29).
 *
 * 색상은 피그마에서 내보낸 PNG의 픽셀에서 뽑은 근사값이다. Dev Mode 값을 받으면 이 파일만 고친다.
 * 화면 코드는 색상 코드를 직접 쓰지 않고 이 토큰(또는 MaterialTheme.colorScheme)만 쓴다.
 */
@Immutable
data class NaruColors(
    /** 패널 바탕 */
    val background: Color = Color(0xFFF7F8FA),
    /** 카드, 세그먼트 선택 칸 */
    val card: Color = Color(0xFFFFFFFF),
    /** 제목·본문 */
    val text: Color = Color(0xFF131417),
    /** 카드 설명 */
    val textSecondary: Color = Color(0xFF3A3B3E),
    /** 출처, 하단 안내 문구, 캡션 */
    val textTertiary: Color = Color(0xFF67686B),
    /** 주요 버튼 ("밤 분위기 고르기", "차박 종료") */
    val primaryButton: Color = Color(0xFF44464E),
    val onPrimaryButton: Color = Color(0xFFFFFFFF),
    /** 보조 버튼, 정보 칩 */
    val secondaryButton: Color = Color(0xFFEAEDF3),
    /** 나루 추천 배지, 선택 테두리, 기상 링, 배터리 바 */
    val accent: Color = Color(0xFF0064FF),
    /** 선택된 행·선택된 카드 바탕 */
    val accentContainer: Color = Color(0xFFEBF3FF),
    /** 나루 제안 패널 바탕, 아이콘 원 */
    val accentSurface: Color = Color(0xFFF4F8FF),
    /** 토글 켜짐 */
    val toggleOn: Color = Color(0xFF02C265),
    /** 주의 카드 (이동 권장, 조회 실패) */
    val warningCard: Color = Color(0xFFFFF3F1),
    val warningBadge: Color = Color(0xFFF8E5E3),
    val warningIcon: Color = Color(0xFFFC7C63),
    val warningIconCircle: Color = Color(0xFFFFDDD7),
    /** 주의 카드 테두리, 주의 이름, 강한 바람 막대, 경고 팝업 아이콘 (피그마 v2 갱신) */
    val danger: Color = Color(0xFFF2453D),
    /** 아이콘 원 (기본) */
    val iconCircle: Color = Color(0xFFEAEDF3),
    /** 세그먼트 버튼 바탕 */
    val segment: Color = Color(0xFFEBECEE),
    /** 진행 바 바탕 */
    val track: Color = Color(0xFFE3E5E9),
    /** 밤 카드 그라데이션과 달 */
    val nightTop: Color = Color(0xFF313E6A),
    val nightBottom: Color = Color(0xFF687CB4),
    val moon: Color = Color(0xFFF3EBD3),
    /** 아침 브리핑 배너 */
    val morningBanner: Color = Color(0xFFFFE6CB),
) {
    val nightBrush: Brush get() = Brush.verticalGradient(listOf(nightTop, nightBottom))
    val morningBrush: Brush get() = Brush.horizontalGradient(listOf(morningBanner, morningBanner.copy(alpha = 0.45f)))
}

/** 모서리 (피그마: 카드·패널 약 20dp, 버튼 약 16dp, 칩·배지 알약) */
object NaruShapes {
    val panel = RoundedCornerShape(24.dp)
    val card = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** 간격 */
object NaruSpacing {
    /** 패널 안쪽 여백 */
    val panel = 28.dp
    /** 카드 안쪽 여백 */
    val card = 20.dp
    /** 카드 사이 */
    val gap = 16.dp
    /** 작은 요소 사이 (칩, 아이콘과 글자) */
    val small = 8.dp
}
