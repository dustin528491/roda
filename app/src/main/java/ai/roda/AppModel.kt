package ai.roda

import ai.roda.car.DemoDrive
import ai.roda.car.PleosCar
import ai.roda.car.Scenario
import ai.roda.core.Attempt
import ai.roda.core.DriveState
import ai.roda.core.Item
import ai.roda.core.ItemKind
import ai.roda.core.KOREAN
import ai.roda.core.Lang
import ai.roda.core.Landmark
import ai.roda.core.Link
import ai.roda.core.SdkStatus
import ai.roda.lesson.Bank
import ai.roda.lesson.Gate
import ai.roda.lesson.GateDecision
import ai.roda.lesson.Phase
import ai.roda.lesson.Plan
import ai.roda.lesson.PlanEvent
import ai.roda.lesson.Planner
import ai.roda.lesson.Scene
import ai.roda.lesson.SceneBank
import ai.roda.lesson.Similarity
import ai.roda.lesson.Turn
import ai.roda.lesson.TurnKind2
import ai.roda.lesson.toItem
import ai.roda.lesson.turns
import ai.roda.lesson.Store
import ai.roda.lesson.Topic
import ai.roda.lesson.TopicPicker
import ai.roda.lesson.Window
import ai.roda.net.Api
import ai.roda.net.FeedbackReq
import ai.roda.net.GenerateReq
import ai.roda.net.TurnContext
import ai.roda.voice.Voice
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

/** 오늘 말할 수 있게 된 상황 하나. */
data class CanDo(val labelKo: String, val sentence: String, val hintKo: String, val place: String, val score: Float, val turns: Int)

data class RouteMark(val label: String, val frac: Float, val next: Boolean = false)

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
    /** 길 그림: 시간 기준 0~1 위치에 놓인 안내 지점들. */
    val marks: List<RouteMark> = emptyList(),
    val routeFrac: Float = 0f,
    val scene: Scene? = null,
    val turnIdx: Int = 0,
    val turnTotal: Int = 0,
    val canDos: List<CanDo> = emptyList(),
    /** 길 그림에서 '영어로 말하며 지나온 구간'(시간 축 0~1 범위들). */
    val painted: List<Pair<Float, Float>> = emptyList(),
    /** 예전 주행에서 말할 수 있게 된 구간(시간 축 0~1을 100칸으로 나눈 것). */
    val known: Set<Int> = emptySet(),
    val userSpeakSec: Int = 0,
    val appSpeakSec: Int = 0,
    val userTurns: Int = 0,
    val lessonRealSec: Int = 0,
)

class AppModel(app: Application) : AndroidViewModel(app) {
    private val tag = "Roda/App"
    val car = PleosCar(app)
    private val demo = DemoDrive(car, viewModelScope)
    private val voice = Voice(app) { stt, tts -> car.setVoiceStatus(stt, tts) }
    private val bank = Bank(app)
    private val sceneBank = SceneBank(app)
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
    private val priority = ArrayDeque<Scene>()
    private val askedPlaces = mutableSetOf<String>()
    private var lastPlaceAtSec = -10_000
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
        recentTopics.clear(); recentTargets.clear(); priority.clear(); askedPlaces.clear(); prefetched.clear(); lastPlaceAtSec = -10_000; markAt.clear(); appSpeakMs = 0; userSpeakMs = 0; userTurns = 0; micSilent = false
        val remain = car.state.value.remainSec.takeIf { it > 0 } ?: (if (useDemo) 41 * 60 else 30 * 60)
        planner.start(remain)
        _ui.update { it.copy(screen = Screen.DRIVE, demo = useDemo, running = true, plan = planner.plan, planTotalSec = planner.totalSec, events = planner.events.toList(), todayAttempts = emptyList(), last = null, item = null, stage = Stage.IDLE, scene = null, canDos = emptyList(), painted = emptyList(), known = emptySet(), userSpeakSec = 0, appSpeakSec = 0, userTurns = 0, lessonRealSec = 0) }
        loop?.cancel()
        loop = viewModelScope.launch { runLoop() }
    }

    fun endDrive() {
        loop?.cancel(); itemJob?.cancel(); voice.stopAll(); demo.stop()
        log("측정: 실제 ${(System.currentTimeMillis() - realStartMs) / 1000}초, 앱이 말한 시간 ${appSpeakMs / 1000}초, 사용자가 말한 시간 ${userSpeakMs / 1000}초, 사용자 발화 ${userTurns}번")
        _ui.update { it.copy(running = false, screen = Screen.ARRIVAL, phase = Phase.DONE, stage = Stage.IDLE, userSpeakSec = (userSpeakMs / 1000).toInt(), appSpeakSec = (appSpeakMs / 1000).toInt(), userTurns = userTurns, lessonRealSec = ((System.currentTimeMillis() - realStartMs) / 1000).toInt()) }
    }

    fun skipDemo(sec: Int) = demo.skip(sec)

    fun backToStart() {
        loop?.cancel(); itemJob?.cancel(); voice.stopAll(); demo.stop()
        _ui.update { it.copy(screen = Screen.START, running = false, item = null, stage = Stage.IDLE) }
    }

    fun replayText(text: String) = viewModelScope.launch { voice.speak(text, _ui.value.settings.lang.locale) }

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
        val phase = planner.phase(el, s.remainSec, s.remainM, s.arrived || (!u.demo && s.gear == ai.roda.core.Gear.PARK && el > 90))
        _ui.update { it.copy(drive = s, gate = gate, phase = phase, elapsedSec = el, plan = planner.plan, planTotalSec = planner.totalSec, events = planner.events.toList()) }
        updateRoute(s, el)
        maybeFetchLandmarks(s)
        // 같은 길 복습: 예전에 표현을 배운 장소 근처를 지나면 우선 순서에 넣는다.
        // 한 번 주행에 최대 3번, 5분(주행 시간 기준) 간격으로만 끼워 넣어 본 수업을 방해하지 않는다.
        if (s.lat != 0.0 && askedPlaces.size < 3 && el - lastPlaceAtSec >= 300 && priority.isEmpty()) {
            store.placeScene(s.lat, s.lon, u.settings.lang.code, sessionStartMs, askedPlaces)?.let { sc ->
                askedPlaces.add(sc.base); lastPlaceAtSec = el
                priority.addLast(sc); log("같은 길 복습: '${sc.base}'")
            }
        }
        if (s.lat != 0.0 && store.knownNear(s.lat, s.lon, u.settings.lang.code, sessionStartMs)) {
            val b = (_ui.value.routeFrac * 100).toInt().coerceIn(0, 99)
            if (b !in _ui.value.known) _ui.update { it.copy(known = it.known + b) }
        }
        if (phase == Phase.DONE && u.phase != Phase.DONE) viewModelScope.launch { arrive() }
    }

    private val markAt = LinkedHashMap<String, Float>()

    /** 남은 거리 비율을 남은 시간에 곱해, 앞의 안내 지점을 수업 시간 축 위에 놓는다. 지나간 지점은 마지막 위치에 남는다. */
    private fun updateRoute(s: DriveState, el: Int) {
        val total = planner.totalSec.coerceAtLeast(1)
        val now = (el.toFloat() / total).coerceIn(0f, 1f)
        val remainM = s.remainM.coerceAtLeast(1)
        s.ahead.forEach { t ->
            val label = if (t.kind == ai.roda.core.TurnKind.ARRIVE) "도착" else t.description.substringBefore(" ·").ifBlank { Gate.turnLabel(t.kind) }.take(8)
            markAt[label] = (now + (t.distanceM.toFloat() / remainM).coerceIn(0f, 1f) * (1f - now)).coerceIn(0f, 1f)
        }
        val nextLabel = s.ahead.firstOrNull()?.let { t -> if (t.kind == ai.roda.core.TurnKind.ARRIVE) "도착" else t.description.substringBefore(" ·").ifBlank { Gate.turnLabel(t.kind) }.take(8) }
        val marks = markAt.entries.sortedBy { it.value }.fold(mutableListOf<RouteMark>()) { acc, e ->
            if (acc.isEmpty() || e.value - acc.last().frac > 0.07f || e.key == "도착") acc += RouteMark(e.key, e.value, e.key == nextLabel); acc
        }
        _ui.update { it.copy(marks = marks, routeFrac = now, userSpeakSec = (userSpeakMs / 1000).toInt(), appSpeakSec = (appSpeakMs / 1000).toInt(), userTurns = userTurns, lessonRealSec = ((System.currentTimeMillis() - realStartMs) / 1000).toInt()) }
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
    // 수업의 단위는 '상황'이다. 한 상황에서 본보기 → 따라 말하기 → 바꿔 말하기 → 질문에 답하기로 사용자가 여러 번 말한다.

    private suspend fun runLoop() {
        delay(800)
        say("${_ui.value.settings.lang.ko} 교실 시작할게요. 복잡한 구간에서는 제가 멈출게요.", KOREAN)
        while (viewModelScope.isActive && _ui.value.running) {
            val u = _ui.value
            if (u.phase == Phase.DONE) break
            if (u.gate.window == Window.HOLD) { setStage(Stage.HOLD); delay(500); continue }
            if (u.gate.window == Window.PARKED && !u.demo) { delay(800); continue }
            val long = u.gate.window == Window.LONG
            val topic = TopicPicker.pick(u.drive, u.phase, u.gate.window, recentTopics, u.settings.familyMode)
            // 듣기만(어린이 보호구역)과 함께 퀴즈는 한 문항짜리로 진행한다.
            if (u.gate.window == Window.LISTEN_ONLY || topic.key == "family_quiz") {
                val listen = u.gate.window == Window.LISTEN_ONLY
                val item = bank.pick(u.settings.lang, if (listen) "school_zone" else "family_quiz", false, listen) ?: run { delay(1000); null } ?: continue
                recentTopics += topic.key
                _ui.update { it.copy(topic = topic, item = item, scene = null, turnIdx = 0, turnTotal = 0, heard = "") }
                gated(listenOk = listen) { runSingle(item) }
                delay(800); continue
            }
            val scene = nextScene(topic, u) ?: run { delay(1000); null } ?: continue
            recentTopics += topic.key; recentTargets += scene.base
            prefetch(topic.key)
            runScene(scene, topic, long)
            delay(700)
        }
    }

    private fun contextFor(u: Ui, t: Topic, lm: Landmark?) = TurnContext(
        phase = u.phase.name, window = u.gate.window.name, topic = t.key, topicKo = t.ko, why = t.why,
        destination = u.drive.destinationName, destinationKind = u.drive.destinationKind,
        nextTurn = u.drive.nextTurn?.let { "${Gate.turnLabel(it.kind)} ${it.distanceM}m ${it.description}" } ?: "",
        traffic = u.drive.traffic.name, raining = u.drive.wiperOn, passengers = u.drive.hasPassenger,
        landmark = if (t.key == "landmark") lm?.name ?: "" else "", landmarkCategory = if (t.key == "landmark") lm?.category ?: "" else "",
    )

    private suspend fun nextScene(topic: Topic, u: Ui): Scene? {
        val lang = u.settings.lang
        priority.removeFirstOrNull()?.let { return it }
        if (topic.key == "review") {
            store.reviewScene(sessionStartMs, lang.code)?.let { return it }
            return sceneBank.pick(lang, "smalltalk")
        }
        val lm = u.drive.landmarks.firstOrNull { it.distanceM in 80..600 }
        // 앞 상황을 하는 동안 미리 만들어 둔 것이 있으면 바로 쓴다(서버 응답 5~6초를 기다리지 않게).
        prefetched.remove(topic.key)?.let { (sc, at) ->
            if (System.currentTimeMillis() - at < 180_000 && sc.base !in recentTargets) return sc
        }
        if (u.settings.onlineCoach) {
            api.scene(GenerateReq(lang.code, u.settings.level, contextFor(u, topic, lm), recentTargets.takeLast(8)))?.let {
                _ui.update { x -> x.copy(serverOnline = true) }; return it
            }
            _ui.update { it.copy(serverOnline = false) }
        }
        return sceneBank.pick(lang, topic.key) ?: sceneBank.pick(lang, "smalltalk") ?: sceneBank.pick(lang, "warmup")
    }

    private val prefetched = java.util.concurrent.ConcurrentHashMap<String, Pair<Scene, Long>>()

    /** 다음에 나올 만한 주제 두 개의 상황을 미리 서버에 요청해 둔다. */
    private fun prefetch(currentKey: String) {
        val u = _ui.value
        if (!u.settings.onlineCoach) return
        val next = TopicPicker.candidates(u.drive, u.phase, Window.LONG, u.settings.familyMode)
            .filter { it.key != currentKey && it.key !in setOf("review", "school_zone", "family_quiz") && !prefetched.containsKey(it.key) }
            .distinctBy { it.key }.take(2)
        val lm = u.drive.landmarks.firstOrNull { it.distanceM in 80..900 }
        for (t in next) viewModelScope.launch {
            api.scene(GenerateReq(u.settings.lang.code, u.settings.level, contextFor(u, t, lm).copy(window = "LONG"), recentTargets.takeLast(8)))
                ?.let { prefetched[t.key] = it to System.currentTimeMillis() }
        }
    }

    /** 상황 하나를 차례대로 진행한다. 중간에 멈춤 구간이 오면 그 차례만 다시 한다. */
    private suspend fun runScene(scene: Scene, topic: Topic, long: Boolean) {
        val turns = scene.turns(long)
        if (turns.isEmpty()) return
        val startFrac = _ui.value.routeFrac
        val scores = mutableListOf<Float>()
        for ((i, turn) in turns.withIndex()) {
            val u = _ui.value
            if (!u.running || u.phase == Phase.DONE) break
            if (u.gate.window == Window.LISTEN_ONLY) break
            if (i > 0 && scene.source != "review" && u.phase == Phase.REVIEW && topic.key != "review") break
            val item = turn.toItem(scene, i)
            _ui.update { it.copy(topic = topic, scene = scene, item = item, turnIdx = i, turnTotal = turns.size, heard = "") }
            val ok = gated(listenOk = false) { runTurn(scene, turn, item)?.let { scores += it } }
            if (!ok) break
        }
        if (scores.isEmpty()) return
        val avg = scores.average().toFloat()
        val d = _ui.value.drive
        // 배운 곳: 길 그림에서 지금 위치와 가장 가까운 안내 지점 이름
        val nowF = _ui.value.routeFrac
        val place = _ui.value.marks.filter { it.label != "도착" }.minByOrNull { kotlin.math.abs(it.frac - nowF) }?.label?.let { "$it 근처" } ?: ""
        val sentence = scene.base.ifBlank { scene.variations.firstOrNull()?.target ?: "" }
        val hint = scene.hintKo.ifBlank { scene.variations.firstOrNull()?.hintKo ?: "" }
        val done = CanDo(scene.canDoKo.ifBlank { topic.ko }, sentence, hint, place, avg, scores.size)
        if (scene.source == "bank" || scene.source == "coach") store.recordScene(scene, avg, d.lat, d.lon, place)
        _ui.update { u ->
            val painted = if (avg >= 0.8f) u.painted + (startFrac to u.routeFrac) else u.painted
            // 복습은 새로 할 수 있게 된 상황이 아니므로 목록에 넣지 않는다.
            val list = if (scene.source == "review" || sentence.isBlank()) u.canDos else u.canDos.filterNot { it.sentence == done.sentence } + done
            u.copy(canDos = list, painted = painted)
        }
    }

    /** 한 차례. 점수(0~1)를 돌려준다. */
    private suspend fun runTurn(scene: Scene, turn: Turn, item: Item): Float? {
        val lang = _ui.value.settings.lang
        try {
            when (turn.kind) {
                TurnKind2.MODEL -> {
                    setStage(Stage.PROMPT); if (turn.sayKo.isNotBlank()) say(turn.sayKo, KOREAN)
                    setStage(Stage.TARGET); say(turn.target, lang.locale)
                    var heard = hear(item); var sc = Similarity.score(turn.target, heard)
                    record(item, heard, sc, comment = tip(turn.target, heard, sc))
                    if (sc < 0.8f) { setStage(Stage.TARGET); say(turn.target, lang.locale); heard = hear(item); sc = Similarity.score(turn.target, heard); record(item, heard, sc) }
                    return sc
                }
                TurnKind2.CUE -> {
                    setStage(Stage.PROMPT); say(turn.sayKo, KOREAN)
                    var heard = hear(item); var sc = Similarity.score(turn.target, heard)
                    setStage(Stage.FEEDBACK)
                    record(item, heard, sc, better = turn.target, comment = tip(turn.target, heard, sc))
                    if (sc < 0.8f) { say(turn.target, lang.locale); heard = hear(item); sc = Similarity.score(turn.target, heard); setStage(Stage.FEEDBACK); record(item, heard, sc, better = turn.target) }
                    else delay(900)
                    return sc
                }
                TurnKind2.ASK -> {
                    setStage(Stage.PROMPT); say(turn.ask, lang.locale)
                    val heard = hear(item); val sc = Similarity.score(turn.target, heard)
                    setStage(Stage.FEEDBACK)
                    var better = ""; var comment = ""; var ok = sc >= 0.8f
                    if (heard.isNotBlank() && _ui.value.settings.onlineCoach && sc < 0.95f) {
                        api.feedback(FeedbackReq(lang.code, _ui.value.settings.level, "${scene.setupKo} 질문: ${turn.ask}", turn.target, heard))?.let { fb ->
                            better = fb.better; comment = fb.commentKo; ok = ok || fb.ok
                        }
                    }
                    if (better.isBlank()) better = if (ok) heard else turn.target
                    if (comment.isBlank()) comment = tip(turn.target, heard, sc)
                    val shown = if (ok) maxOf(sc, 0.9f) else sc     // 본보기와 달라도 자연스러운 답이면 맞은 것으로 본다
                    record(item, heard, shown, better, comment)
                    if (!ok || Similarity.score(better, heard) < 0.95f) {
                        if (comment.isNotBlank()) say(comment, KOREAN)
                        say(better, lang.locale)
                        val again = hear(item.copy(expected = better, target = better))
                        record(item, again, Similarity.score(better, again), better, comment)
                    } else delay(900)
                    return shown
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(tag, "turn failed", e); return null
        }
    }

    /** 고칠 점 한 줄. 빠진 단어를 알려 준다. */
    private fun tip(target: String, heard: String, sc: Float): String {
        if (heard.isBlank()) return ""
        if (sc >= 0.95f) return "완벽해요"
        val miss = Similarity.missing(target, heard).take(2)
        return if (miss.isNotEmpty()) "'${miss.joinToString(", ")}'가 빠졌어요" else "거의 맞았어요"
    }

    /** 듣기만(어린이 보호구역)과 함께 퀴즈. */
    private suspend fun runSingle(item: Item) {
        val lang = _ui.value.settings.lang
        try {
            setStage(Stage.PROMPT); say(item.promptKo, KOREAN)
            if (item.kind == ItemKind.QUIZ) { setStage(Stage.QUIZ); delay(3500); setStage(Stage.TARGET); say(item.target, lang.locale); say(item.target, lang.locale) }
            else { setStage(Stage.TARGET); say(item.target, lang.locale); delay(500); say(item.target, lang.locale) }
            record(item, "", 1f)
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { Log.w(tag, "single failed", e) }
    }

    /** 진행하다가 게이트가 닫히면(회전 직전 등) 바로 멈추고, 열리면 그 차례를 처음부터 다시 한다. */
    private suspend fun gated(listenOk: Boolean, block: suspend () -> Unit): Boolean {
        var attempt = 0
        while (attempt < 3) {
            attempt++
            var interrupted = false
            val job = viewModelScope.launch { block() }
            itemJob = job
            val watcher = viewModelScope.launch {
                while (isActive && job.isActive) {
                    val w = _ui.value.gate.window
                    val bad = w == Window.HOLD || (!listenOk && w == Window.LISTEN_ONLY) || !_ui.value.running
                    if (bad) { interrupted = true; job.cancel(); voice.stopAll(); break }
                    delay(250)
                }
            }
            job.join(); watcher.cancel()
            if (!interrupted) return true
            if (!_ui.value.running) return false
            if (_ui.value.gate.window == Window.LISTEN_ONLY) return false
            setStage(Stage.HOLD)
            log("멈춤: ${_ui.value.gate.reasonKo}")
            say(holdLine(), KOREAN)
            while (_ui.value.running && (_ui.value.gate.window == Window.HOLD)) delay(400)
            if (_ui.value.gate.window == Window.LISTEN_ONLY) return false
            delay(500)
            say("이어서 할게요.", KOREAN)
        }
        return false
    }

    private fun holdLine(): String {
        val g = _ui.value.gate
        return when {
            g.reasonKo.contains("방향지시등") -> "잠깐 멈출게요."
            else -> g.reasonKo.substringBefore("·").trim().let { "$it, 지나고 이어 갈게요." }
        }
    }

    private fun praise(sc: Float) = when { sc >= 0.95f -> "완벽해요!"; sc >= 0.85f -> "좋아요!"; else -> "거의 맞았어요." }

    /** 차량 마이크로 듣는다. 시연 모드이거나 인식기가 없으면 시연용 답변을 천천히 채워 보여 준다. */
    private suspend fun hear(item: Item): String {
        setStage(Stage.LISTENING)
        val u = _ui.value
        if (u.settings.inputMode == InputMode.PLEOS_STT && u.sdk.stt == Link.CONNECTED && !micSilent) {
            val t0 = System.currentTimeMillis()
            val r = voice.listen(u.settings.lang.locale)
            // 데모 주행 중 마이크 입력이 없으면(에뮬레이터) 시연용 답변으로 이어 간다. 실제 주행에서는 그대로 쓴다.
            if (r != null && (r.isNotBlank() || !u.demo)) {
                userSpeakMs += (System.currentTimeMillis() - t0 - 600).coerceAtLeast(0); userTurns++
                _ui.update { it.copy(heard = r) }; return r
            }
            if (u.demo) micSilent = true   // 마이크가 없는 시연 환경: 이후에는 기다리지 않는다
        }
        val answer = bank.demoAnswer(item.id) ?: demoAnswerFor(item)
        demoTurnCount++
        _ui.update { it.copy(heard = "") }
        delay(700)
        // 시연용 답변은 보통 말 속도(초당 약 2.6단어)로 흘려보내고, 그 시간만 '말한 시간'으로 센다.
        val words = answer.split(" ")
        val t0 = System.currentTimeMillis()
        for (i in words.indices) { _ui.update { it.copy(heard = words.take(i + 1).joinToString(" ")) }; delay(380) }
        userSpeakMs += System.currentTimeMillis() - t0; userTurns++
        delay(400)
        return answer
    }

    /** LLM이 만든 문항의 시연용 답변: 목표 문장에서 관사·전치사 하나를 빼서 '조금 틀린' 답을 만든다. */
    private fun demoAnswerFor(item: Item): String {
        val w = item.expected.split(" ").toMutableList()
        val idx = w.indexOfFirst { it.lowercase() in setOf("the", "a", "an", "to", "for", "el", "la", "le", "les", "de") }
        if (idx >= 0 && demoTurnCount % 4 == 2) w.removeAt(idx)   // 네 번에 한 번쯤 관사·전치사를 빠뜨린다
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
        _ui.update { it.copy(running = false, stage = Stage.IDLE, screen = Screen.ARRIVAL, userSpeakSec = (userSpeakMs / 1000).toInt(), appSpeakSec = (appSpeakMs / 1000).toInt(), userTurns = userTurns, lessonRealSec = ((System.currentTimeMillis() - realStartMs) / 1000).toInt()) }
        demo.stop()
        log("측정: 실제 ${(System.currentTimeMillis() - realStartMs) / 1000}초, 앱이 말한 시간 ${appSpeakMs / 1000}초, 사용자가 말한 시간 ${userSpeakMs / 1000}초, 사용자 발화 ${userTurns}번")
        say("도착했어요. 오늘 ${_ui.value.canDos.size}가지 상황을 ${_ui.value.settings.lang.ko}로 말했어요.", KOREAN)
    }

    /** 말하기 비율 측정: 앱이 말한 시간과 사용자가 말한 시간(밀리초). */
    private var appSpeakMs = 0L
    private var userSpeakMs = 0L
    private var userTurns = 0
    private var micSilent = false
    private var demoTurnCount = 0

    private suspend fun say(text: String, locale: java.util.Locale) {
        val t0 = System.currentTimeMillis(); voice.speak(text, locale); appSpeakMs += System.currentTimeMillis() - t0
    }
    private fun setStage(s: Stage) = _ui.update { it.copy(stage = s) }
    private fun log(t: String) { Log.i(tag, t); _ui.update { it.copy(log = (it.log + t).takeLast(30)) } }

    override fun onCleared() {
        voice.release(); car.stop(); demo.stop()
    }
}
