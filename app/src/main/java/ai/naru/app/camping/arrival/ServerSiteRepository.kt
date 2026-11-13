package ai.naru.app.camping.arrival

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime

/**
 * 중계 서버(`server/`)에서 장소 정보를 가져온다. 외부 API 키는 서버에만 있다.
 *
 * - [sites]: 실패하면 [IOException]을 던진다. 화면은 "불러오지 못했어요"와 다시 시도를 보여준다.
 * - [conditions]: 실패하면 모든 항목을 null로 돌려준다. 판정 규칙이 "확인 필요"로 보수적으로 처리한다.
 *   Demo 값으로 몰래 대체하지 않는다 (AGENTS.md: 외부 API 실패 시 값을 추정해서 채우지 않는다).
 */
class ServerSiteRepository(private val baseUrl: String) : SiteRepository {

    override suspend fun sites(): List<Site> = ServerJson.parseSites(get("/api/sites"))

    override suspend fun conditions(site: Site): SiteConditions = try {
        ServerJson.parseConditions(get("/api/sites/${site.id}/conditions"))
    } catch (e: Exception) {
        Log.w(TAG, "오늘 밤 정보를 불러오지 못함: ${site.id}", e)
        SiteConditions(weather = null, highTides = null)
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code: $path")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "ServerSiteRepository"

        /** 서버는 외부 API를 7초 안에 포기하므로 그보다 조금 길게 기다린다 */
        const val TIMEOUT_MS = 12_000
    }
}

/** 서버 응답 JSON → 앱 모델. 순수 함수라 단위 테스트한다. 모르는 값은 null로 둔다. */
object ServerJson {

    fun parseSites(json: String): List<Site> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Site(
                id = o.getString("id"),
                name = o.getString("name"),
                placeType = PlaceType.valueOf(o.getString("placeType")),
                permission = CampingPermission.valueOf(o.getString("permission")),
                facilities = o.getJSONArray("facilities").let { f -> (0 until f.length()).map { f.getString(it) } },
            )
        }
    }

    fun parseConditions(json: String): SiteConditions {
        val o = JSONObject(json)
        return SiteConditions(
            weather = o.objectOrNull("weather")?.let {
                NightWeather(
                    minTempC = it.getDouble("minTempC").toFloat(),
                    precipitation = Precipitation.valueOf(it.getString("precipitation")),
                    strongWind = it.getBoolean("strongWind"),
                    maxWindMs = if (it.isNull("maxWindMs") || !it.has("maxWindMs")) null else it.getDouble("maxWindMs").toFloat(),
                )
            },
            highTides = o.arrayOrNull("highTides")?.let { a -> (0 until a.length()).map { LocalTime.parse(a.getString(it)) } },
            lowTides = if (o.has("lowTides")) o.arrayOrNull("lowTides")?.let { a -> (0 until a.length()).map { LocalTime.parse(a.getString(it)) } } else null,
            sunrise = o.stringOrNull("sunrise")?.let(LocalTime::parse),
            morningWeather = o.objectOrNull("morningWeather")?.let {
                MorningWeather(tempC = it.getDouble("tempC").toFloat(), sky = Sky.valueOf(it.getString("sky")))
            },
        )
    }

    private fun JSONObject.objectOrNull(key: String): JSONObject? = if (isNull(key)) null else optJSONObject(key)
    private fun JSONObject.arrayOrNull(key: String): JSONArray? = if (isNull(key)) null else optJSONArray(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
}
