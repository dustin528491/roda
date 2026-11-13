package ai.roadtalk.net

import ai.roadtalk.core.Item
import ai.roadtalk.core.ItemKind
import ai.roadtalk.core.Landmark
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class TurnContext(
    val phase: String,
    val window: String,
    val topic: String,
    val topicKo: String,
    val why: String,
    val destination: String = "",
    val destinationKind: String = "",
    val nextTurn: String = "",
    val traffic: String = "",
    val raining: Boolean = false,
    val passengers: Boolean = false,
    val landmark: String = "",
    val landmarkCategory: String = "",
)

@Serializable
data class GenerateReq(val lang: String, val level: String, val ctx: TurnContext, val avoid: List<String> = emptyList())

@Serializable
data class GenerateRes(
    val kind: String = "respond", val promptKo: String = "", val target: String = "",
    val hintKo: String = "", val expected: String = "", val long: Boolean = false,
)

@Serializable
data class FeedbackReq(val lang: String, val level: String, val promptKo: String, val expected: String, val heard: String)

@Serializable
data class FeedbackRes(val ok: Boolean = false, val better: String = "", val commentKo: String = "", val followUp: String = "")

@Serializable
data class LandmarkRes(val items: List<LandmarkDto> = emptyList())

@Serializable
data class LandmarkDto(val name: String, val category: String, val distanceM: Int, val side: String = "ahead")

/**
 * RoadTalk 서버. 인식된 '글자'만 보낸다(음성 원본은 차 밖으로 보내지 않는다).
 * 서버가 없거나 느리면 null을 돌려주고, 앱은 내장 문장으로 계속한다.
 */
class Api(private val base: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Volatile var online = true; private set

    suspend fun generate(req: GenerateReq): Item? = post<GenerateReq, GenerateRes>("/v1/generate", req, 11000)?.let {
        if (it.target.isBlank()) return null
        Item(
            id = "coach-${System.currentTimeMillis()}", topic = req.ctx.topic,
            kind = when (it.kind) { "listen" -> ItemKind.LISTEN; "repeat" -> ItemKind.REPEAT; "quiz" -> ItemKind.QUIZ; else -> ItemKind.RESPOND },
            promptKo = it.promptKo, target = it.target, hintKo = it.hintKo, expected = it.expected.ifBlank { it.target },
            long = it.long, source = "coach",
        )
    }

    suspend fun feedback(req: FeedbackReq): FeedbackRes? = post("/v1/feedback", req, 9000)

    suspend fun landmarks(lat: Double, lon: Double, heading: Float): List<Landmark> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("$base/v1/landmarks?lat=$lat&lon=$lon&heading=$heading").openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 8000
            val body = c.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<LandmarkRes>(body).items.map { Landmark(it.name, it.category, it.distanceM, it.side) }
        }.onFailure { Log.w("RoadTalk/Api", "landmarks: $it") }.getOrDefault(emptyList())
    }

    private suspend inline fun <reified A, reified B> post(path: String, body: A, timeoutMs: Int): B? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 4000; c.readTimeout = timeoutMs
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(json.encodeToString(body).toByteArray()) }
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            online = true
            json.decodeFromString<B>(text)
        }.onFailure { Log.w("RoadTalk/Api", "$path: $it"); online = false }.getOrNull()
    }
}
