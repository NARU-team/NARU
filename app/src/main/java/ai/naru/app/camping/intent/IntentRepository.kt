package ai.naru.app.camping.intent

import ai.naru.app.camping.arrival.PlaceType
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 발화를 제안으로 바꾸는 곳. 실패하면 null. */
interface IntentRepository {
    /** 서버가 없으면 false → 화면에서 발화 해석을 숨긴다 */
    val available: Boolean

    suspend fun interpret(utterance: String, history: List<Turn>, context: IntentContext): IntentReply?
}

object DisabledIntent : IntentRepository {
    override val available = false
    override suspend fun interpret(utterance: String, history: List<Turn>, context: IntentContext): IntentReply? = null
}

/** 중계 서버 `POST /api/intent` (서버에서 LLM 호출, 대화는 저장하지 않음) */
class ServerIntentRepository(private val baseUrl: String) : IntentRepository {

    override val available = true

    override suspend fun interpret(utterance: String, history: List<Turn>, context: IntentContext): IntentReply? = try {
        IntentJson.reply(post(IntentJson.request(utterance, history, context)))
    } catch (e: Exception) {
        Log.w(TAG, "발화를 해석하지 못함", e)
        null
    }

    private suspend fun post(body: String): String = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + "/api/intent").openConnection() as HttpURLConnection
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
        const val TAG = "ServerIntent"
        const val TIMEOUT_MS = 12_000
    }
}

/** 발화 해석 요청·응답 JSON. 순수 함수라 단위 테스트한다. 알 수 없는 제안은 건너뛴다. */
object IntentJson {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun request(utterance: String, history: List<Turn>, context: IntentContext): String {
        val turns = JSONArray(history.map { JSONObject().put("role", it.role.name.lowercase()).put("text", it.text) })
        val ctx = JSONObject()
            .put("stage", context.stage.name)
            .put("targetTempC", context.targetTempC?.toDouble() ?: JSONObject.NULL)
            .put("tempMinC", context.vehicleTempRange?.start?.toDouble() ?: JSONObject.NULL)
            .put("tempMaxC", context.vehicleTempRange?.endInclusive?.toDouble() ?: JSONObject.NULL)
            .put("wakeAt", context.wakeAt?.format(hhmm) ?: JSONObject.NULL)
            .put("sunrise", context.sunrise?.format(hhmm) ?: JSONObject.NULL)
            .put("theme", context.theme?.name ?: JSONObject.NULL)
            .put("soundOn", context.soundOn)
            .put("voiceOn", context.voiceOn)
        return JSONObject().put("utterance", utterance).put("history", turns).put("context", ctx).toString()
    }

    fun reply(json: String): IntentReply {
        val o = JSONObject(json)
        val array = o.getJSONArray("proposals")
        val proposals = (0 until array.length()).mapNotNull { i -> proposal(array.getJSONObject(i)) }
        return IntentReply(o.getString("reply"), o.optBoolean("needsClarification"), proposals)
    }

    private fun proposal(p: JSONObject): IntentProposal? {
        val action = runCatching { IntentAction.valueOf(p.getString("type")) }.getOrNull() ?: return null
        return IntentProposal(
            action = action,
            celsius = p.doubleOrNull("celsius")?.toFloat(),
            deltaC = p.doubleOrNull("deltaC")?.toFloat(),
            time = p.stringOrNull("time")?.let { runCatching { LocalTime.parse(it, hhmm) }.getOrNull() },
            theme = p.stringOrNull("theme")?.let { runCatching { PlaceType.valueOf(it) }.getOrNull() },
            on = if (p.isNull("on") || !p.has("on")) null else p.getBoolean("on"),
        )
    }

    private fun JSONObject.doubleOrNull(key: String) = if (!has(key) || isNull(key)) null else getDouble(key)
    private fun JSONObject.stringOrNull(key: String) = if (!has(key) || isNull(key)) null else getString(key)
}
