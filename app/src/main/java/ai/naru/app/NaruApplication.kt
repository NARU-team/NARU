package ai.naru.app

import ai.naru.app.camping.arrival.DemoSiteRepository
import ai.naru.app.camping.arrival.ServerSiteRepository
import ai.naru.app.camping.arrival.SiteRepository
import ai.naru.app.camping.morning.AndroidMorningAlarm
import ai.naru.app.camping.morning.MorningAlarm
import ai.naru.app.camping.narration.NarrationRepository
import ai.naru.app.camping.narration.PleosVoiceGuide
import ai.naru.app.camping.narration.ServerNarrationRepository
import ai.naru.app.camping.narration.TemplateOnlyNarration
import ai.naru.app.camping.narration.VoiceGuide
import ai.naru.app.camping.night.AndroidNightSound
import ai.naru.app.camping.record.FileSessionRecordStore
import ai.naru.app.camping.record.SessionRecordStore
import java.io.File
import ai.naru.app.camping.night.NightSound
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.PleosVehicleGateway
import ai.naru.app.vehicle.VehicleGateway
import android.app.Application

/**
 * 앱 전체에서 하나만 있어야 하는 객체를 들고 있는 곳.
 *
 * 차량 게이트웨이는 화면마다 새로 만들면 안 된다. 기어·배터리 상태가 화면마다 달라지기 때문이다.
 */
class NaruApplication : Application() {

    /**
     * Pleos 차량 서비스가 있으면(Pleos 에뮬레이터·실차) 실제 차량, 없으면(일반 에뮬레이터·기기) Fake를 쓴다.
     */
    val vehicleGateway: VehicleGateway by lazy {
        if (PleosVehicleGateway.isAvailable(this)) PleosVehicleGateway(this) else FakeVehicleGateway()
    }

    /** 서버 주소(local.properties의 naru.serverUrl)가 있으면 중계 서버, 없으면 내장 샘플 데이터. */
    val siteRepository: SiteRepository by lazy {
        if (BuildConfig.SERVER_URL.isNotBlank()) ServerSiteRepository(BuildConfig.SERVER_URL) else DemoSiteRepository()
    }

    val nightSound: NightSound by lazy { AndroidNightSound(this) }

    val morningAlarm: MorningAlarm by lazy { AndroidMorningAlarm(this) }

    /** 서버가 있으면 AI 멘트(서버에서 LLM 호출), 없으면 준비된 문장만 쓴다. */
    val narrationRepository: NarrationRepository by lazy {
        if (BuildConfig.SERVER_URL.isNotBlank()) ServerNarrationRepository(BuildConfig.SERVER_URL) else TemplateOnlyNarration
    }

    val voiceGuide: VoiceGuide by lazy { PleosVoiceGuide(this) }

    /** 차박 기록 (앱 내부 저장소 파일) */
    val recordStore: SessionRecordStore by lazy { FileSessionRecordStore(File(filesDir, "session_records.json")) }
}
