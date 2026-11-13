package ai.naru.app.camping

import ai.naru.app.camping.arrival.DemoSiteRepository
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.morning.MorningAlarm
import ai.naru.app.camping.morning.RestoreReport
import ai.naru.app.camping.intent.IntentAction
import ai.naru.app.camping.intent.IntentContext
import ai.naru.app.camping.intent.IntentProposal
import ai.naru.app.camping.intent.IntentReply
import ai.naru.app.camping.intent.IntentRepository
import ai.naru.app.camping.intent.ProposedChange
import ai.naru.app.camping.intent.SpeechEvent
import ai.naru.app.camping.intent.SpeechInput
import ai.naru.app.camping.intent.Turn
import ai.naru.app.camping.intent.TurnRole
import java.time.LocalTime
import ai.naru.app.camping.narration.NarrationMoment
import ai.naru.app.camping.narration.NarrationRepository
import ai.naru.app.camping.narration.VoiceGuide
import ai.naru.app.camping.night.NightSound
import ai.naru.app.camping.record.InMemorySessionRecordStore
import java.time.LocalDateTime
import ai.naru.app.vehicle.AmbientLight
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * 세션 흐름 통합 테스트. Fake 차량은 바로 응답하고, Unconfined 디스패처라 호출 즉시 끝까지 실행된다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CampingViewModelTest {

    private val originalAmbient = AmbientLight(on = true, brightness = 40, colorRgb = 0xFFFFFF)
    private val fake = FakeVehicleGateway(targetTemperature = 22f, ambientLight = originalAmbient)
    private val sound = RecordingSound()
    private val alarm = RecordingAlarm()
    private val narration = FakeNarration()
    private val voice = RecordingVoice()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val records = InMemorySessionRecordStore()
    private val intents = FakeIntents()
    private val speech = FakeSpeech()
    private var now = LocalDateTime.of(2026, 11, 13, 22, 0)

    private fun viewModel(vehicle: VehicleGateway = fake) =
        CampingViewModel(vehicle, DemoSiteRepository(), sound, alarm, narration, voice, records, intents, speech, clock = { now })

    /** 숲 캠핑장에서 24°C로 밤을 시작하기 직전까지 진행한다. */
    private fun CampingViewModel.goToBedtime() {
        start()
        selectSite(state.value.sites.first { it.placeType == PlaceType.FOREST })
        confirmArrival()
        adjustTemperature(4) // 22 → 24°C (0.5 단위)
    }

    private fun vehicleState() = runBlocking { fake.getTargetTemperature() to fake.getAmbientLight() }

    @Test
    fun `밤에 기어가 P를 벗어나면 소리를 멈추고 설정을 복구한 뒤 끝낸다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        assertEquals(CampingStage.NIGHT, vm.state.value.stage)
        assertEquals(VehicleResult.Success(24f), vehicleState().first)

        fake.setGear(Gear.DRIVE)

        val state = vm.state.value
        assertEquals(CampingStage.IDLE, state.stage)
        assertEquals(EndReason.GEAR_LEFT_PARK, state.lastEndReason)
        assertEquals(RestoreReport(ControlResult.Applied, ControlResult.Applied), state.restore)
        assertEquals(VehicleResult.Success(22f) to VehicleResult.Success(originalAmbient), vehicleState())
        assertFalse(sound.playing)
    }

    @Test
    fun `아침에 기어가 P를 벗어나면 알람을 멈추고 설정을 복구한 뒤 끝낸다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        vm.simulateWakeTime()
        assertEquals(CampingStage.MORNING, vm.state.value.stage)
        assertEquals(true, alarm.ringing)

        fake.setGear(Gear.DRIVE)

        assertEquals(CampingStage.IDLE, vm.state.value.stage)
        assertEquals(EndReason.GEAR_LEFT_PARK, vm.state.value.lastEndReason)
        assertEquals(VehicleResult.Success(22f) to VehicleResult.Success(originalAmbient), vehicleState())
        assertFalse(alarm.ringing)
    }

    @Test
    fun `아침에 일어나 복구하고 마치면 정상 종료된다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        vm.simulateWakeTime()

        vm.finishSession() // 복구 전에는 마칠 수 없다
        assertEquals(CampingStage.MORNING, vm.state.value.stage)

        vm.wakeUp()
        vm.finishSession()

        assertEquals(CampingStage.IDLE, vm.state.value.stage)
        assertEquals(EndReason.COMPLETED, vm.state.value.lastEndReason)
        assertEquals(VehicleResult.Success(22f) to VehicleResult.Success(originalAmbient), vehicleState())
    }

    @Test
    fun `차량을 준비하는 사이 기어가 바뀌면 바꾼 온도를 되돌린다`() {
        // 설정 온도를 적용하자마자 기어가 D로 바뀌는 차량
        val gearFlipping = object : VehicleGateway by fake {
            override suspend fun setTargetTemperature(celsius: Float): VehicleResult<Unit> =
                fake.setTargetTemperature(celsius).also { fake.setGear(Gear.DRIVE) }
        }
        val vm = viewModel(gearFlipping)
        vm.goToBedtime()

        vm.startNight()

        assertEquals(CampingStage.IDLE, vm.state.value.stage)
        assertEquals(EndReason.GEAR_LEFT_PARK, vm.state.value.lastEndReason)
        assertEquals(VehicleResult.Success(22f), vehicleState().first)
        assertFalse(sound.playing)
    }

    @Test
    fun `도착 판정 뒤 AI 멘트를 보여주고 읽는다`() {
        narration.reply = "숲 캠핑장은 정보 확인 단계예요."
        val vm = viewModel()
        vm.start()
        vm.selectSite(vm.state.value.sites.first { it.placeType == PlaceType.FOREST })

        val shown = vm.state.value.narration!!
        assertEquals(NarrationMoment.ARRIVAL, shown.moment)
        assertEquals("숲 캠핑장은 정보 확인 단계예요.", shown.text)
        assertEquals(true, shown.fromAi)
        assertEquals(listOf("숲 캠핑장은 정보 확인 단계예요."), voice.spoken)
        // LLM에는 규칙 엔진이 정한 등급이 사실로 넘어간다
        assertEquals("정보 확인", narration.lastFacts?.get("안내등급"))
    }

    @Test
    fun `AI 멘트가 실패하면 준비된 문장을 읽는다`() {
        narration.reply = null
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()

        val shown = vm.state.value.narration!!
        assertEquals(NarrationMoment.BEDTIME, shown.moment)
        assertEquals(false, shown.fromAi)
        assertEquals(true, shown.text.contains("24도"))
        assertEquals(listOf(shown.text), voice.spoken.takeLast(1))
    }

    @Test
    fun `음성 안내를 끄면 읽지 않는다`() {
        val vm = viewModel()
        vm.toggleVoice()
        vm.goToBedtime()
        vm.startNight()
        assertEquals(emptyList<String>(), voice.spoken.filter { it == vm.state.value.narration?.text })
    }

    @Test
    fun `일어나면 아침 브리핑 멘트를 읽는다`() {
        narration.reply = null
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        vm.simulateWakeTime()
        vm.wakeUp()

        val shown = vm.state.value.narration!!
        assertEquals(NarrationMoment.MORNING, shown.moment)
        assertEquals(true, shown.text.contains("바깥은"))
    }

    @Test
    fun `밤을 지나 세션이 끝나면 기록을 남긴다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        now = now.plusHours(9)
        fake.setBatteryPercent(70)
        vm.simulateWakeTime()
        vm.wakeUp()
        vm.finishSession()

        val record = vm.state.value.records.single()
        assertEquals(80, record.nightStartBattery)
        assertEquals(70, record.nightEndBattery)
        assertEquals(9.0, record.nightHours, 0.01)
        assertEquals(24f, record.targetTempC)
        assertEquals(EndReason.WAKE_TIME, record.endReason)
        assertEquals(record, runBlocking { records.load() }.single())
    }

    @Test
    fun `밤 전에 끝난 세션은 기록하지 않는다`() {
        val vm = viewModel()
        vm.goToBedtime()
        fake.setGear(Gear.DRIVE)
        assertEquals(emptyList<Any>(), vm.state.value.records)
    }

    @Test
    fun `밤에 기어가 바뀌어 끝나도 기록하고 이유를 남긴다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        now = now.plusHours(3)
        fake.setGear(Gear.DRIVE)
        assertEquals(EndReason.GEAR_LEFT_PARK, vm.state.value.records.single().endReason)
    }

    @Test
    fun `비슷한 기록이 쌓이면 취침 준비에서 예상 잔량을 보여준다`() {
        val vm = viewModel()
        vm.goToBedtime()
        assertEquals(null, vm.state.value.bedtime?.forecast)

        vm.addSampleRecords()

        val forecast = vm.state.value.bedtime?.forecast
        assertEquals(4, forecast?.basedOn)
        assertEquals(true, forecast!!.lowPercent <= forecast.highPercent)
        vm.clearRecords()
        assertEquals(null, vm.state.value.bedtime?.forecast)
    }

    // --- 발화 해석 ---

    @Test
    fun `제안은 승인 전에는 아무것도 바꾸지 않고, 바꾸기를 누르면 적용한다`() {
        intents.replies += IntentReply("23도로 바꿀까요?", false, listOf(IntentProposal(IntentAction.ADJUST_TEMPERATURE, deltaC = 1f)))
        val vm = viewModel()
        vm.goToBedtime() // 24°C
        vm.ask("조금 더 따뜻하게")

        assertEquals(listOf(ProposedChange.Temperature(24f, 25f)), vm.state.value.assistant.pending)
        assertEquals(24f, vm.state.value.bedtime?.settings?.targetTempC)

        vm.approveChanges()
        assertEquals(25f, vm.state.value.bedtime?.settings?.targetTempC)
        assertEquals("바꿨어요.", vm.state.value.assistant.turns.last().text)
    }

    @Test
    fun `취소하면 그대로 둔다`() {
        intents.replies += IntentReply("숲 테마로 바꿀까요?", false, listOf(IntentProposal(IntentAction.SET_THEME, theme = PlaceType.LAKE)))
        val vm = viewModel()
        vm.goToBedtime()
        vm.ask("호수로")
        vm.rejectChanges()
        assertEquals(PlaceType.FOREST, vm.state.value.bedtime?.settings?.theme)
        assertEquals(emptyList<ProposedChange>(), vm.state.value.assistant.pending)
    }

    @Test
    fun `멀티턴 - 앞선 대화를 함께 보내고, 단계가 바뀌면 지운다`() {
        intents.replies += IntentReply("몇 시에 깨워 드릴까요?", true, emptyList())
        intents.replies += IntentReply("7시 반으로 바꿀까요?", false, listOf(IntentProposal(IntentAction.SET_WAKE_TIME, time = LocalTime.of(7, 30))))
        val vm = viewModel()
        vm.goToBedtime()
        vm.ask("깨워줘")
        vm.ask("7시 반")

        assertEquals(listOf(Turn(TurnRole.USER, "깨워줘"), Turn(TurnRole.ASSISTANT, "몇 시에 깨워 드릴까요?")), intents.histories.last())
        assertEquals(listOf(ProposedChange.WakeTime(LocalTime.of(7, 0), LocalTime.of(7, 30), sunrise = false)), vm.state.value.assistant.pending)

        vm.startNight()
        assertEquals(emptyList<Turn>(), vm.state.value.assistant.turns)
    }

    @Test
    fun `밤에 온도를 승인하면 차량에 적용하고 확인한다`() {
        intents.replies += IntentReply("25도로 바꿀까요?", false, listOf(IntentProposal(IntentAction.SET_TEMPERATURE, celsius = 25f)))
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        vm.ask("좀 추워")
        vm.approveChanges()

        assertEquals(VehicleResult.Success(25f), runBlocking { fake.getTargetTemperature() })
        assertEquals(ai.naru.app.camping.bedtime.ControlResult.Applied, vm.state.value.temperatureApply)
    }

    @Test
    fun `제안이 규칙 검사에서 모두 걸러지면 바꿀까요라고 묻지 않는다`() {
        intents.replies += IntentReply("24도로 바꿀까요?", false, listOf(IntentProposal(IntentAction.SET_TEMPERATURE, celsius = 24f)))
        val vm = viewModel()
        vm.goToBedtime() // 이미 24°C
        vm.ask("24도로 해줘")
        assertEquals(emptyList<ProposedChange>(), vm.state.value.assistant.pending)
        assertEquals("지금 설정과 같거나 바꿀 수 없는 값이라 그대로 둘게요.", vm.state.value.assistant.turns.last().text)
    }

    @Test
    fun `해석에 실패하면 이해하지 못했다고 안내한다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.ask("블라블라")
        assertEquals(true, vm.state.value.assistant.notice?.contains("이해하지 못했어요"))
    }

    @Test
    fun `음성으로 들은 문장을 그대로 해석한다`() {
        intents.replies += IntentReply("23도로 바꿀까요?", false, listOf(IntentProposal(IntentAction.SET_TEMPERATURE, celsius = 23f)))
        speech.result = SpeechEvent.Final("조금 시원하게")
        val vm = viewModel()
        vm.goToBedtime()
        vm.toggleListening()

        assertEquals(Turn(TurnRole.USER, "조금 시원하게"), vm.state.value.assistant.turns.first())
        assertEquals(false, vm.state.value.assistant.listening)
    }

    @Test
    fun `음성 입력을 못 쓰면 글로 입력하라고 안내한다`() {
        speech.result = SpeechEvent.Failed("MIC_ERROR")
        val vm = viewModel()
        vm.goToBedtime()
        vm.toggleListening()
        assertEquals(true, vm.state.value.assistant.notice?.contains("글로 입력"))
    }

    private class FakeIntents : IntentRepository {
        override val available = true
        val replies = ArrayDeque<IntentReply>()
        val histories = mutableListOf<List<Turn>>()
        override suspend fun interpret(utterance: String, history: List<Turn>, context: IntentContext): IntentReply? {
            histories += history
            return replies.removeFirstOrNull()
        }
    }

    private class FakeSpeech : SpeechInput {
        var result: SpeechEvent = SpeechEvent.Ended
        override fun listen(onEvent: (SpeechEvent) -> Unit) = onEvent(result)
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class FakeNarration : NarrationRepository {
        var reply: String? = null
        var lastFacts: Map<String, Any>? = null
        override suspend fun narrate(moment: NarrationMoment, facts: Map<String, Any>): String? {
            lastFacts = facts
            return reply
        }
    }

    private class RecordingVoice : VoiceGuide {
        val spoken = mutableListOf<String>()
        override fun speak(text: String) { spoken += text }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class RecordingSound : NightSound {
        var playing = false
        override fun play(theme: PlaceType): Boolean { playing = true; return true }
        override fun stop() { playing = false }
    }

    private class RecordingAlarm : MorningAlarm {
        var ringing = false
        override fun start(): Boolean { ringing = true; return true }
        override fun stop() { ringing = false }
    }
}
