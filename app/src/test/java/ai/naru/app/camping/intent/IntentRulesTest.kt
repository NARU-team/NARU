package ai.naru.app.camping.intent

import ai.naru.app.camping.CampingStage
import ai.naru.app.camping.arrival.PlaceType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class IntentRulesTest {

    private val bedtime = IntentContext(
        stage = CampingStage.BEDTIME_PREP,
        targetTempC = 22f,
        vehicleTempRange = 15f..30f,
        wakeAt = LocalTime.of(7, 0),
        sunrise = LocalTime.of(7, 10),
        theme = PlaceType.FOREST,
        soundOn = true,
        voiceOn = true,
    )
    private val night = bedtime.copy(stage = CampingStage.NIGHT)

    @Test
    fun `온도 올리기는 지금 값 기준, 0점5도 단위로 맞춘다`() {
        val changes = IntentRules.resolve(listOf(IntentProposal(IntentAction.ADJUST_TEMPERATURE, deltaC = 1.2f)), bedtime)
        assertEquals(listOf(ProposedChange.Temperature(22f, 23f)), changes)
    }

    @Test
    fun `차량 범위를 넘으면 범위 끝으로 자른다`() {
        val changes = IntentRules.resolve(listOf(IntentProposal(IntentAction.SET_TEMPERATURE, celsius = 34f)), bedtime)
        assertEquals(listOf(ProposedChange.Temperature(22f, 30f)), changes)
    }

    @Test
    fun `지금과 같은 값은 제안하지 않는다`() {
        val same = listOf(
            IntentProposal(IntentAction.SET_TEMPERATURE, celsius = 22f),
            IntentProposal(IntentAction.SET_THEME, theme = PlaceType.FOREST),
            IntentProposal(IntentAction.SET_SOUND, on = true),
        )
        assertEquals(emptyList<ProposedChange>(), IntentRules.resolve(same, bedtime.copy(stage = CampingStage.BEDTIME_PREP)))
    }

    @Test
    fun `단계에서 허용되지 않은 동작은 버린다`() {
        // 밤에는 기상 시각·테마를 바꾸지 않는다
        val proposals = listOf(
            IntentProposal(IntentAction.SET_WAKE_TIME, time = LocalTime.of(7, 30)),
            IntentProposal(IntentAction.SET_THEME, theme = PlaceType.BEACH),
            IntentProposal(IntentAction.SET_SOUND, on = false),
        )
        assertEquals(listOf(ProposedChange.Sound(false)), IntentRules.resolve(proposals, night))
        assertEquals(emptyList<ProposedChange>(), IntentRules.resolve(proposals, bedtime.copy(stage = CampingStage.MORNING)))
    }

    @Test
    fun `일출에 깨우기와 기상 시각`() {
        assertEquals(
            listOf(ProposedChange.WakeTime(LocalTime.of(7, 0), LocalTime.of(7, 10), sunrise = true)),
            IntentRules.resolve(listOf(IntentProposal(IntentAction.WAKE_AT_SUNRISE)), bedtime),
        )
        assertEquals(
            listOf(ProposedChange.WakeTime(LocalTime.of(7, 0), LocalTime.of(7, 30), sunrise = false)),
            IntentRules.resolve(listOf(IntentProposal(IntentAction.SET_WAKE_TIME, time = LocalTime.of(7, 30))), bedtime),
        )
    }

    @Test
    fun `같은 종류가 여러 개면 마지막 것을 쓴다`() {
        val proposals = listOf(
            IntentProposal(IntentAction.SET_TEMPERATURE, celsius = 23f),
            IntentProposal(IntentAction.SET_TEMPERATURE, celsius = 24f),
        )
        assertEquals(listOf(ProposedChange.Temperature(22f, 24f)), IntentRules.resolve(proposals, bedtime))
    }

    @Test
    fun `값이 빠진 제안은 버린다`() {
        assertEquals(emptyList<ProposedChange>(), IntentRules.resolve(listOf(IntentProposal(IntentAction.SET_TEMPERATURE)), bedtime))
    }

    @Test
    fun `서버 요청과 응답 JSON`() {
        val request = JSONObject(IntentJson.request("아니 1도 더", listOf(Turn(TurnRole.USER, "따뜻하게"), Turn(TurnRole.ASSISTANT, "23도로 바꿀까요?")), bedtime))
        assertEquals("BEDTIME_PREP", request.getJSONObject("context").getString("stage"))
        assertEquals("07:10", request.getJSONObject("context").getString("sunrise"))
        assertEquals("assistant", request.getJSONArray("history").getJSONObject(1).getString("role"))

        val reply = IntentJson.reply(
            """{"reply":"24도로 바꿀까요?","needsClarification":false,"proposals":[
                {"type":"SET_TEMPERATURE","celsius":24,"deltaC":null,"time":null,"theme":null,"on":null},
                {"type":"FLY","celsius":null,"deltaC":null,"time":null,"theme":null,"on":null},
                {"type":"SET_WAKE_TIME","celsius":null,"deltaC":null,"time":"07:30","theme":null,"on":null}]}""",
        )
        assertEquals("24도로 바꿀까요?", reply.reply)
        assertEquals(listOf(IntentAction.SET_TEMPERATURE, IntentAction.SET_WAKE_TIME), reply.proposals.map { it.action })
        assertEquals(24f, reply.proposals[0].celsius)
        assertEquals(LocalTime.of(7, 30), reply.proposals[1].time)
    }
}
