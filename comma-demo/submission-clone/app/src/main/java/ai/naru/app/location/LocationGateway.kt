package ai.naru.app.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lon: Double)

/**
 * 차량 위치. 화면·ViewModel은 이 인터페이스만 안다 (이슈 #44).
 * 위치를 못 쓰면(미지원·권한 없음·null) null → 시연 지역 고르기로 대체 (AGENTS.md 4장).
 */
interface LocationGateway {
    /** 위치 권한이 있는지 (없으면 화면이 권한을 요청한다) */
    fun hasPermission(): Boolean

    /** 마지막으로 알려진 위치. 못 받으면 null */
    suspend fun lastKnown(): LatLng?

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}

/** 테스트·일반 에뮬레이터용 */
class FakeLocationGateway(var location: LatLng? = null, var permitted: Boolean = true) : LocationGateway {
    override fun hasPermission() = permitted
    override suspend fun lastKnown(): LatLng? = if (permitted) location else null
}

/**
 * Pleos FusedLocation SDK (v1.1.3). isSupported → initialize → getLastKnownLocation.
 * getLastKnownLocation은 새로 계산하지 않고 마지막 위치를 준다 (null 가능).
 */
class PleosLocationGateway(context: Context) : LocationGateway {

    private val appContext = context.applicationContext
    private val fused by lazy { FusedLocation(appContext).also { it.initialize() } }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, LocationGateway.PERMISSION) == PackageManager.PERMISSION_GRANTED

    override suspend fun lastKnown(): LatLng? {
        if (!hasPermission()) return null
        val supported = runCatching { FusedLocation(appContext).isSupported() }.getOrDefault(false)
        if (!supported) {
            Log.w(TAG, "이 차량은 FusedLocation을 지원하지 않음")
            return null
        }
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                try {
                    fused.getLastKnownLocation(object : FusedLocationListener {
                        override fun onChanged(location: LocationData?) {
                            if (continuation.isActive) continuation.resume(location?.let { LatLng(it.latitude, it.longitude) })
                        }

                        override fun onError(exception: Exception) {
                            Log.w(TAG, "위치를 받지 못함", exception)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                } catch (e: SecurityException) {
                    Log.w(TAG, "위치 권한 없음", e)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        const val TAG = "LocationGateway"
        const val TIMEOUT_MS = 6_000L
    }
}
