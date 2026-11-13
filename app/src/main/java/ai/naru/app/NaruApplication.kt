package ai.naru.app

import ai.naru.app.camping.arrival.DemoSiteRepository
import ai.naru.app.camping.arrival.SiteRepository
import ai.naru.app.camping.morning.AndroidMorningAlarm
import ai.naru.app.camping.morning.MorningAlarm
import ai.naru.app.camping.night.AndroidNightSound
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

    /** TODO(다음 이슈): 중계 서버가 생기면 서버 구현으로 바꾼다. 지금은 시연 지역 샘플 데이터. */
    val siteRepository: SiteRepository by lazy { DemoSiteRepository() }

    val nightSound: NightSound by lazy { AndroidNightSound(this) }

    val morningAlarm: MorningAlarm by lazy { AndroidMorningAlarm(this) }
}
