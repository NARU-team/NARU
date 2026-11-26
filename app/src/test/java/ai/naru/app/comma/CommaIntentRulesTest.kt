package ai.naru.app.comma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommaIntentRulesTest {
    @Test
    fun `요청 예문을 구조화된 제안으로만 변환한다`() {
        assertEquals(CommaProposedChange.Duration(20),
            CommaIntentRules.interpretLocally("20분만 쉬고 싶어", RestPhase.READY)?.change)
        assertEquals(CommaProposedChange.TemperatureDelta(1f),
            CommaIntentRules.interpretLocally("조금 더 따뜻하게 해 줘", RestPhase.RESTING)?.change)
        assertEquals(CommaProposedChange.TemperatureAbsolute(25f),
            CommaIntentRules.interpretLocally("25도로 맞춰 줘", RestPhase.READY)?.change)
        assertEquals(CommaProposedChange.Sound(RestSound.RAIN),
            CommaIntentRules.interpretLocally("빗소리로 바꿔 줘", RestPhase.READY)?.change)
        assertEquals(CommaProposedChange.Sound(RestSound.SILENT),
            CommaIntentRules.interpretLocally("소리 없이 쉬고 싶어", RestPhase.READY)?.change)
        assertEquals(CommaProposedChange.Brightness(15),
            CommaIntentRules.interpretLocally("화면을 어둡게 해 줘", RestPhase.READY)?.change)
        assertEquals(CommaProposedChange.Extension(5),
            CommaIntentRules.interpretLocally("5분만 더 쉴래", RestPhase.WAKING)?.change)
    }

    @Test
    fun `복합 요청은 한 승인 카드의 여러 변경으로 만든다`() {
        val proposal = CommaIntentRules.interpretLocally(
            "20분 동안 따뜻하게 빗소리 들으면서 쉴래",
            RestPhase.READY,
        )!!
        assertEquals(
            listOf(
                CommaProposedChange.Duration(20),
                CommaProposedChange.TemperatureDelta(1f),
                CommaProposedChange.Sound(RestSound.RAIN),
            ),
            proposal.changes,
        )
        assertEquals(CommaProposalSource.LOCAL_RULE, proposal.source)
    }

    @Test
    fun `현재 단계와 capability에서 허용되지 않은 제안은 거부한다`() {
        assertNull(CommaIntentRules.interpretLocally("20분만 쉬고 싶어", RestPhase.RESTING))
        assertNull(CommaIntentRules.interpretLocally("5분만 더 쉴래", RestPhase.READY))
        val unsupported = VehicleCapabilities()
        assertTrue(!CommaIntentRules.validate(
            listOf(CommaProposedChange.TemperatureAbsolute(25f)),
            RestPhase.READY,
            unsupported,
        ))
    }

    @Test
    fun `서버 JSON은 최소 컨텍스트와 여러 제안을 보존한다`() {
        val context = CommaIntentContext(
            phase = RestPhase.READY,
            sessionId = null,
            selectedMinutes = 15,
            remainingMinutes = null,
            currentTempC = 23f,
            recommendedTempC = 24f,
            temperatureRange = 16f..30f,
            climateSupported = true,
            ambientLightSupported = true,
            sound = RestSound.SILENT,
            screenBrightnessPercent = 30,
            recentPreferenceSummary = null,
        )
        val request = CommaIntentJson.request("request-1", "20분 쉬고 싶어", context)
        assertTrue(request.contains("request-1"))
        assertTrue(request.contains("READY"))
        val parsed = CommaIntentJson.parse(
            """{"requestId":"request-1","reply":"바꿀까요?","needsClarification":false,"proposals":[{"type":"SET_DURATION","minutes":20,"celsius":null,"deltaC":null,"sound":null,"percent":null},{"type":"SET_REST_SOUND","minutes":null,"celsius":null,"deltaC":null,"sound":"RAIN","percent":null}]}""",
        )
        assertEquals(
            listOf(CommaProposedChange.Duration(20), CommaProposedChange.Sound(RestSound.RAIN)),
            parsed.changes,
        )
    }
}
