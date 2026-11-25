package ai.naru.app.camping.narration

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 멘트를 만드는 곳. 실패하면 null을 돌려주고, 호출하는 쪽이 준비된 문장을 쓴다. */
interface NarrationRepository {
    suspend fun narrate(moment: NarrationMoment, facts: Map<String, Any>): String?
}

/** 서버가 없는 개발 환경: 항상 준비된 문장을 쓴다. */
object TemplateOnlyNarration : NarrationRepository {
    override suspend fun narrate(moment: NarrationMoment, facts: Map<String, Any>): String? = null
}

/** 중계 서버 `POST /api/narration` (서버에서 LLM 호출, 키는 서버에만 있음). */
class ServerNarrationRepository(private val baseUrl: String) : NarrationRepository {

    override suspend fun narrate(moment: NarrationMoment, facts: Map<String, Any>): String? = try {
        val body = NarrationJson.request(moment, facts)
        NarrationJson.text(post("/api/narration", body))
    } catch (e: Exception) {
        Log.w(TAG, "멘트를 받지 못해 준비된 문장을 씀: $moment", e)
        null
    }

    private suspend fun post(path: String, body: String): String = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "ServerNarration"

        /** 서버는 LLM을 8초 안에 포기한다. 기다리는 동안 화면에는 준비된 문장이 먼저 보인다. */
        const val TIMEOUT_MS = 10_000
    }
}

/** 멘트 요청·응답 JSON. 순수 함수라 단위 테스트한다. */
object NarrationJson {

    fun request(moment: NarrationMoment, facts: Map<String, Any>): String {
        val factsJson = JSONObject()
        for ((key, value) in facts) {
            factsJson.put(key, if (value is List<*>) JSONArray(value) else value)
        }
        return JSONObject().put("moment", moment.name).put("facts", factsJson).toString()
    }

    /** `{ "text": "..." }`에서 문장을 꺼낸다. 비어 있으면 null. */
    fun text(json: String): String? = JSONObject(json).optString("text").trim().ifEmpty { null }
}
