package ai.roda.lesson

import ai.roda.core.Item
import ai.roda.core.ItemKind
import ai.roda.core.Lang
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 같은 문장을 한 군데만 바꿔 다시 말하게 하는 짧은 신호("이번엔 30분"). */
@Serializable
data class Variation(val cueKo: String, val target: String, val hintKo: String = "")

/**
 * 상황 하나. 도로 위의 한 장면에서 사용자가 여러 번 말하도록 짠다.
 * 본보기 한 번 → 따라 말하기 → 바꿔 말하기 2~3번 → (긴 직진이면) 원어 질문에 답하기.
 * 앱의 한국어 설명은 한 줄로 줄이고, 말하는 쪽은 사용자가 되게 한다.
 */
@Serializable
data class Scene(
    val id: String = "",
    val topic: String = "",
    val lang: String = "en",
    val canDoKo: String = "",          // 이 상황을 끝내면 할 수 있게 되는 일: "늦는다고 알리기"
    val setupKo: String = "",          // 한 줄 상황 설명
    val base: String = "",
    val hintKo: String = "",
    val variations: List<Variation> = emptyList(),
    val question: String = "",         // 원어 질문
    val questionKo: String = "",
    val sampleAnswer: String = "",
    val source: String = "bank",       // bank, coach, review, place
)

enum class TurnKind2 { MODEL, CUE, ASK }

data class Turn(val kind: TurnKind2, val sayKo: String, val target: String, val hintKo: String, val ask: String = "")

fun Scene.turns(long: Boolean): List<Turn> = buildList {
    if (base.isNotBlank()) add(Turn(TurnKind2.MODEL, setupKo, base, hintKo))
    variations.take(if (long) 3 else 1).forEach { add(Turn(TurnKind2.CUE, it.cueKo, it.target, it.hintKo)) }
    if (long && question.isNotBlank() && sampleAnswer.isNotBlank()) add(Turn(TurnKind2.ASK, questionKo, sampleAnswer, questionKo, question))
}

fun Turn.toItem(scene: Scene, idx: Int) = Item(
    id = "${scene.id}#$idx", topic = scene.topic,
    kind = if (kind == TurnKind2.MODEL) ItemKind.REPEAT else ItemKind.RESPOND,
    promptKo = if (kind == TurnKind2.ASK) ask else sayKo, target = target, hintKo = hintKo, expected = target,
    long = kind == TurnKind2.ASK, source = scene.source,
)

/** 인터넷이 끊겨도 수업이 이어지도록 앱에 넣어 둔 상황 꾸러미(assets/scenes.json). */
class SceneBank(context: Context) {
    private val all: List<Scene> = runCatching {
        val text = context.assets.open("scenes.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<List<Scene>>(text)
    }.getOrDefault(emptyList())
    private val used = mutableSetOf<String>()

    fun pick(lang: Lang, topic: String): Scene? {
        val pool = all.filter { it.lang == lang.code && it.topic == topic }
        if (pool.isEmpty()) return null
        val s = (pool.filter { it.id !in used }.ifEmpty { pool }).random()
        used += s.id
        return s
    }

    fun count(lang: Lang) = all.count { it.lang == lang.code }
}
