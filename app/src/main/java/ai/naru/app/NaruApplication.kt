package ai.naru.app

import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.VehicleGateway
import android.app.Application

/**
 * 앱 전체에서 하나만 있어야 하는 객체를 들고 있는 곳.
 *
 * 차량 게이트웨이는 화면마다 새로 만들면 안 된다. 기어·배터리 상태가 화면마다 달라지기 때문이다.
 */
class NaruApplication : Application() {

    /** TODO(다음 이슈): 실제 차량 게이트웨이가 생기면 에뮬레이터·실차에서는 그것을 쓴다. */
    val vehicleGateway: VehicleGateway by lazy { FakeVehicleGateway() }
}
