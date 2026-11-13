package ai.naru.app.camping

import ai.naru.app.camping.arrival.ArrivalAssessment
import ai.naru.app.camping.arrival.ArrivalRules
import ai.naru.app.camping.arrival.Site
import ai.naru.app.camping.arrival.SiteConditions
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.arrival.SiteRepository
import ai.naru.app.camping.bedtime.BedtimeRules
import ai.naru.app.camping.bedtime.BedtimeSettings
import ai.naru.app.camping.bedtime.NightPreparer
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.bedtime.TemperatureRecommendation
import ai.naru.app.camping.bedtime.VehicleBackup
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.camping.morning.MorningAlarm
import ai.naru.app.camping.morning.MorningBriefing
import ai.naru.app.camping.morning.MorningLightApplier
import ai.naru.app.camping.morning.MorningRules
import ai.naru.app.camping.morning.RestoreReport
import ai.naru.app.camping.morning.SettingsRestorer
import ai.naru.app.camping.intent.DisabledIntent
import ai.naru.app.camping.intent.IntentContext
import ai.naru.app.camping.intent.IntentRepository
import ai.naru.app.camping.intent.IntentRules
import ai.naru.app.camping.intent.ProposedChange
import ai.naru.app.camping.intent.SpeechEvent
import ai.naru.app.camping.intent.SpeechInput
import ai.naru.app.camping.intent.Turn
import ai.naru.app.camping.intent.TurnRole
import ai.naru.app.camping.narration.NarrationMoment
import ai.naru.app.camping.narration.NarrationRepository
import ai.naru.app.camping.narration.NarrationRequest
import ai.naru.app.camping.narration.NarrationScripts
import ai.naru.app.camping.narration.VoiceGuide
import ai.naru.app.camping.night.NightRules
import ai.naru.app.camping.night.NightTemps
import ai.naru.app.camping.night.TempSample
import ai.naru.app.camping.bedtime.FeedbackHint
import ai.naru.app.camping.bedtime.FeedbackRules
import ai.naru.app.camping.bedtime.TempFeedback
import ai.naru.app.camping.record.BatteryForecast
import ai.naru.app.camping.record.InMemorySessionRecordStore
import ai.naru.app.camping.record.MorningForecast
import ai.naru.app.camping.record.SampleRecords
import ai.naru.app.camping.record.SessionRecord
import ai.naru.app.camping.record.SessionRecordStore
import java.util.UUID
import ai.naru.app.camping.night.NightSound
import ai.naru.app.camping.night.ThemeLightApplier
import java.time.LocalDateTime
import java.time.LocalTime
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult
import android.util.Log
import ai.naru.app.camping.arrival.CampingPermission
import ai.naru.app.camping.arrival.NearbyPlace
import ai.naru.app.location.LatLng
import ai.naru.app.location.FakeLocationGateway
import ai.naru.app.location.LocationGateway
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 차박 화면이 그릴 내용 전부. 화면은 이 값만 보고 그린다. */
data class CampingUiState(
    val stage: CampingStage = CampingStage.IDLE,
    val gear: Gear = Gear.UNKNOWN,
    val batteryPercent: Int? = null,
    /** 홈 화면 칩: 실내·바깥 온도. 모르면 null (칩 숨김) */
    val cabinTempC: Float? = null,
    val outsideTempC: Float? = null,
    /** 마지막으로 아침으로 넘어가거나 세션이 끝난 이유 */
    val lastEndReason: EndReason? = null,
    /** 사용자에게 보여줄 안내 (예: P가 아니라 시작 불가) */
    val message: String? = null,
    /** 도착 단계: 고를 수 있는 장소 */
    val sites: List<Site> = emptyList(),
    /** 도착 단계: 장소 목록을 불러오지 못함 (서버 연결 실패) */
    val sitesUnavailable: Boolean = false,
    /** 도착 단계: 고른 장소의 오늘 밤 정보를 불러오는 중 */
    val loadingSite: Site? = null,
    /** 도착 단계: 차량 위치로 장소를 찾는 중 (이슈 #44) */
    val locating: Boolean = false,
    /** 도착 단계: 시연 지역이 아닌 현재 위치의 오늘 밤 정보. 장소 유형을 고르면 판정한다 */
    val hereConditions: SiteConditions? = null,
    /** 도착 단계: 현재 위치 근처의 실제 야영장 (이슈 #46). 비어 있으면 장소 유형만 묻는다 */
    val nearbyPlaces: List<NearbyPlace> = emptyList(),
    /** 도착 단계: 고른 근처 야영장의 오늘 밤 정보를 불러오는 중 */
    val loadingPlace: NearbyPlace? = null,
    /** 도착 단계: 고른 장소와 그 판정 결과 */
    val arrival: ArrivalState? = null,
    /** 취침 준비 단계: 오늘 밤 설정 */
    val bedtime: BedtimeState? = null,
    /** 세션 시작 전 원래 차량 설정. 기상 단계에서 복구에 쓴다. */
    val backup: VehicleBackup? = null,
    /** 설정 온도 적용 결과. 밤 화면에서 안내한다. */
    val temperatureApply: ControlResult? = null,
    /** 밤 단계: 테마 조명 적용 결과 */
    val lightApply: ControlResult? = null,
    /** 밤 단계: 사용자가 테마 소리를 켜 두었는지 */
    val soundOn: Boolean = true,
    /** 밤 단계: 실제로 소리가 나고 있는지 (오디오 포커스를 못 받으면 false) */
    val soundPlaying: Boolean = false,
    /** 밤 단계: 아침으로 넘어갈 일시 */
    val wakeDateTime: LocalDateTime? = null,
    /** 밤을 시작할 때의 배터리 잔량. 아침 브리핑에서 밤사이 사용량을 구한다. */
    val nightStartBattery: Int? = null,
    /** 기상 단계 상태. 새 세션을 시작하면 지운다. */
    val morning: MorningState? = null,
    /** 차량 설정 복구 중인지 (아침 "일어났어요" 또는 기어 이탈) */
    val restoring: Boolean = false,
    /** 차량 설정 복구 결과. 세션이 끝난 뒤에도 남겨 두고(대기 화면 안내), 새 세션을 시작하면 지운다. */
    val restore: RestoreReport? = null,
    /** 가장 최근 멘트 (도착·잠들기 전·기상) */
    val narration: NarrationState? = null,
    /** 음성 안내 켜기/끄기 */
    val voiceOn: Boolean = true,
    /** 지난 차박 기록 (오래된 순) */
    val records: List<SessionRecord> = emptyList(),
    /** 이번 밤을 시작한 시각 */
    val nightStartedAt: LocalDateTime? = null,
    /** 이번 밤이 끝난 시각과 그때 배터리 (아침으로 넘어가거나 밤에서 끝날 때) */
    val nightEndedAt: LocalDateTime? = null,
    val nightEndBattery: Int? = null,
    /** 밤이 끝난 이유 (기상 시각·배터리 하한·기어 이탈). 기록에 남긴다 */
    val morningReason: EndReason? = null,
    /** 발화 해석 (취침 준비·밤 단계) */
    val assistant: AssistantState = AssistantState(),
    /** 취침 준비 단계 안의 화면 */
    val bedtimeStep: BedtimeStep = BedtimeStep.SETTINGS,
    /** 밤사이 실내·바깥 온도 기록 (10분 간격, 아침 브리핑 그래프) */
    val nightTemps: List<TempSample> = emptyList(),
    /** 아침 브리핑에서 고른 온도 느낌 (기록에 저장, 다음 추천에 반영) */
    val feedback: TempFeedback? = null,
)

/**
 * 발화 해석 대화.
 * @property turns 같은 단계 안의 대화 (멀티턴). 단계가 바뀌면 지운다
 * @property pending 승인을 기다리는 변경. [바꾸기]를 눌러야 규칙 코드로 실행된다
 */
data class AssistantState(
    val available: Boolean = false,
    val turns: List<Turn> = emptyList(),
    val pending: List<ProposedChange> = emptyList(),
    val busy: Boolean = false,
    val listening: Boolean = false,
    /** 음성 인식 중간 결과 */
    val partial: String? = null,
    /** 이해하지 못했거나 음성 입력을 쓸 수 없을 때 안내 */
    val notice: String? = null,
)

/** 멘트 하나. AI 멘트를 기다리는 동안에는 준비된 문장을 먼저 보여준다. */
data class NarrationState(
    val moment: NarrationMoment,
    val text: String,
    /** AI가 만든 문장인지 (false면 준비된 문장) */
    val fromAi: Boolean,
    /** AI 멘트를 기다리는 중 */
    val pending: Boolean,
)

data class MorningState(
    /** 알람이 울리는 중인지 */
    val alarmRinging: Boolean = false,
    /** 알람을 울리지 못했는지 (포커스·알람음 없음). 화면 안내용 */
    val alarmUnavailable: Boolean = false,
    /** 아침 조명 결과. 적용 전이면 null */
    val lightApply: ControlResult? = null,
    /** 아침 브리핑. 만들기 전이면 null */
    val briefing: MorningBriefing? = null,
)

data class BedtimeState(
    val settings: BedtimeSettings,
    val recommendation: TemperatureRecommendation,
    val sunrise: LocalTime?,
    val vehicleTempRange: ClosedFloatingPointRange<Float>?,
    /** 차박 시작하기를 누른 뒤 차량 준비 중 */
    val preparing: Boolean = false,
    /** 아침 예상 잔량. 비슷한 기록이 부족하면 null */
    val forecast: MorningForecast? = null,
    /** 온도별 아침 예상 배터리 (고른 온도 −1/0/+1). 기록이 부족한 온도는 forecast가 null */
    val forecastRows: List<TempForecast> = emptyList(),
    /** 추천에 반영한 지난 비슷한 밤의 피드백 ("지난 차박 22°C는 더웠음") */
    val feedbackHint: FeedbackHint? = null,
)

data class TempForecast(val celsius: Float, val forecast: MorningForecast?)

/** 취침 준비 단계 안의 화면: 취침 준비 2/3 → 밤 분위기 3/3 (피그마 v2, 이슈 #37) */
enum class BedtimeStep { SETTINGS, THEME }

data class ArrivalState(
    val site: Site,
    val conditions: SiteConditions,
    val assessment: ArrivalAssessment,
    /** 조회 시각 ("16:20에 조회했어요") */
    val fetchedAt: LocalDateTime? = null,
    /** 조회 실패 후 다시 시도를 썼는지 (한 번만) */
    val retried: Boolean = false,
)

/**
 * 차박 세션을 진행하는 ViewModel.
 *
 * 차량 게이트웨이에서 기어·배터리를 구독하고, 사용자 동작과 차량 변화를 사건으로 바꿔
 * [CampingSessionRules]에 넘긴다. 단계 판단은 규칙이 하고, 여기서는 결과를 상태에 반영만 한다.
 */
class CampingViewModel(
    private val vehicle: VehicleGateway,
    private val siteRepository: SiteRepository,
    private val sound: NightSound,
    private val alarm: MorningAlarm,
    private val narration: NarrationRepository,
    private val voice: VoiceGuide,
    private val recordStore: SessionRecordStore = InMemorySessionRecordStore(),
    private val intents: IntentRepository = DisabledIntent,
    private val speech: SpeechInput? = null,
    /** 차량 위치. 기본값은 위치 없음(시연 지역 고르기) */
    private val location: LocationGateway = FakeLocationGateway(location = null, permitted = false),
    /** 지금 시각. 테스트에서 바꿔 끼운다. */
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    private val nightPreparer = NightPreparer(vehicle)
    private val themeLightApplier = ThemeLightApplier(vehicle)
    private val morningLightApplier = MorningLightApplier(vehicle)
    private val settingsRestorer = SettingsRestorer(vehicle)

    /** 밤 단계 동안만 도는 기상 시각 확인. 밤을 벗어나거나 복구를 시작하면 취소한다. */
    private var nightJob: Job? = null

    /** 밤 테마 조명 적용. 복구는 이 작업이 끝난 뒤에 한다 (복구 후 테마 조명이 덮어쓰지 않게). */
    private var themeLightJob: Job? = null

    /** 기상 단계 준비 작업 (조명, 브리핑). 복구는 이 작업이 끝난 뒤에 한다. */
    private var morningJob: Job? = null

    /** 설정 복구 작업. 한 번만 돈다. */
    private var restoreJob: Job? = null

    /**
     * 위치로 장소 찾기. 새로 찾거나 사용자가 다른 선택(장소 유형·시연 지역·장소 바꾸기)을 하면 취소한다.
     * 늦게 도착한 이전 결과가 화면을 덮어쓰지 않게 하기 위함 (에뮬레이터에서 장소 유형을 고른 뒤 고르기 화면으로 돌아간 실측)
     */
    private var locateJob: Job? = null

    /** 멘트 요청. 새 멘트가 오면 이전 요청은 취소한다. */
    private var narrationJob: Job? = null

    /** 복구가 끝나면 이 이유로 세션을 끝낸다 (아침에 기어가 P를 벗어난 경우). */
    private var pendingEndReason: EndReason? = null

    /** 발화 해석 요청·음성 인식 제한 시간 작업 */
    private var assistantJob: Job? = null
    private var listenTimeoutJob: Job? = null

    private val _state = MutableStateFlow(CampingUiState(assistant = AssistantState(available = intents.available)))
    val state: StateFlow<CampingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vehicle.gear.collect { gear ->
                _state.update { it.copy(gear = gear) }
                dispatch(CampingEvent.GearChanged(gear))
            }
        }
        viewModelScope.launch {
            vehicle.batteryPercent.collect { percent ->
                _state.update { it.copy(batteryPercent = percent) }
                checkBatteryLimit()
            }
        }
        loadSites()
        refreshHomeStatus()
        viewModelScope.launch {
            val records = recordStore.load()
            _state.update { it.copy(records = records) }
        }
    }

    /** 홈 화면 칩용 실내·바깥 온도를 다시 읽는다 (앱 시작, 세션 종료 후 홈으로 돌아올 때). */
    fun refreshHomeStatus() {
        viewModelScope.launch {
            val cabin = (vehicle.getCabinTemperature() as? VehicleResult.Success)?.value
            val outside = (vehicle.getOutsideTemperature() as? VehicleResult.Success)?.value
            _state.update { it.copy(cabinTempC = cabin, outsideTempC = outside) }
        }
    }

    /** 장소 목록을 불러온다. 실패하면 화면에 다시 시도를 보여준다. */
    fun loadSites() {
        viewModelScope.launch {
            val sites = try {
                siteRepository.sites()
            } catch (e: Exception) {
                Log.w(TAG, "장소 목록을 불러오지 못함", e)
                null
            }
            _state.update { it.copy(sites = sites ?: it.sites, sitesUnavailable = sites == null) }
        }
    }

    // --- 사용자 동작 ---

    fun start() {
        dispatch(CampingEvent.Start(_state.value.gear))
        if (_state.value.stage == CampingStage.ARRIVAL_CHECK) locate()
    }

    /**
     * 차량 위치로 도착 장소를 찾는다 (기획서 "위치와 장소 유형… 조회", 이슈 #44).
     * - 시연 지역 근처면 그 장소로 바로 판정
     * - 아니면 현재 위치의 날씨·일출·물때를 받아 두고 장소 유형을 묻는다
     * - 권한 없음·미지원·위치 없음·서버 실패면 시연 지역 고르기로 대체
     */
    fun locate() {
        if (_state.value.stage != CampingStage.ARRIVAL_CHECK || !location.hasPermission()) return
        locateJob?.cancel()
        _state.update { it.copy(locating = true, arrival = null, hereConditions = null) }
        locateJob = viewModelScope.launch {
            val here = location.lastKnown()
            val located = here?.let { siteRepository.conditionsAt(it) }
            if (_state.value.stage != CampingStage.ARRIVAL_CHECK) return@launch
            _state.update { it.copy(locating = false) }
            when {
                located == null -> Log.w(TAG, "위치로 장소를 찾지 못해 시연 지역 고르기로 대체")
                located.site != null -> showArrival(_state.value.sites.firstOrNull { it.id == located.site.id } ?: located.site, located.conditions, retried = false)
                else -> {
                    // 시연 지역이 아니면 근처 실제 야영장을 찾는다 (없거나 실패하면 장소 유형만 묻는다)
                    val nearby = siteRepository.nearby(here).orEmpty()
                    if (_state.value.stage != CampingStage.ARRIVAL_CHECK) return@launch
                    _state.update { it.copy(hereConditions = located.conditions, nearbyPlaces = nearby) }
                }
            }
        }
    }

    /**
     * 근처 야영장을 고르면 그 좌표로 오늘 밤 정보를 조회해 판정한다.
     * 허용 여부는 자동차야영장만 등록된 장소로 보고, 장소 유형을 모르면 숲(기본 테마)으로 둔다.
     */
    fun choosePlace(place: NearbyPlace) {
        locateJob?.cancel()
        _state.update { it.copy(loadingPlace = place) }
        viewModelScope.launch {
            val located = siteRepository.conditionsAt(LatLng(place.lat, place.lon))
            if (_state.value.stage != CampingStage.ARRIVAL_CHECK || _state.value.loadingPlace != place) return@launch
            _state.update { it.copy(hereConditions = null, loadingPlace = null) }
            showArrival(place.toSite(), located?.conditions ?: SiteConditions(weather = null, highTides = null), retried = false)
        }
    }

    private fun NearbyPlace.toSite() = Site(
        id = CAMP_SITE_PREFIX + id,
        name = name,
        placeType = placeType ?: PlaceType.FOREST,
        permission = permission,
        facilities = facilities,
    )

    /** 현재 위치의 장소 유형 ("어떤 곳인가요?"). 허용 여부·시설 데이터가 없어 확인 필요로 판정한다 */
    fun choosePlaceType(type: PlaceType) {
        val conditions = _state.value.hereConditions ?: return
        locateJob?.cancel()
        val site = Site(id = HERE_SITE_ID, name = "현재 위치", placeType = type, permission = CampingPermission.UNKNOWN, facilities = emptyList())
        _state.update { it.copy(hereConditions = null) }
        showArrival(site, conditions, retried = false)
    }

    private fun showArrival(site: Site, conditions: SiteConditions, retried: Boolean) {
        val assessment = ArrivalRules.assess(site, conditions)
        _state.update { it.copy(arrival = ArrivalState(site, conditions, assessment, fetchedAt = clock(), retried = retried), loadingSite = null) }
        narrate(NarrationScripts.arrival(site, conditions, assessment))
    }

    /** 밤 전(도착·취침 준비)에 그만두고 홈으로. 밤부터는 처리하지 않는다 (차박 종료는 복구가 필요). */
    fun cancel() = dispatch(CampingEvent.Cancel)

    /** 밤에 [차박 종료] 확인: 소리·알람을 끄고 설정을 복구한 뒤 홈으로 (이슈 #39) */
    fun endNow() = dispatch(CampingEvent.EndNow)

    /** 도착 단계에서 장소를 고르면 오늘 밤 정보를 불러와 규칙으로 판정한다. */
    fun selectSite(site: Site) {
        locateJob?.cancel()
        _state.update { it.copy(locating = false, hereConditions = null) }
        loadConditions(site, retried = false)
    }

    /** 조회 실패일 때 한 번만 다시 불러온다 (피그마: "다시 시도는 한 번만 해요") */
    fun retryConditions() {
        val arrival = _state.value.arrival ?: return
        if (arrival.retried) return
        val campPlace = _state.value.nearbyPlaces.firstOrNull { CAMP_SITE_PREFIX + it.id == arrival.site.id }
        if (arrival.site.id == HERE_SITE_ID || campPlace != null) {
            // 현재 위치·근처 야영장은 좌표로 다시 조회한다 (장소 유형은 그대로)
            viewModelScope.launch {
                val where = campPlace?.let { LatLng(it.lat, it.lon) } ?: location.lastKnown()
                val located = where?.let { siteRepository.conditionsAt(it) }
                if (_state.value.stage != CampingStage.ARRIVAL_CHECK) return@launch
                showArrival(arrival.site, located?.conditions ?: arrival.conditions, retried = true)
            }
            return
        }
        loadConditions(arrival.site, retried = true)
    }

    /** [장소 바꾸기]: 장소 고르기로 돌아간다 */
    fun changeSite() {
        if (_state.value.stage != CampingStage.ARRIVAL_CHECK) return
        stopNarration()
        locateJob?.cancel()
        _state.update { it.copy(arrival = null, loadingSite = null, hereConditions = null, locating = false, nearbyPlaces = emptyList(), loadingPlace = null) }
    }

    private fun loadConditions(site: Site, retried: Boolean) {
        _state.update { it.copy(loadingSite = site) }
        viewModelScope.launch {
            val conditions = siteRepository.conditions(site)
            if (_state.value.stage != CampingStage.ARRIVAL_CHECK || _state.value.loadingSite != site) return@launch
            showArrival(site, conditions, retried)
        }
    }

    /** 판정 결과를 확인한 뒤에만 다음 단계로 넘어간다. 넘어가면 오늘 밤 설정의 추천값을 만든다. */
    fun confirmArrival() {
        val arrival = _state.value.arrival ?: return
        dispatch(CampingEvent.ConfirmArrival)
        if (_state.value.stage != CampingStage.BEDTIME_PREP) return
        viewModelScope.launch {
            val range = vehicle.capabilities().targetTemperatureRange
            val minTemp = arrival.conditions.weather?.minTempC
            val hint = FeedbackRules.hint(_state.value.records, minTemp)
            val recommendation = BedtimeRules.recommendTemperature(
                minTempC = minTemp,
                batteryPercent = _state.value.batteryPercent,
                vehicleRange = range,
                feedback = hint,
            )
            val settings = BedtimeSettings(
                targetTempC = recommendation.celsius,
                wakeAt = DEFAULT_WAKE_AT,
                wakeBasis = WakeBasis.MANUAL,
                theme = arrival.site.placeType,
            )
            _state.update {
                it.copy(bedtime = withForecast(it, BedtimeState(settings, recommendation, arrival.conditions.sunrise, range, feedbackHint = hint)))
            }
        }
    }

    // --- 취침 준비 단계 ---

    fun adjustTemperature(steps: Int) = updateBedtime { bedtime ->
        val next = BedtimeRules.adjustTemperature(bedtime.settings.targetTempC, steps, bedtime.vehicleTempRange)
        bedtime.copy(settings = bedtime.settings.copy(targetTempC = next))
    }

    fun adjustWakeTime(minutes: Long) = updateBedtime { bedtime ->
        val next = bedtime.settings.wakeAt.plusMinutes(minutes)
        bedtime.copy(settings = bedtime.settings.copy(wakeAt = next, wakeBasis = WakeBasis.MANUAL))
    }

    fun wakeAtSunrise() = setSunriseWake(true)

    /** 온도 슬라이더 */
    fun setTemperature(celsius: Float) = updateBedtime { bedtime ->
        bedtime.copy(settings = bedtime.settings.copy(targetTempC = BedtimeRules.setTemperature(celsius, bedtime.vehicleTempRange)))
    }

    /** [일출에 맞추기] 토글: 켜면 일출 12분 전, 끄면 지금 시각을 직접 설정으로 둔다 */
    fun setSunriseWake(on: Boolean) = updateBedtime { bedtime ->
        val sunrise = bedtime.sunrise
        if (on && sunrise != null) {
            bedtime.copy(settings = bedtime.settings.copy(wakeAt = BedtimeRules.sunriseWakeTime(sunrise), wakeBasis = WakeBasis.SUNRISE))
        } else {
            bedtime.copy(settings = bedtime.settings.copy(wakeBasis = WakeBasis.MANUAL))
        }
    }

    /** [직접 설정] 시각 고르기 팝업 */
    fun setWakeTime(time: LocalTime) = updateBedtime { bedtime ->
        bedtime.copy(settings = bedtime.settings.copy(wakeAt = time, wakeBasis = WakeBasis.MANUAL))
    }

    /** [밤 분위기 고르기] → 밤 분위기 3/3 */
    fun goToThemeStep() {
        if (_state.value.stage == CampingStage.BEDTIME_PREP && _state.value.bedtime != null) _state.update { it.copy(bedtimeStep = BedtimeStep.THEME) }
    }

    /** 단계 헤더 ← : 밤 분위기 → 취침 준비, 취침 준비 → 도착 안내 */
    fun back() {
        val state = _state.value
        when {
            state.stage == CampingStage.BEDTIME_PREP && state.bedtimeStep == BedtimeStep.THEME ->
                _state.update { it.copy(bedtimeStep = BedtimeStep.SETTINGS) }
            state.stage == CampingStage.BEDTIME_PREP -> dispatch(CampingEvent.BackToArrival)
            state.stage == CampingStage.ARRIVAL_CHECK -> cancel()
        }
    }

    fun selectTheme(theme: PlaceType) = updateBedtime { bedtime ->
        bedtime.copy(settings = bedtime.settings.copy(theme = theme))
    }

    /**
     * 사용자 승인. 원래 설정을 백업하고 설정 온도를 적용한 뒤 밤 단계로 넘어간다.
     * 제어가 실패해도 세션은 계속한다 (화면·음향·알람 중심으로 제공).
     */
    fun startNight() {
        val bedtime = _state.value.bedtime ?: return
        if (bedtime.preparing) return
        _state.update { it.copy(bedtime = bedtime.copy(preparing = true)) }
        viewModelScope.launch {
            val preparation = nightPreparer.prepare(bedtime.settings)
            if (_state.value.stage != CampingStage.BEDTIME_PREP) {
                // 차량을 준비하는 사이 세션이 끝났다 (기어 이탈). 이미 바꾼 설정을 되돌린다.
                val report = settingsRestorer.restore(preparation.backup)
                _state.update { it.copy(restore = report, bedtime = it.bedtime?.copy(preparing = false)) }
                return@launch
            }
            _state.update {
                it.copy(
                    backup = preparation.backup,
                    temperatureApply = preparation.temperature,
                    nightStartBattery = it.batteryPercent,
                    bedtime = it.bedtime?.copy(preparing = false),
                )
            }
            dispatch(CampingEvent.StartNight)
            if (_state.value.stage == CampingStage.NIGHT) {
                val state = _state.value
                narrate(NarrationScripts.bedtime(bedtime.settings, bedtime.sunrise, state.batteryPercent, state.bedtime?.forecast))
            }
        }
    }

    /** 설정을 바꿀 때마다 예상 잔량도 다시 계산한다 (온도·기상 시각이 바뀌면 달라짐). */
    private fun updateBedtime(transform: (BedtimeState) -> BedtimeState) {
        _state.update { state -> state.bedtime?.let { state.copy(bedtime = withForecast(state, transform(it))) } ?: state }
    }

    private fun withForecast(state: CampingUiState, bedtime: BedtimeState): BedtimeState {
        val now = clock()
        val forecast = BatteryForecast.forecast(
            records = state.records,
            currentBattery = state.batteryPercent,
            targetTempC = bedtime.settings.targetTempC,
            minTempC = state.arrival?.conditions?.weather?.minTempC,
            now = now,
            wakeAt = NightRules.nextWakeDateTime(now, bedtime.settings.wakeAt),
        )
        val rows = BedtimeRules.forecastTemps(bedtime.settings.targetTempC, bedtime.vehicleTempRange).map { temp ->
            TempForecast(
                temp,
                BatteryForecast.forecast(
                    records = state.records,
                    currentBattery = state.batteryPercent,
                    targetTempC = temp,
                    minTempC = state.arrival?.conditions?.weather?.minTempC,
                    now = now,
                    wakeAt = NightRules.nextWakeDateTime(now, bedtime.settings.wakeAt),
                ),
            )
        }
        return bedtime.copy(forecast = forecast, forecastRows = rows)
    }

    // --- 기록 ---

    /** 밤 단계를 거친 세션이 끝나면 기록을 남긴다. 저장에 실패해도 세션에는 영향이 없다. */
    private fun saveRecord(reason: EndReason) {
        val state = _state.value
        val startedAt = state.nightStartedAt ?: return
        val arrival = state.arrival ?: return
        val settings = state.bedtime?.settings ?: return
        val record = SessionRecord(
            id = UUID.randomUUID().toString(),
            nightStartedAt = startedAt,
            nightEndedAt = state.nightEndedAt ?: clock(),
            siteName = arrival.site.name,
            placeType = arrival.site.placeType,
            grade = arrival.assessment.grade,
            minTempC = arrival.conditions.weather?.minTempC,
            targetTempC = settings.targetTempC,
            theme = settings.theme,
            nightStartBattery = state.nightStartBattery,
            nightEndBattery = state.nightEndBattery ?: state.batteryPercent,
            // 아침에서 정상 종료하면 아침으로 넘어간 이유(기상 시각·배터리 하한)를 남긴다
            endReason = if (reason == EndReason.COMPLETED) state.lastEndReasonBeforeMorning() else reason,
            predictedLowPercent = state.bedtime.forecast?.lowPercent,
            predictedHighPercent = state.bedtime.forecast?.highPercent,
            feedback = state.feedback,
        )
        viewModelScope.launch {
            val records = recordStore.append(record)
            _state.update { it.copy(records = records) }
        }
    }

    private fun CampingUiState.lastEndReasonBeforeMorning(): EndReason = morningReason ?: EndReason.COMPLETED

    /** 아침 브리핑 "온도는 어땠나요?" */
    fun setFeedback(feedback: TempFeedback) {
        if (_state.value.stage == CampingStage.MORNING) _state.update { it.copy(feedback = feedback) }
    }

    /** 밤사이 실내·바깥 온도를 한 번 읽어 기록한다 (그래프용, 실패한 값은 null) */
    private fun sampleTemps() {
        viewModelScope.launch {
            val cabin = (vehicle.getCabinTemperature() as? VehicleResult.Success)?.value
            val outside = (vehicle.getOutsideTemperature() as? VehicleResult.Success)?.value
            _state.update { it.copy(nightTemps = it.nightTemps + TempSample(clock(), cabin, outside)) }
        }
    }

    /** 개발용: 지금 조건과 비슷한 샘플 기록을 더한다 (실제 측정값 아님). */
    fun addSampleRecords() {
        viewModelScope.launch {
            val state = _state.value
            val minTemp = state.arrival?.conditions?.weather?.minTempC ?: 5f
            val target = state.bedtime?.settings?.targetTempC ?: 22f
            var records = state.records
            for (sample in SampleRecords.generate(clock(), minTemp, target)) records = recordStore.append(sample)
            _state.update { it.copy(records = records) }
            updateBedtime { it }
        }
    }

    /** 개발용: 기록을 모두 지운다. */
    fun clearRecords() {
        viewModelScope.launch {
            recordStore.clear()
            _state.update { it.copy(records = emptyList()) }
            updateBedtime { it }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // --- 개발용: 알람과 배터리 감시가 생기기 전까지 사건을 손으로 발생시킨다 ---

    fun simulateWakeTime() = dispatch(CampingEvent.WakeTimeReached)

    // --- 밤 단계 ---

    /** 테마 소리 켜기/끄기 */
    fun toggleSound() {
        val turnOn = !_state.value.soundOn
        _state.update { it.copy(soundOn = turnOn) }
        if (_state.value.stage != CampingStage.NIGHT) return
        if (turnOn) playThemeSound() else stopThemeSound()
    }

    private fun onEnterNight() {
        val settings = _state.value.bedtime?.settings ?: return
        val wakeDateTime = NightRules.nextWakeDateTime(clock(), settings.wakeAt)
        _state.update { it.copy(wakeDateTime = wakeDateTime, nightStartedAt = clock()) }
        if (_state.value.soundOn) playThemeSound()
        checkBatteryLimit()
        sampleTemps()

        themeLightJob = viewModelScope.launch {
            val lightResult = themeLightApplier.apply(settings.theme)
            _state.update { it.copy(lightApply = lightResult) }
        }

        nightJob = viewModelScope.launch {
            // 앱이 켜져 있는 동안 기상 시각을 확인한다. TODO(미확정): Suspend 상태에서의 알람
            while (isActive) {
                if (NightTemps.isSampleDue(_state.value.nightTemps.lastOrNull()?.at, clock())) sampleTemps()
                if (NightRules.isWakeTimeReached(clock(), wakeDateTime)) {
                    dispatch(CampingEvent.WakeTimeReached)
                    break
                }
                delay(WAKE_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun onLeaveNight() {
        _state.update { it.copy(nightEndedAt = clock(), nightEndBattery = it.batteryPercent, morningReason = it.lastEndReason) }
        nightJob?.cancel()
        nightJob = null
        themeLightJob = null
        stopThemeSound()
    }

    /** 밤 단계에서 배터리가 하한에 닿으면 경고와 함께 아침으로 넘긴다. */
    private fun checkBatteryLimit() {
        val state = _state.value
        if (state.stage == CampingStage.NIGHT && NightRules.isBatteryAtLimit(state.batteryPercent)) {
            dispatch(CampingEvent.BatteryLow)
        }
    }

    private fun playThemeSound() {
        val theme = _state.value.bedtime?.settings?.theme ?: return
        val playing = sound.play(theme)
        _state.update { it.copy(soundPlaying = playing) }
    }

    private fun stopThemeSound() {
        sound.stop()
        _state.update { it.copy(soundPlaying = false) }
    }

    // --- 기상 단계 ---

    /** "일어났어요": 알람을 끄고 원래 차량 설정을 되돌린다. */
    fun wakeUp() {
        if (_state.value.stage != CampingStage.MORNING) return
        stopAlarm()
        startRestore()
        viewModelScope.launch {
            morningJob?.join()
            val state = _state.value
            if (state.stage == CampingStage.MORNING) state.morning?.briefing?.let { narrate(NarrationScripts.morning(it)) }
        }
    }

    // --- 발화 해석 (P1) ---

    /**
     * 사용자 요청을 서버(LLM)로 해석해 제안을 받는다. 제안은 규칙으로 다시 검사하고, 승인 전에는 아무것도 바꾸지 않는다.
     * 최근 대화를 함께 보내 "아니 1도 더" 같은 고쳐 말하기와 되묻기에 답할 수 있다.
     */
    fun ask(utterance: String) {
        val text = utterance.trim().take(MAX_UTTERANCE)
        val state = _state.value
        if (text.isEmpty() || !state.assistant.available || state.stage !in IntentRules.STAGES || state.assistant.busy) return
        val history = state.assistant.turns.takeLast(MAX_HISTORY_TURNS)
        val context = intentContext(state)
        updateAssistant { it.copy(turns = it.turns + Turn(TurnRole.USER, text), busy = true, partial = null, notice = null, pending = emptyList()) }
        assistantJob = viewModelScope.launch {
            val reply = intents.interpret(text, history, context)
            val now = _state.value
            if (now.stage != context.stage) return@launch // 기다리는 사이 단계가 바뀜
            if (reply == null) {
                updateAssistant { it.copy(busy = false, notice = NOT_UNDERSTOOD) }
                return@launch
            }
            val changes = IntentRules.resolve(reply.proposals, intentContext(now))
            // 제안이 있었지만 규칙 검사 후 바꿀 것이 없으면 "바꿀까요?"라고 묻지 않는다 (예: 지금과 같은 값)
            val text = if (reply.proposals.isNotEmpty() && changes.isEmpty()) NOTHING_TO_CHANGE else reply.reply
            updateAssistant { it.copy(turns = it.turns + Turn(TurnRole.ASSISTANT, text), pending = changes, busy = false) }
            if (now.voiceOn) voice.speak(text)
        }
    }

    /** [바꾸기]: 승인한 변경을 기존 규칙 코드로 실행한다. */
    fun approveChanges() {
        val changes = _state.value.assistant.pending
        if (changes.isEmpty()) return
        updateAssistant { it.copy(pending = emptyList()) }
        viewModelScope.launch {
            for (change in changes) apply(change)
            reply(DONE)
        }
    }

    /** [취소]: 아무것도 바꾸지 않는다. */
    fun rejectChanges() {
        if (_state.value.assistant.pending.isEmpty()) return
        updateAssistant { it.copy(pending = emptyList()) }
        reply(KEPT)
    }

    /** 마이크 버튼: 한 문장을 듣고 그대로 [ask]한다. 실패하면 글로 입력하라고 안내한다. */
    fun toggleListening() {
        val input = speech ?: return
        if (_state.value.assistant.listening) {
            stopListening()
            return
        }
        voice.stop()
        updateAssistant { it.copy(listening = true, partial = null, notice = null) }
        listenTimeoutJob = viewModelScope.launch {
            delay(LISTEN_TIMEOUT_MS)
            stopListening()
        }
        input.listen { event ->
            when (event) {
                SpeechEvent.Listening -> Unit
                is SpeechEvent.Partial -> updateAssistant { it.copy(partial = event.text) }
                is SpeechEvent.Final -> {
                    stopListening()
                    ask(event.text)
                }
                is SpeechEvent.Failed -> {
                    stopListening()
                    updateAssistant { it.copy(notice = VOICE_UNAVAILABLE) }
                }
                SpeechEvent.Ended -> stopListening()
            }
        }
    }

    private fun stopListening() {
        listenTimeoutJob?.cancel()
        listenTimeoutJob = null
        speech?.stop()
        updateAssistant { it.copy(listening = false, partial = null) }
    }

    private suspend fun apply(change: ProposedChange) {
        when (change) {
            is ProposedChange.Temperature -> {
                updateBedtime { it.copy(settings = it.settings.copy(targetTempC = change.to)) }
                // 밤에는 차량에 바로 적용한다 (지원 확인 → 적용 → 다시 조회, 실패해도 다시 시도하지 않음)
                if (_state.value.stage == CampingStage.NIGHT) {
                    val result = nightPreparer.applyTemperature(change.to)
                    _state.update { it.copy(temperatureApply = result) }
                }
            }
            is ProposedChange.WakeTime -> updateBedtime {
                it.copy(settings = it.settings.copy(wakeAt = change.to, wakeBasis = if (change.sunrise) WakeBasis.SUNRISE else WakeBasis.MANUAL))
            }
            is ProposedChange.Theme -> updateBedtime { it.copy(settings = it.settings.copy(theme = change.to)) }
            is ProposedChange.Sound -> if (_state.value.soundOn != change.on) toggleSound()
            is ProposedChange.Voice -> if (_state.value.voiceOn != change.on) toggleVoice()
        }
    }

    private fun reply(text: String) {
        updateAssistant { it.copy(turns = it.turns + Turn(TurnRole.ASSISTANT, text)) }
        if (_state.value.voiceOn) voice.speak(text)
    }

    private fun intentContext(state: CampingUiState): IntentContext {
        val bedtime = state.bedtime
        return IntentContext(
            stage = state.stage,
            targetTempC = bedtime?.settings?.targetTempC,
            vehicleTempRange = bedtime?.vehicleTempRange,
            wakeAt = bedtime?.settings?.wakeAt,
            sunrise = bedtime?.sunrise,
            theme = bedtime?.settings?.theme,
            soundOn = state.soundOn,
            voiceOn = state.voiceOn,
        )
    }

    private fun updateAssistant(transform: (AssistantState) -> AssistantState) {
        _state.update { it.copy(assistant = transform(it.assistant)) }
    }

    /** 단계가 바뀌면 대화를 지운다 (멀티턴은 같은 단계 안에서만) */
    private fun resetAssistant() {
        assistantJob?.cancel()
        if (_state.value.assistant.listening) stopListening()
        _state.update { it.copy(assistant = AssistantState(available = intents.available)) }
    }

    // --- 멘트와 음성 안내 ---

    fun toggleVoice() {
        val on = !_state.value.voiceOn
        _state.update { it.copy(voiceOn = on) }
        if (!on) voice.stop()
    }

    /**
     * 준비된 문장을 먼저 보여주고, AI 멘트가 오면 바꾼다. AI가 실패하면 준비된 문장을 그대로 쓴다.
     * 음성은 최종 문장이 정해진 뒤 한 번만 읽는다. AI 멘트는 화면·음성에만 쓰고 차량 제어로 이어지지 않는다.
     */
    private fun narrate(request: NarrationRequest) {
        narrationJob?.cancel()
        voice.stop()
        _state.update { it.copy(narration = NarrationState(request.moment, request.template, fromAi = false, pending = true)) }
        narrationJob = viewModelScope.launch {
            val ai = narration.narrate(request.moment, request.facts)
            val text = ai ?: request.template
            _state.update { it.copy(narration = NarrationState(request.moment, text, fromAi = ai != null, pending = false)) }
            if (_state.value.voiceOn) voice.speak(text)
        }
    }

    private fun stopNarration() {
        narrationJob?.cancel()
        narrationJob = null
        voice.stop()
    }

    /** "차박 마치기": 복구가 끝난 뒤에만 세션을 정상 종료한다. */
    fun finishSession() {
        if (_state.value.restore == null) return
        dispatch(CampingEvent.Restored())
    }

    private fun onEnterMorning() {
        val playing = alarm.start()
        _state.update { it.copy(morning = MorningState(alarmRinging = playing, alarmUnavailable = !playing)) }

        morningJob = viewModelScope.launch {
            val light = morningLightApplier.apply()
            updateMorning { it.copy(lightApply = light) }

            val state = _state.value
            val cabin = (vehicle.getCabinTemperature() as? VehicleResult.Success)?.value
            val briefing = MorningRules.briefing(
                reason = state.lastEndReason,
                weather = state.arrival?.conditions?.morningWeather,
                batteryAtNightStart = state.nightStartBattery,
                batteryNow = state.batteryPercent,
                cabinTempC = cabin,
            )
            updateMorning { it.copy(briefing = briefing) }
        }
    }

    private fun onLeaveMorning() {
        morningJob?.cancel()
        morningJob = null
        stopAlarm()
    }

    /**
     * 밤·아침에 기어가 P를 벗어났다. 소리·알람과 기상 시각 확인을 멈추고, 복구를 먼저 마친 뒤 [reason]으로 끝낸다.
     * 복구가 끝날 때까지 다른 사건(배터리 하한, 기상 시각)은 받지 않는다 ([dispatch] 참고).
     */
    private fun endAfterRestore(reason: EndReason) {
        stopNarration()
        stopAlarm()
        stopThemeSound()
        nightJob?.cancel()
        nightJob = null
        if (_state.value.restore != null) {
            dispatch(CampingEvent.Restored(reason))
            return
        }
        pendingEndReason = reason
        startRestore()
    }

    private fun startRestore() {
        if (restoreJob != null || _state.value.restore != null) return
        _state.update { it.copy(restoring = true) }
        restoreJob = viewModelScope.launch {
            // 테마·아침 조명을 켜는 중이면 끝난 뒤에 되돌린다. 순서가 바뀌면 그 조명이 남는다.
            themeLightJob?.join()
            morningJob?.join()
            val report = settingsRestorer.restore(_state.value.backup)
            _state.update { it.copy(restoring = false, restore = report) }
            restoreJob = null
            pendingEndReason?.let { reason ->
                pendingEndReason = null
                dispatch(CampingEvent.Restored(reason))
            }
        }
    }

    private fun stopAlarm() {
        alarm.stop()
        updateMorning { it.copy(alarmRinging = false) }
    }

    private fun updateMorning(transform: (MorningState) -> MorningState) {
        _state.update { state -> state.morning?.let { state.copy(morning = transform(it)) } ?: state }
    }

    override fun onCleared() {
        sound.stop()
        alarm.stop()
        voice.release()
        speech?.release()
    }

    private fun dispatch(event: CampingEvent) {
        // 기어 이탈로 복구하는 중에는 복구 완료만 받는다. 그사이 배터리 하한·기상 시각으로 아침에 넘어가지 않게 한다.
        if (pendingEndReason != null && event !is CampingEvent.Restored) return
        val previous = _state.value.stage
        when (val result = CampingSessionRules.next(previous, event)) {
            is CampingTransition.MoveTo -> {
                applyMove(event, result)
                if (previous != CampingStage.NIGHT && result.stage == CampingStage.NIGHT) onEnterNight()
                if (previous == CampingStage.NIGHT && result.stage != CampingStage.NIGHT) onLeaveNight()
                if (previous != CampingStage.MORNING && result.stage == CampingStage.MORNING) onEnterMorning()
                if (previous == CampingStage.MORNING && result.stage != CampingStage.MORNING) onLeaveMorning()
                if (result.stage == CampingStage.IDLE) {
                    stopNarration()
                    refreshHomeStatus()
                }
                if (result.stage != previous) resetAssistant()
                if (result.stage == CampingStage.IDLE && previous in setOf(CampingStage.NIGHT, CampingStage.MORNING)) {
                    saveRecord(result.reason ?: EndReason.COMPLETED)
                }
            }
            is CampingTransition.RestoreThenEnd -> endAfterRestore(result.reason)
            is CampingTransition.Rejected -> {
                // 사용자가 직접 누른 시작만 거절 이유를 보여준다.
                // 기어 변경 같은 차량 사건의 거절은 "해당 없음"이라 조용히 무시한다.
                if (event is CampingEvent.Start) {
                    _state.update { it.copy(message = result.why) }
                }
            }
        }
    }

    private fun applyMove(event: CampingEvent, result: CampingTransition.MoveTo) {
        _state.update {
                it.copy(
                    stage = result.stage,
                    // 새 세션을 시작하면 지난 종료 이유는 지운다.
                    lastEndReason = if (event is CampingEvent.Start) null else result.reason ?: it.lastEndReason,
                    message = null,
                    // 새 세션을 시작하면 지난 세션의 판정·설정·백업은 지운다.
                    arrival = if (event is CampingEvent.Start) null else it.arrival,
                    loadingSite = null,
                    locating = false,
                    hereConditions = null,
                    nearbyPlaces = if (event is CampingEvent.Start) emptyList() else it.nearbyPlaces,
                    loadingPlace = null,
                    bedtime = if (event is CampingEvent.Start) null else it.bedtime,
                    backup = if (event is CampingEvent.Start) null else it.backup,
                    temperatureApply = if (event is CampingEvent.Start) null else it.temperatureApply,
                    lightApply = if (event is CampingEvent.Start) null else it.lightApply,
                    wakeDateTime = if (event is CampingEvent.Start) null else it.wakeDateTime,
                    nightStartBattery = if (event is CampingEvent.Start) null else it.nightStartBattery,
                    morning = if (event is CampingEvent.Start) null else it.morning,
                    restoring = if (event is CampingEvent.Start) false else it.restoring,
                    restore = if (event is CampingEvent.Start) null else it.restore,
                    // 단계가 바뀌면 취침 준비 안의 화면은 처음(2/3)부터
                    bedtimeStep = BedtimeStep.SETTINGS,
                    narration = if (event is CampingEvent.Start) null else it.narration,
                    nightTemps = if (event is CampingEvent.Start) emptyList() else it.nightTemps,
                    feedback = if (event is CampingEvent.Start) null else it.feedback,
                    nightStartedAt = if (event is CampingEvent.Start) null else it.nightStartedAt,
                    nightEndedAt = if (event is CampingEvent.Start) null else it.nightEndedAt,
                    nightEndBattery = if (event is CampingEvent.Start) null else it.nightEndBattery,
                    morningReason = if (event is CampingEvent.Start) null else it.morningReason,
                )
            }
    }

    private companion object {
        const val TAG = "CampingViewModel"

        /** 시연 지역이 아닌 현재 위치의 장소 id */
        const val HERE_SITE_ID = "here"

        /** 근처 야영장 장소 id 앞붙이 (고캠핑 contentId) */
        const val CAMP_SITE_PREFIX = "camp-"
        const val MAX_UTTERANCE = 100
        const val MAX_HISTORY_TURNS = 6
        const val LISTEN_TIMEOUT_MS = 10_000L
        const val NOT_UNDERSTOOD = "이해하지 못했어요. 아래 예시처럼 말해 주세요."
        const val VOICE_UNAVAILABLE = "음성 입력을 쓸 수 없어요. 글로 입력해 주세요."
        const val DONE = "바꿨어요."
        const val KEPT = "그대로 둘게요."
        const val NOTHING_TO_CHANGE = "지금 설정과 같거나 바꿀 수 없는 값이라 그대로 둘게요."
        val DEFAULT_WAKE_AT: LocalTime = LocalTime.of(7, 0)
        const val WAKE_CHECK_INTERVAL_MS = 15_000L
    }
}
