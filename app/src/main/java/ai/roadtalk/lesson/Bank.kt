package ai.roadtalk.lesson

import ai.roadtalk.core.Item
import ai.roadtalk.core.ItemKind
import ai.roadtalk.core.Lang
import ai.roadtalk.core.Landmark
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BankItem(
    val id: String,
    val topic: String,
    val lang: String,
    val kind: String,
    val promptKo: String,
    val target: String,
    val hintKo: String,
    val expected: String = "",
    val long: Boolean = false,
    val demoAnswer: String = "",
)

/** 인터넷이 끊겨도 수업이 계속되도록 앱에 넣어 둔 문장 꾸러미(assets/bank.json). */
class Bank(context: Context) {
    private val all: List<BankItem> = runCatching {
        val text = context.assets.open("bank.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<List<BankItem>>(text)
    }.getOrDefault(emptyList())

    private val used = mutableSetOf<String>()

    fun demoAnswer(id: String): String? = all.firstOrNull { it.id == id }?.demoAnswer?.takeIf { it.isNotBlank() }

    fun pick(lang: Lang, topic: String, long: Boolean, listenOnly: Boolean, landmark: Landmark? = null): Item? {
        val pool = all.filter { it.lang == lang.code && it.topic == topic }
            .filter { if (listenOnly) it.kind == "listen" else it.kind != "listen" || topic == "school_zone" }
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it.id !in used }
        val sized = (fresh.ifEmpty { pool }).sortedBy { if (it.long == long) 0 else 1 }
        val b = (sized.takeWhile { it.long == sized.first().long }).random()
        used += b.id
        return b.toItem(landmark)
    }

    fun countFor(lang: Lang) = all.count { it.lang == lang.code }

    private fun BankItem.toItem(landmark: Landmark?): Item {
        fun fill(s: String) = if (landmark == null) s else s.replace("{name}", landmark.name).replace("{place}", placeWord(landmark.category))
        return Item(
            id = id, topic = topic,
            kind = when (kind) { "listen" -> ItemKind.LISTEN; "repeat" -> ItemKind.REPEAT; "quiz" -> ItemKind.QUIZ; else -> ItemKind.RESPOND },
            promptKo = fill(promptKo), target = fill(target), hintKo = fill(hintKo),
            expected = fill(expected.ifBlank { target }), long = long, source = "bank",
        )
    }

    private fun placeWord(cat: String) = when (cat) {
        "hospital", "clinic" -> "병원"; "school" -> "학교"; "station" -> "역"; "park" -> "공원"; "cafe" -> "카페"
        "bank" -> "은행"; "fuel" -> "주유소"; "charging_station" -> "충전소"; "library" -> "도서관"; "mall", "department_store" -> "백화점"
        "university" -> "대학교"; "police" -> "경찰서"; "museum" -> "박물관"; "hotel" -> "호텔"; else -> "건물"
    }
}
