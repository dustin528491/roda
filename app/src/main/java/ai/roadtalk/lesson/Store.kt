package ai.roadtalk.lesson

import ai.roadtalk.core.Attempt
import ai.roadtalk.core.Item
import ai.roadtalk.core.ItemKind
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.Normalizer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/** 목표 문장과 인식 결과의 단어 단위 일치도(0~1). 대소문자·문장부호·악센트 차이는 무시한다. */
object Similarity {
    fun words(s: String): List<String> {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return n.replace("'", "").replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    fun score(expected: String, heard: String): Float {
        val a = words(expected); val b = words(heard)
        if (a.isEmpty()) return 1f
        if (b.isEmpty()) return 0f
        val d = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) d[i][0] = i
        for (j in 0..b.size) d[0][j] = j
        for (i in 1..a.size) for (j in 1..b.size) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
        }
        return (1f - d[a.size][b.size].toFloat() / max(a.size, b.size)).coerceIn(0f, 1f)
    }

    /** 빠진 단어(목표에는 있는데 말하지 않은 단어). 피드백에 쓴다. */
    fun missing(expected: String, heard: String): List<String> {
        val h = words(heard).toMutableList()
        return words(expected).filter { w -> if (w in h) { h.remove(w); false } else true }
    }
}

@Serializable
data class SavedAttempt(
    val itemId: String, val topic: String, val kind: String,
    val promptKo: String, val target: String, val hintKo: String,
    val heard: String, val score: Float, val better: String = "",
    val lat: Double = 0.0, val lon: Double = 0.0, val place: String = "",
    val at: Long, val lang: String = "en",
)

@Serializable
private data class Saved(val attempts: List<SavedAttempt> = emptyList())

/**
 * 학습 기록. '장소–표현–정답률'을 기기 안에 저장하고, 같은 길을 다시 지날 때 꺼낸다.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("roadtalk", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var data: Saved = runCatching { json.decodeFromString<Saved>(prefs.getString("data", null) ?: "{}") }.getOrDefault(Saved())

    val attempts get() = data.attempts

    fun record(a: Attempt, lang: String) {
        val s = SavedAttempt(a.item.id, a.item.topic, a.item.kind.name, a.item.promptKo, a.item.target, a.item.hintKo,
            a.heard, a.score, a.better, a.lat, a.lon, a.placeLabel, a.at, lang)
        data = data.copy(attempts = (data.attempts + s).takeLast(600))
        prefs.edit().putString("data", json.encodeToString(data)).apply()
    }

    fun clear() { data = Saved(); prefs.edit().remove("data").apply() }

    fun today(sinceMs: Long) = data.attempts.filter { it.at >= sinceMs }

    /** 오늘 틀린 표현부터 다시 묻는다. */
    fun reviewItems(sinceMs: Long, lang: String, limit: Int = 4): List<Item> {
        val todays = data.attempts.filter { it.at >= sinceMs && it.lang == lang && it.kind != ItemKind.LISTEN.name }
        val worst = todays.groupBy { it.target }.map { (_, v) -> v.minBy { it.score } }.sortedBy { it.score }
        return worst.take(limit).map { it.toItem("review", "오늘 배운 표현을 다시 말해 보세요. ${it.promptKo}") }
    }

    /** 지금 위치에서 반경 안에서 '예전에(오늘 이전)' 배운 표현. */
    fun placeItems(lat: Double, lon: Double, lang: String, beforeMs: Long, radiusM: Double = 180.0): List<Item> =
        data.attempts.filter { it.at < beforeMs && it.lang == lang && it.lat != 0.0 && it.kind != ItemKind.LISTEN.name && distM(lat, lon, it.lat, it.lon) < radiusM }
            .distinctBy { it.target }.sortedBy { it.score }.take(2)
            .map { it.toItem("place", "${it.place.ifBlank { "이곳" }}에서 배웠던 표현이에요. ${it.promptKo}") }

    /** 구역 알림을 걸 장소들(같은 표현을 배운 위치를 묶은 대표점). */
    fun places(limit: Int = 10): List<Pair<String, Pair<Double, Double>>> =
        data.attempts.filter { it.lat != 0.0 }.groupBy { "%.3f,%.3f".format(it.lat, it.lon) }
            .entries.sortedByDescending { it.value.size }.take(limit)
            .map { (k, v) -> k to (v.map { it.lat }.average() to v.map { it.lon }.average()) }

    private fun SavedAttempt.toItem(source: String, prompt: String) = Item(
        id = "$source-$itemId-${at}", topic = topic, kind = if (kind == "QUIZ") ItemKind.QUIZ else ItemKind.REPEAT,
        promptKo = prompt, target = better.ifBlank { target }, hintKo = hintKo, expected = better.ifBlank { target }, source = source,
    )

    companion object {
        fun distM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
            val dy = (aLat - bLat) * 111_320.0
            val dx = (aLon - bLon) * 111_320.0 * cos(Math.toRadians((aLat + bLat) / 2))
            return sqrt(dx * dx + dy * dy)
        }
    }
}
