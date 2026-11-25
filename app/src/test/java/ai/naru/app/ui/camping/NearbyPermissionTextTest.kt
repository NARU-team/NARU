package ai.naru.app.ui.camping

import ai.naru.app.camping.arrival.CampingPermission
import ai.naru.app.camping.arrival.NearbyPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 근처 야영장 카드 허용 표시 (이슈 #69) */
class NearbyPermissionTextTest {
    private fun place(permission: CampingPermission, licensed: Boolean) = NearbyPlace(
        id = "1", name = "추암오토캠핑장", kind = "자동차야영장", placeType = null, permission = permission,
        facilities = emptyList(), distanceKm = 0.5, lat = 37.477, lon = 129.159, licensed = licensed,
    )

    @Test
    fun `인허가 영업 중이면 인허가 등록 표시가 가장 먼저`() {
        assertEquals("인허가 등록 자동차야영장", nearbyPermissionText(place(CampingPermission.ALLOWED, licensed = true)))
    }

    @Test
    fun `인허가 확인 안 됐으면 고캠핑 업종 기준, 그 외는 확인 필요`() {
        assertEquals("자동차야영장", nearbyPermissionText(place(CampingPermission.ALLOWED, licensed = false)))
        assertEquals("차박 가능 여부 확인 필요", nearbyPermissionText(place(CampingPermission.UNKNOWN, licensed = false)))
    }

    @Test
    fun `출처에 두 데이터가 모두 있고 보장 표현이 없다`() {
        assert(NEARBY_SOURCE_NOTE.contains("한국관광공사 고캠핑"))
        assert(NEARBY_SOURCE_NOTE.contains("행정안전부 지방행정 인허가 데이터"))
        for (banned in listOf("안전합니다", "차박해도 됩니다", "보장")) {
            assertFalse(banned, NEARBY_SOURCE_NOTE.contains(banned))
            assertFalse(banned, nearbyPermissionText(place(CampingPermission.ALLOWED, licensed = true)).contains(banned))
        }
    }
}
