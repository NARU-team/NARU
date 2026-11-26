package ai.naru.app.comma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class GleoCommaContractTest {
    private val capabilities = VehicleCapabilities(
        climate = ControlSupport.SUPPORTED,
        climateTemperatureRange = 16f..30f,
        lighting = ControlSupport.SUPPORTED,
    )

    @Test
    fun `Gleo 설정 요청은 승인 전 제안으로만 변환한다`() {
        val proposal = GleoCommaContract.proposal(
            GleoCommaContract.ACTION_PROPOSE_SETTINGS,
            mapOf(
                "durationMinutes" to 20,
                "targetTemperatureCelsius" to 25,
                "restSound" to "RAIN",
            ),
            RestPhase.READY,
            capabilities,
        )

        requireNotNull(proposal)
        assertEquals(CommaProposalSource.GLEO, proposal.source)
        assertEquals(3, proposal.changes.size)
        assertTrue(proposal.changes.first() is CommaProposedChange.Duration)
    }

    @Test
    fun `허용되지 않는 단계의 시간 변경은 거부한다`() {
        assertNull(
            GleoCommaContract.proposal(
                GleoCommaContract.ACTION_PROPOSE_SETTINGS,
                mapOf("durationMinutes" to 20),
                RestPhase.RESTING,
                capabilities,
            ),
        )
    }

    @Test
    fun `연장은 5분 또는 10분만 허용한다`() {
        assertNull(
            GleoCommaContract.proposal(
                GleoCommaContract.ACTION_EXTEND_REST,
                mapOf("minutes" to 7),
                RestPhase.WAKING,
                capabilities,
            ),
        )
        val valid = GleoCommaContract.proposal(
            GleoCommaContract.ACTION_EXTEND_REST,
            mapOf("minutes" to 10),
            RestPhase.WAKING,
            capabilities,
        )
        requireNotNull(valid)
        assertTrue(valid.change is CommaProposedChange.Extension)
    }

    @Test
    fun `사용자 승인 대기는 공식 asking callback으로 직렬화한다`() {
        val json = JSONObject(
            GleoCallback(
                functionName = GleoCommaContract.ACTION_PROPOSE_SETTINGS,
                status = GleoCallbackStatus.ASKING,
                message = "User confirmation is required in the app.",
            ).toJson(),
        )

        assertEquals("asking", json.getString("status"))
        assertEquals(GleoCommaContract.ACTION_PROPOSE_SETTINGS, json.getString("functionName"))
    }

    @Test
    fun `외부 Intent envelope는 허용 키와 값 범위를 검증한다`() {
        assertTrue(
            GleoCommaContract.isValidEnvelope(
                GleoCommaContract.ACTION_PROPOSE_SETTINGS,
                mapOf("requestId" to "request-1", "durationMinutes" to 20),
            ),
        )
        assertEquals(
            false,
            GleoCommaContract.isValidEnvelope(
                GleoCommaContract.ACTION_PROPOSE_SETTINGS,
                mapOf("durationMinutes" to 181),
            ),
        )
        assertEquals(
            false,
            GleoCommaContract.isValidEnvelope(
                GleoCommaContract.ACTION_PROPOSE_SETTINGS,
                mapOf("durationMinutes" to 20, "unexpected" to true),
            ),
        )
        assertEquals(
            false,
            GleoCommaContract.isValidEnvelope(
                GleoCommaContract.ACTION_EXTEND_REST,
                mapOf("minutes" to 7),
            ),
        )
    }

    @Test
    fun `차량 미지원 조명 요청은 거부한다`() {
        assertNull(
            GleoCommaContract.proposal(
                GleoCommaContract.ACTION_PROPOSE_SETTINGS,
                mapOf("ambientLightPercent" to 30),
                RestPhase.READY,
                capabilities.copy(lighting = ControlSupport.UNSUPPORTED),
            ),
        )
    }
}
