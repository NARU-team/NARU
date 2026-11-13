package ai.naru.app.camping.arrival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ArrivalPresenterTest {

    private val beach = Site("b", "태안 꽃지해수욕장 (시연)", PlaceType.BEACH, CampingPermission.ALLOWED, listOf("화장실", "샤워장"))
    private val forest = Site("f", "가평 숲 캠핑장 (시연)", PlaceType.FOREST, CampingPermission.ALLOWED, listOf("화장실", "개수대"))
    private val calm = NightWeather(6f, Precipitation.NONE, strongWind = false, maxWindMs = 2f)

    private fun present(site: Site, conditions: SiteConditions, retried: Boolean = false) =
        ArrivalPresenter.present(site, conditions, ArrivalRules.assess(site, conditions), retried)

    @Test
    fun `특이사항이 없으면 정보 확인`() {
        val p = present(forest, SiteConditions(calm, null, sunrise = LocalTime.of(7, 8)))
        assertEquals(ArrivalStatus.INFO, p.state)
        assertEquals("조회한 정보에 특이사항이 없어요", p.title)
        assertEquals("정보 확인", p.badgeText)
        assertEquals(ArrivalButtons("취침 준비하기", confirmIsPrimary = true, showRetry = false), p.buttons)
        assertEquals("최저 6°C", p.cards[0].value)
        assertEquals("바람 2m/s, 비 소식 없음", p.cards[0].description)
    }

    @Test
    fun `허용 여부가 불명확하면 현장 확인, 근거에 맞는 제목`() {
        val p = present(beach.copy(permission = CampingPermission.UNKNOWN), SiteConditions(calm, listOf(LocalTime.of(14, 0))))
        assertEquals(ArrivalStatus.CHECK, p.state)
        assertEquals("차박 허용 여부를 확인해 주세요", p.title)
        assertEquals("현장 안내판에서 확인할 수 있어요", p.subtitle)
        assertEquals("현장 확인 후 계속", p.buttons.confirmText)
        assertEquals("차박 허용 여부를 찾지 못했어요", p.cards[2].value)
    }

    @Test
    fun `강한 바람과 밤사이 만조면 이동 권장, 주의 카드와 보조 버튼`() {
        val windy = NightWeather(3f, Precipitation.NONE, strongWind = true, maxWindMs = 14f)
        val p = present(beach, SiteConditions(windy, listOf(LocalTime.of(2, 14)), lowTides = listOf(LocalTime.of(8, 31))))
        assertEquals(ArrivalStatus.MOVE, p.state)
        assertEquals("오늘 밤은 다른 곳을 권해요", p.title)
        assertEquals("이동 권장", p.badgeText)
        assertFalse(p.buttons.confirmIsPrimary)
        assertEquals("그래도 준비하기", p.buttons.confirmText)
        assertEquals("강한 바람 14m/s", p.cards[0].value)
        assertTrue(p.cards[0].warning)
        assertEquals("만조 02:14", p.cards[1].value)
        assertEquals("자는 동안 물이 가장 높아져요\n간조는 08:31이에요", p.cards[1].description)
        assertTrue(p.cards[1].warning)
    }

    @Test
    fun `날씨를 못 불러오면 조회 실패, 다시 시도는 한 번만`() {
        val first = present(forest, SiteConditions(null, null))
        assertEquals(ArrivalStatus.LOOKUP_FAILED, first.state)
        assertEquals("일부 정보를 불러오지 못했어요", first.title)
        assertEquals("날씨는 한 번 더 불러올 수 있어요", first.subtitle)
        assertEquals(ArrivalButtons("취침 준비하기", confirmIsPrimary = false, showRetry = true), first.buttons)
        assertEquals("조회 실패", first.cards[0].value)

        val again = present(forest, SiteConditions(null, null), retried = true)
        assertEquals(ArrivalButtons("취침 준비하기", confirmIsPrimary = true, showRetry = false), again.buttons)
    }

    @Test
    fun `이동 권장 근거가 있으면 조회 실패보다 먼저 보여준다`() {
        val p = present(beach.copy(permission = CampingPermission.PROHIBITED), SiteConditions(null, null))
        assertEquals(ArrivalStatus.MOVE, p.state)
        assertEquals("차박 금지 장소예요", p.cards[2].value)
    }

    @Test
    fun `해변이 아니면 물때 대신 일출 카드`() {
        val p = present(forest, SiteConditions(calm, null, sunrise = LocalTime.of(7, 8)))
        assertEquals(ArrivalCardKind.SUNRISE, p.cards[1].kind)
        assertEquals("07:08", p.cards[1].value)
    }

    @Test
    fun `시설 목록 뒤 조사는 받침에 맞춘다`() {
        assertEquals("화장실, 샤워장이 있어요\n오늘 밤 이용 가능 여부는 현장에서 확인해 주세요", present(beach, SiteConditions(calm, listOf())).cards[2].description)
        assertEquals("이", subjectParticle("샤워장"))
        assertEquals("가", subjectParticle("개수대"))
    }

    @Test
    fun `이용 정보 출처는 사실대로 (시연 지역 내장)`() {
        assertEquals("시연 지역 내장 정보", present(forest, SiteConditions(calm, null)).cards[2].source)
    }
}
