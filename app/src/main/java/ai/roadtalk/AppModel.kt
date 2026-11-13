package ai.roadtalk

import ai.roadtalk.car.DemoDrive
import ai.roadtalk.car.PleosCar
import ai.roadtalk.car.Scenario
import ai.roadtalk.core.Attempt
import ai.roadtalk.core.DriveState
import ai.roadtalk.core.Item
import ai.roadtalk.core.ItemKind
import ai.roadtalk.core.KOREAN
import ai.roadtalk.core.Lang
import ai.roadtalk.core.Landmark
import ai.roadtalk.core.Link
import ai.roadtalk.core.SdkStatus
import ai.roadtalk.lesson.Bank
import ai.roadtalk.lesson.Gate
import ai.roadtalk.lesson.GateDecision
import ai.roadtalk.lesson.Phase
import ai.roadtalk.lesson.Plan
import ai.roadtalk.lesson.PlanEvent
import ai.roadtalk.lesson.Planner
import ai.roadtalk.lesson.Similarity
import ai.roadtalk.lesson.Store
import ai.roadtalk.lesson.Topic
import ai.roadtalk.lesson.TopicPicker
import ai.roadtalk.lesson.Window
import ai.roadtalk.net.Api
import ai.roadtalk.net.FeedbackReq
import ai.roadtalk.net.GenerateReq
import ai.roadtalk.net.TurnContext
import ai.roadtalk.voice.Voice
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class Screen { START, DRIVE, ARRIVAL, SETTINGS }

enum class Stage(val ko: String) { IDLE("준비"), PROMPT("안내"), TARGET("들어 보세요"), LISTENING("말해 보세요"), FEEDBACK("피드백"), HOLD("잠시 멈춤"), QUIZ("함께 생각해 보세요") }

enum class InputMode(val ko: String) { PLEOS_STT("Pleos 음성 인식"), DEMO("시연용 자동 답변") }

data class Settings(
    val lang: Lang = Lang.EN,
    val level: String = "intermediate",
    val inputMode: InputMode = InputMode.PLEOS_STT,
    val familyMode: Boolean = true,
    val onlineCoach: Boolean = true,
    val scenario: Scenario = Scenario.COMMUTE,
    val speedup: Double = 8.0,
)

data class Ui(
    val screen: Screen = Screen.START,
    val settings: Settings = Settings(),
    val drive: DriveState = DriveState(),
    val sdk: SdkStatus = SdkStatus(),
    val demo: Boolean = false,
    val running: Boolean = false,
    val phase: Phase = Phase.WARMUP,
    val gate: GateDecision = GateDecision(Window.LONG, "", Int.MAX_VALUE),
    val topic: Topic? = null,
    val item: Item? = null,
    val stage: Stage = Stage.IDLE,
    val heard: String = "",
    val last: Attempt? = null,
    val plan: Plan? = null,
    val planTotalSec: Int = 0,
    val elapsedSec: Int = 0,
    val events: List<PlanEvent> = emptyList(),
    val todayAttempts: List<Attempt> = emptyList(),
    val serverOnline: Boolean = true,
    val log: List<String> = emptyList(),
)

class AppModel(app: Application) : AndroidViewModel(app) {
    private val tag = "RoadTalk/App"
    val car = PleosCar(app)
    private val demo = DemoDrive(car, viewModelScope)
    private val voice = Voice(app) { stt, tts -> car.setVoiceStatus(stt, tts) }
    private val bank = Bank(app)
    private val store = Store(app)
    private val api = Api(BuildConfig.SERVER_URL)
    private val planner = Planner()

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    private var loop: Job? = null
    private var itemJob: Job? = null
    private var sessionStartMs = 0L
    private var realStartMs = 0L
    private val recentTopics = mutableListOf<String>()
    private val recentTargets = mutableListOf<String>()
    private val priority = ArrayDeque<Item>()
    private val askedPlaces = mutableSetOf<String>()
    private var serverLandmarks: List<Landmark> = emptyList()
    private var lmFetchedAt = 0L
    private var lmLat = 0.0; private var lmLon = 0.0

    init {
        car.start()
        voice.start()
        voice.onPartial = { p -> _ui.update { it.copy(heard = p) } }
        car.onPlaceEntered = { key -> log("구역 알림: $key 진입 → 이곳에서 배운 표현 복습") }
        viewModelScope.launch { car.state.collect { s -> onDrive(s) } }
        viewModelScope.launch { car.status.collect { st -> _ui.update { it.copy(sdk = st) } } }
        // 구역 알림: 예전에 표현을 배운 장소들을 FusedLocation에 등록한다.
        viewModelScope.launch {
            delay(3000)
            store.places().forEach { (k, p) -> car.watchPlace(k, p.first, p.second) }
        }
    }

    // ---------------- 화면 동작 ----------------

    fun go(s: Screen) = _ui.update { it.copy(screen = s) }
    fun updateSettings(f: (Settings) -> Settings) = _ui.update { it.copy(settings = f(it.settings)) }
    fun clearRecord() { store.clear(); log("학습 기록을 지웠어요") }

    fun startDrive(useDemo: Boolean) {
        val st = _ui.value.settings
        if (useDemo) {
            demo.speedup = st.speedup
            demo.start(st.scenario, vehicleConnected = _ui.value.sdk.vehicle == Link.CONNECTED)
            demo.lang?.let { l -> updateSettings { it.copy(lang = l) } }
        }
        sessionStartMs = System.currentTimeMillis()
        realStartMs = sessionStartMs
        recentTopics.clear(); recentTargets.clear(); priority.clear(); askedPlaces.clear(); prefetched.clear()
        val remain = car.state.value.remainSec.takeIf { it > 0 } ?: (if (useDemo) 41 * 60 else 30 * 60)
        planner.start(remain)
        _ui.update { it.copy(screen = Screen.DRIVE, demo = useDemo, running = true, plan = planner.plan, planTotalSec = planner.totalSec, events = planner.events.toList(), todayAttempts = emptyList(), last = null, item = null, stage = Stage.IDLE) }
        loop?.cancel()
        loop = viewModelScope.launch { runLoop() }
    }

    fun endDrive() {
        loop?.cancel(); itemJob?.cancel(); voice.stopAll(); demo.stop()
        _ui.update { it.copy(running = false, screen = Screen.ARRIVAL, phase = Phase.DONE, stage = Stage.IDLE) }
    }

    fun skipDemo(sec: Int) = demo.skip(sec)

    fun backToStart() {
        loop?.cancel(); itemJob?.cancel(); voice.stopAll(); demo.stop()
        _ui.update { it.copy(screen = Screen.START, running = false, item = null, stage = Stage.IDLE) }
    }

    /** 주차 화면에서 표현 카드를 눌러 다시 듣기. */
    fun replay(a: Attempt) = viewModelScope.launch {
        voice.speak(a.better.ifBlank { a.item.target }, _ui.value.settings.lang.locale)
    }

    // ---------------- 상황 반영 ----------------

    private fun elapsedSec(): Int = if (_ui.value.demo) demo.simSec.toInt() else ((System.currentTimeMillis() - realStartMs) / 1000).toInt()

    private fun onDrive(s0: DriveState) {
        val s = if (serverLandmarks.isNotEmpty() && System.currentTimeMillis() - lmFetchedAt < 60_000) s0.copy(landmarks = serverLandmarks) else s0
        val gate = Gate.decide(s)
        val u = _ui.value
        if (!u.running) { _ui.update { it.copy(drive = s, gate = gate) }; return }
        val el = elapsedSec()
        planner.onRemainChanged(el, s.remainSec)?.let { ev -> log(ev.textKo) }
        val phase = planner.phase(el, s.remainSec, s.remainM, s.arrived || (!u.demo && s.gear == ai.roadtalk.core.Gear.PARK && el > 90))
        _ui.update { it.copy(drive = s, gate = gate, phase = phase, elapsedSec = el, plan = planner.plan, planTotalSec = planner.totalSec, events = planner.events.toList()) }
        maybeFetchLandmarks(s)
        // 같은 길 복습: 예전에 표현을 배운 장소 근처를 지나면 우선 순서에 넣는다.
        if (s.lat != 0.0) {
            store.placeItems(s.lat, s.lon, u.settings.lang.code, sessionStartMs).forEach { item ->
                val key = item.target
                if (askedPlaces.add(key)) { priority.addLast(item); log("같은 길 복습: '${item.target}'") }
            }
        }
        if (phase == Phase.DONE && u.phase != Phase.DONE) viewModelScope.launch { arrive() }
    }

    private fun maybeFetchLandmarks(s: DriveState) {
        if (!_ui.value.settings.onlineCoach || s.lat == 0.0) return
        val now = System.currentTimeMillis()
        val moved = Store.distM(s.lat, s.lon, lmLat, lmLon)
        if (now - lmFetchedAt < 20_000 && moved < 400) return
        lmFetchedAt = now; lmLat = s.lat; lmLon = s.lon
        viewModelScope.launch {
            val l = api.landmarks(s.lat, s.lon, s.heading)
            if (l.isNotEmpty()) { serverLandmarks = l; log("앞쪽 장소: " + l.take(3).joinToString { "${it.name}(${it.distanceM}m)" }) }
        }
    }

    // ---------------- 수업 진행 ----------------

    private suspend fun runLoop() {
        delay(800)
        say("오늘 ${(planner.totalSec / 60)}분 동안 ${_ui.value.settings.lang.label} 회화를 할게요. 복잡한 구간에서는 제가 알아서 멈출게요.", KOREAN)
        while (viewModelScope.isActive && _ui.value.running) {
            val u = _ui.value
            if (u.phase == Phase.DONE) break
            if (u.gate.window == Window.HOLD) { setStage(Stage.HOLD); delay(500); continue }
            if (u.gate.window == Window.PARKED && !u.demo) { delay(800); continue }
            val listenOnly = u.gate.window == Window.LISTEN_ONLY
            val long = u.gate.window == Window.LONG
            val topic = TopicPicker.pick(u.drive, u.phase, u.gate.window, recentTopics, u.settings.familyMode)
            val item = nextItem(topic, u, listenOnly, long) ?: run { delay(1000); null } ?: continue
            recentTopics += topic.key; recentTargets += item.target
            _ui.update { it.copy(topic = topic, item = item, heard = "") }
            prefetch(topic.key)
            val ok = runWithGate(item, listenOnly)
            if (!ok) continue
            delay(1500)
        }
    }

    private suspend fun nextItem(topic: Topic, u: Ui, listenOnly: Boolean, long: Boolean): Item? {
        val lang = u.settings.lang
        if (!listenOnly) priority.removeFirstOrNull()?.let { return it }
        if (topic.key == "review") {
            store.reviewItems(sessionStartMs, lang.code).firstOrNull { it.target !in recentTargets.takeLast(6) }?.let { return it }
            return bank.pick(lang, "smalltalk", long, false)
        }
        val lm = u.drive.landmarks.firstOrNull { it.distanceM in 80..600 }
        // 앞 문항을 하는 동안 미리 만들어 둔 문항이 있으면 바로 쓴다(서버 응답 4~5초를 기다리지 않게).
        prefetched.remove(topic.key)?.let { (item, at) ->
            val fresh = System.currentTimeMillis() - at < 120_000
            val fits = long || !item.long
            val sameLm = topic.key != "landmark" || (lm != null && item.target.contains(lm.name))
            if (fresh && fits && sameLm && item.target !in recentTargets) return item
        }
        if (u.settings.onlineCoach && !listenOnly && topic.key != "family_quiz") {
            val ctx = TurnContext(
                phase = u.phase.name, window = u.gate.window.name, topic = topic.key, topicKo = topic.ko, why = topic.why,
                destination = u.drive.destinationName, destinationKind = u.drive.destinationKind,
                nextTurn = u.drive.nextTurn?.let { "${Gate.turnLabel(it.kind)} ${it.distanceM}m ${it.description}" } ?: "",
                traffic = u.drive.traffic.name, raining = u.drive.wiperOn, passengers = u.drive.hasPassenger,
                landmark = if (topic.key == "landmark") lm?.name ?: "" else "", landmarkCategory = if (topic.key == "landmark") lm?.category ?: "" else "",
            )
            api.generate(GenerateReq(lang.code, u.settings.level, ctx, recentTargets.takeLast(8)))?.let { gen ->
                _ui.update { it.copy(serverOnline = true) }
                return if (!long) gen.copy(long = false) else gen
            }
            _ui.update { it.copy(serverOnline = false) }
        }
        return bank.pick(lang, topic.key, long, listenOnly, if (topic.key == "landmark") lm else null)
            ?: bank.pick(lang, if (listenOnly) "school_zone" else "smalltalk", long, listenOnly)
    }

    private val prefetched = java.util.concurrent.ConcurrentHashMap<String, Pair<Item, Long>>()

    /** 다음에 나올 만한 주제 두 개를 미리 서버에 요청해 둔다. */
    private fun prefetch(currentKey: String) {
        val u = _ui.value
        if (!u.settings.onlineCoach) return
        val next = TopicPicker.candidates(u.drive, u.phase, Window.LONG, u.settings.familyMode)
            .map { it }.filter { it.key != currentKey && it.key !in setOf("review", "school_zone", "family_quiz") && !prefetched.containsKey(it.key) }
            .distinctBy { it.key }.take(2)
        val lm = u.drive.landmarks.firstOrNull { it.distanceM in 80..900 }
        for (t in next) viewModelScope.launch {
            val ctx = TurnContext(
                phase = u.phase.name, window = "LONG", topic = t.key, topicKo = t.ko, why = t.why,
                destination = u.drive.destinationName, destinationKind = u.drive.destinationKind,
                traffic = u.drive.traffic.name, raining = u.drive.wiperOn, passengers = u.drive.hasPassenger,
                landmark = if (t.key == "landmark") lm?.name ?: "" else "", landmarkCategory = if (t.key == "landmark") lm?.category ?: "" else "",
            )
            api.generate(GenerateReq(u.settings.lang.code, u.settings.level, ctx, recentTargets.takeLast(8)))?.let { prefetched[t.key] = it to System.currentTimeMillis() }
        }
    }

    /** 문항을 진행하다가 게이트가 닫히면(회전 직전 등) 바로 멈추고, 열리면 처음부터 다시 한다. */
    private suspend fun runWithGate(item: Item, listenOnly: Boolean): Boolean {
        var attempt = 0
        while (attempt < 3) {
            attempt++
            var interrupted = false
            val job = viewModelScope.launch { runItem(item) }
            itemJob = job
            val watcher = viewModelScope.launch {
                while (isActive && job.isActive) {
                    val w = _ui.value.gate.window
                    val bad = w == Window.HOLD || (!listenOnly && w == Window.LISTEN_ONLY && item.kind != ItemKind.LISTEN) || !_ui.value.running
                    if (bad) { interrupted = true; job.cancel(); voice.stopAll(); break }
                    delay(250)
                }
            }
            job.join(); watcher.cancel()
            if (!interrupted) return true
            if (!_ui.value.running) return false
            setStage(Stage.HOLD)
            log("멈춤: ${_ui.value.gate.reasonKo}")
            say(holdLine(), KOREAN)
            while (_ui.value.running && (_ui.value.gate.window == Window.HOLD)) delay(400)
            if (_ui.value.gate.window == Window.LISTEN_ONLY && item.kind != ItemKind.LISTEN) return false
            delay(600)
            say("이어서 할게요.", KOREAN)
        }
        return false
    }

    private fun holdLine(): String {
        val g = _ui.value.gate
        return when {
            g.reasonKo.contains("방향지시등") -> "차선 바꾸는 동안 잠깐 멈출게요."
            g.reasonKo.contains("어린이") -> "어린이 보호구역이에요. 듣기만 할게요."
            else -> g.reasonKo.substringBefore("·").trim().let { "$it, 지나고 이어 갈게요." }
        }
    }

    private suspend fun runItem(item: Item) {
        val lang = _ui.value.settings.lang
        try {
            setStage(Stage.PROMPT)
            say(item.promptKo, KOREAN)
            when (item.kind) {
                ItemKind.LISTEN -> {
                    setStage(Stage.TARGET); say(item.target, lang.locale); delay(600); say(item.target, lang.locale)
                    record(item, "", 1f)
                }
                ItemKind.REPEAT -> {
                    setStage(Stage.TARGET); say(item.target, lang.locale)
                    val heard = hear(item)
                    val sc = Similarity.score(item.expected, heard)
                    if (sc >= 0.8f) { setStage(Stage.FEEDBACK); say(praise(sc), KOREAN); record(item, heard, sc) }
                    else {
                        setStage(Stage.FEEDBACK)
                        val miss = Similarity.missing(item.expected, heard).take(2)
                        record(item, heard, sc, comment = if (miss.isNotEmpty()) "빠진 단어: ${miss.joinToString()}" else "한 번 더")
                        say("한 번 더 들어 볼게요.", KOREAN); say(item.target, lang.locale)
                        val again = hear(item)
                        val sc2 = Similarity.score(item.expected, again)
                        record(item, again, sc2); if (sc2 >= 0.8f) say(praise(sc2), KOREAN)
                    }
                }
                ItemKind.RESPOND -> {
                    val heard = hear(item)
                    val sc = Similarity.score(item.expected, heard)
                    setStage(Stage.FEEDBACK)
                    var better = ""; var comment = ""
                    if (heard.isNotBlank() && _ui.value.settings.onlineCoach && sc < 0.95f) {
                        api.feedback(FeedbackReq(lang.code, _ui.value.settings.level, item.promptKo, item.expected, heard))?.let { fb ->
                            better = fb.better; comment = fb.commentKo
                        }
                    }
                    if (better.isBlank()) better = item.expected
                    if (comment.isBlank()) comment = if (sc >= 0.8f) praise(sc) else "이렇게 말하면 더 자연스러워요."
                    record(item, heard, sc, better, comment)
                    say(comment, KOREAN)
                    if (sc < 0.95f) { say(better, lang.locale); delay(300); say("따라 말해 보세요.", KOREAN); setStage(Stage.LISTENING); hear(item.copy(expected = better)) }
                }
                ItemKind.QUIZ -> {
                    setStage(Stage.QUIZ); delay(3500)
                    setStage(Stage.TARGET); say("정답은", KOREAN); say(item.target, lang.locale); say(item.target, lang.locale)
                    record(item, "", 1f)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(tag, "item failed", e)
        }
    }

    private fun praise(sc: Float) = when { sc >= 0.95f -> "완벽해요!"; sc >= 0.85f -> "좋아요!"; else -> "거의 맞았어요." }

    /** 차량 마이크로 듣는다. 시연 모드이거나 인식기가 없으면 시연용 답변을 천천히 채워 보여 준다. */
    private suspend fun hear(item: Item): String {
        setStage(Stage.LISTENING)
        val u = _ui.value
        if (u.settings.inputMode == InputMode.PLEOS_STT && u.sdk.stt == Link.CONNECTED) {
            val r = voice.listen(u.settings.lang.locale)
            // 데모 주행 중 마이크 입력이 없으면(에뮬레이터) 시연용 답변으로 이어 간다. 실제 주행에서는 그대로 쓴다.
            if (r != null && (r.isNotBlank() || !u.demo)) { _ui.update { it.copy(heard = r) }; return r }
        }
        val answer = bank.demoAnswer(item.id) ?: demoAnswerFor(item)
        _ui.update { it.copy(heard = "") }
        delay(700)
        val words = answer.split(" ")
        for (i in words.indices) { _ui.update { it.copy(heard = words.take(i + 1).joinToString(" ")) }; delay(260) }
        delay(500)
        return answer
    }

    /** LLM이 만든 문항의 시연용 답변: 목표 문장에서 관사·전치사 하나를 빼서 '조금 틀린' 답을 만든다. */
    private fun demoAnswerFor(item: Item): String {
        val w = item.expected.split(" ").toMutableList()
        val idx = w.indexOfFirst { it.lowercase() in setOf("the", "a", "an", "to", "for", "el", "la", "le", "les", "de") }
        if (idx >= 0 && recentTargets.size % 2 == 0) w.removeAt(idx)
        return w.joinToString(" ")
    }

    private fun record(item: Item, heard: String, score: Float, better: String = "", comment: String = "") {
        val s = _ui.value.drive
        val place = s.landmarks.firstOrNull { it.distanceM < 400 }?.name ?: s.nextTurn?.description?.takeIf { it.isNotBlank() } ?: ""
        val a = Attempt(item, heard, score, better, comment, s.lat, s.lon, place)
        if (item.kind != ItemKind.LISTEN || heard.isNotBlank()) store.record(a, _ui.value.settings.lang.code)
        _ui.update { u -> u.copy(last = a, todayAttempts = (u.todayAttempts.filterNot { it.item.id == item.id && it.at < a.at && it.item.kind != ItemKind.REPEAT } + a)) }
    }

    private suspend fun arrive() {
        loop?.cancel(); itemJob?.cancel(); voice.stopAll()
        val n = _ui.value.todayAttempts.count { it.item.kind != ItemKind.LISTEN }
        _ui.update { it.copy(running = false, stage = Stage.IDLE, screen = Screen.ARRIVAL) }
        demo.stop()
        say("도착했어요. 오늘 ${n}문장을 연습했어요. 수고했어요!", KOREAN)
    }

    private suspend fun say(text: String, locale: java.util.Locale) { voice.speak(text, locale) }
    private fun setStage(s: Stage) = _ui.update { it.copy(stage = s) }
    private fun log(t: String) { Log.i(tag, t); _ui.update { it.copy(log = (it.log + t).takeLast(30)) } }

    override fun onCleared() {
        voice.release(); car.stop(); demo.stop()
    }
}
