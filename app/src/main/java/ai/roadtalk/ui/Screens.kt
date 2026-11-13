package ai.roadtalk.ui

import ai.roadtalk.AppModel
import ai.roadtalk.InputMode
import ai.roadtalk.Screen
import ai.roadtalk.Stage
import ai.roadtalk.Ui
import ai.roadtalk.car.Scenario
import ai.roadtalk.core.Attempt
import ai.roadtalk.core.Caution
import ai.roadtalk.core.ItemKind
import ai.roadtalk.core.Lang
import ai.roadtalk.core.Link
import ai.roadtalk.core.Traffic
import ai.roadtalk.core.TurnSignal
import ai.roadtalk.lesson.Gate
import ai.roadtalk.lesson.Phase
import ai.roadtalk.lesson.Window
import ai.roadtalk.lesson.fmtDist
import ai.roadtalk.lesson.fmtMin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun App(vm: AppModel) {
    val ui by vm.ui.collectAsState()
    RoadTalkTheme {
        Box(Modifier.fillMaxSize().background(P.background)) {
            when (ui.screen) {
                Screen.START -> StartScreen(ui, vm)
                Screen.DRIVE -> DriveScreen(ui, vm)
                Screen.ARRIVAL -> ArrivalScreen(ui, vm)
                Screen.SETTINGS -> SettingsScreen(ui, vm)
            }
        }
    }
}

private val APP_ICON = Icons.Outlined.RecordVoiceOver

/** 화면 폭이 좁으면(분할 화면 세로 창 840dp) 한 줄로 쌓는다. */
private const val WIDE_DP = 1200

// ============================== 출발 ==============================

@Composable
fun StartScreen(ui: Ui, vm: AppModel) {
    val d = ui.drive
    val hasRoute = d.routeActive && d.remainSec > 0
    val total = if (hasRoute) d.remainSec else 41 * 60
    val warm = (total * 0.08).toInt().coerceIn(120, 240)
    val review = (total * 0.12).toInt().coerceIn(180, 360)
    Column(Modifier.fillMaxSize()) {
        PageNav("로드토크", APP_ICON, subtitle = "출퇴근길 회화 코치") { NavAction(icon = Icons.Outlined.Settings) { vm.go(Screen.SETTINGS) } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth.value >= WIDE_DP
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    Column(Modifier.width(420.dp).fillMaxHeight().background(P.surface).verticalScroll(rememberScrollState())) {
                        if (hasRoute) NavRow(Icons.Outlined.Navigation, "내비 경로", d.destinationName, selected = true)
                        Scenario.entries.forEach { s ->
                            NavRow(scenarioIcon(s), s.ko, selected = !hasRoute && ui.settings.scenario == s) { vm.updateSettings { it.copy(scenario = s) } }
                        }
                    }
                    VDivider()
                }
                Column(Modifier.weight(1f).fillMaxHeight().background(P.surface)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 36.dp)) {
                        Text(if (hasRoute) "오늘 가는 길" else "데모 주행 · ${ui.settings.scenario.ko}", style = T.labelM.copy(color = P.textSecondary))
                        Spacer(Modifier.height(8.dp))
                        Text(if (hasRoute) d.destinationName else ui.settings.scenario.desc, style = T.titleM, maxLines = 2)
                        Spacer(Modifier.height(28.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmtMin(total), style = T.headlineL)
                            Spacer(Modifier.width(16.dp))
                            Text("동안 이렇게 진행해요", style = T.bodyL.copy(color = P.textSecondary), modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        PlanSegments(total, warm, review, current = null)
                        Spacer(Modifier.height(14.dp))
                        Text("막히면 대화를 더하고, 목적지 2km 전부터는 오늘 틀린 표현만 다시 물어요.", style = T.bodyS.copy(color = P.textSecondary))
                        Spacer(Modifier.height(36.dp))
                        Text("연습할 언어", style = T.labelM.copy(color = P.textSecondary))
                        Spacer(Modifier.height(12.dp))
                        Segmented(Lang.entries.map { it to it.label }, ui.settings.lang, Modifier.fillMaxWidth()) { l -> vm.updateSettings { it.copy(lang = l) } }
                        if (!wide) {
                            Spacer(Modifier.height(36.dp))
                            Text("데모 시나리오", style = T.labelM.copy(color = P.textSecondary))
                            Spacer(Modifier.height(12.dp))
                            Segmented(Scenario.entries.map { it to it.ko.take(5) }, ui.settings.scenario, Modifier.fillMaxWidth()) { s -> vm.updateSettings { it.copy(scenario = s) } }
                        }
                        Spacer(Modifier.height(36.dp))
                        Text("Pleos Connect SDK", style = T.labelM.copy(color = P.textSecondary))
                        Spacer(Modifier.height(12.dp))
                        SdkTiles(ui, columns = if (wide) 5 else 3)
                    }
                    HDivider()
                    BlackButton(if (hasRoute) "출발하며 시작" else "데모 주행으로 시작", Modifier.fillMaxWidth(), height = 110.dp) { vm.startDrive(useDemo = !hasRoute) }
                }
            }
        }
    }
}

private fun scenarioIcon(s: Scenario): ImageVector = when (s) {
    Scenario.COMMUTE -> Icons.Outlined.WorkOutline
    Scenario.RAINY_HOME -> Icons.Outlined.Umbrella
    Scenario.FAMILY -> Icons.Outlined.FamilyRestroom
    Scenario.TRAVEL -> Icons.Outlined.Luggage
}

@Composable
private fun SdkTiles(ui: Ui, columns: Int) {
    val s = ui.sdk
    val tiles = listOf(
        Triple(Icons.Outlined.Directions, "NaviHelper", s.navi),
        Triple(Icons.Outlined.MyLocation, "FusedLocation", s.location),
        Triple(Icons.Outlined.DirectionsCar, "Vehicle", s.vehicle),
        Triple(Icons.Outlined.Mic, "SpeechToText", s.stt),
        Triple(Icons.Outlined.VolumeUp, "TextToSpeech", s.tts),
    )
    Column(Modifier.fillMaxWidth().border(1.dp, P.divider)) {
        tiles.chunked(columns).forEachIndexed { r, row ->
            if (r > 0) HDivider()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { i, (icon, name, link) ->
                    if (i > 0) VDivider()
                    Column(Modifier.weight(1f).padding(24.dp)) {
                        Icon(icon, null, tint = P.textPrimary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(20.dp))
                        Text(name, style = T.bodyM, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(linkText(link), style = T.labelM.copy(color = linkColor(link)))
                    }
                }
                repeat(columns - row.size) { VDivider(); Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun linkText(l: Link) = when (l) { Link.CONNECTED -> "연결됨"; Link.CONNECTING -> "연결 중"; Link.DEMO -> "데모"; Link.UNAVAILABLE -> "없음" }
private fun linkColor(l: Link) = when (l) { Link.CONNECTED -> P.positive; Link.DEMO -> P.textSecondary; else -> P.textTertiary }

/** 테두리 안에 워밍업·본 대화·복습 칸을 시간 비율대로 나눈다. 지금 단계만 검은 바탕. */
@Composable
fun PlanSegments(total: Int, warm: Int, review: Int, current: Phase?, progress: Float? = null) {
    val main = (total - warm - review).coerceAtLeast(1)
    val segs = listOf(Triple(Phase.WARMUP, "워밍업 ${fmtMin(warm)}", warm), Triple(Phase.MAIN, "본 대화 ${fmtMin(main)}", main), Triple(Phase.REVIEW, "복습 ${fmtMin(review)}", review))
    Column {
        Row(Modifier.fillMaxWidth().height(72.dp).border(1.dp, P.basic300).height(IntrinsicSize.Min)) {
            segs.forEachIndexed { i, (ph, label, w) ->
                if (i > 0) VDivider()
                val sel = ph == current
                Box(Modifier.weight(w.toFloat().coerceAtLeast(total * 0.14f)).fillMaxHeight().background(if (sel) P.selected else P.surface), contentAlignment = Alignment.Center) {
                    Text(label, style = T.labelM.copy(color = if (sel) P.basic00 else P.textSecondary), maxLines = 1)
                }
            }
        }
        if (progress != null) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(P.basic200)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0.005f, 1f)).fillMaxHeight().background(P.positive))
            }
        }
    }
}

// ============================== 주행 중 ==============================

@Composable
fun DriveScreen(ui: Ui, vm: AppModel) {
    val d = ui.drive
    Column(Modifier.fillMaxSize()) {
        PageNav("로드토크", APP_ICON, subtitle = listOfNotNull(ui.phase.ko, ui.topic?.ko, ui.settings.lang.label).joinToString(" · ")) {
            Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.End) {
                Text("도착까지", style = T.labelS.copy(color = P.textSecondary))
                Text("${fmtMin(d.remainSec)} · ${fmtDist(d.remainM)}", style = T.titleSBold)
            }
            if (ui.demo) NavAction("+1분") { vm.skipDemo(60) }
            NavAction("끝내기") { vm.endDrive() }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth.value >= WIDE_DP
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(420.dp).fillMaxHeight().background(P.surface).verticalScroll(rememberScrollState())) { ContextRows(ui, compact = false) }
                    VDivider()
                    CoachPanel(ui, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(P.surface).height(IntrinsicSize.Min)) { ContextCells(ui) }
                    HDivider()
                    CoachPanel(ui, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        HDivider()
        Column(Modifier.fillMaxWidth().background(P.surface).padding(horizontal = 30.dp, vertical = 18.dp)) {
            ui.plan?.let { p -> PlanSegments(ui.planTotalSec.coerceAtLeast(1), p.warmupSec, p.reviewSec, ui.phase, progress = ui.elapsedSec.toFloat() / ui.planTotalSec.coerceAtLeast(1)) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val spoken = ui.todayAttempts.count { it.item.kind != ItemKind.LISTEN && it.heard.isNotBlank() }
                Text("말한 문장 $spoken", style = T.labelM)
                Spacer(Modifier.width(24.dp))
                val ev = ui.events.lastOrNull()
                Text(ev?.textKo ?: "", style = T.labelM.copy(color = if (ev?.textKo?.contains("늦어") == true) P.negative else P.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class Ctx(val key: String, val icon: ImageVector, val title: String, val value: String, val color: Color = P.textPrimary)

/** 지금 상황 목록. 지금 주제를 고른 근거가 된 줄만 검은 바탕으로 표시한다. */
private fun contextData(ui: Ui): Pair<List<Ctx>, String?> {
    val d = ui.drive
    val rows = buildList {
        add(Ctx("dest", Icons.Outlined.Flag, "목적지", d.destinationName.ifBlank { "-" }))
        d.nextTurn?.let { t -> add(Ctx("turn", Icons.Outlined.Directions, "다음 안내", "${Gate.turnLabel(t.kind)} ${fmtDist(t.distanceM)}", if (t.distanceM <= Gate.HOLD_TURN_M) P.negative else P.textPrimary)) }
        d.nearestCaution?.let { c -> add(Ctx("caution", if (c.kind == Caution.SCHOOL_ZONE) Icons.Outlined.School else Icons.Outlined.Speed, "주의 지점", "${Gate.cautionLabel(c.kind)} ${fmtDist(c.distanceM)}", if (c.distanceM <= Gate.LISTEN_SCHOOL_M) P.negative else P.textPrimary)) }
        add(Ctx("traffic", Icons.Outlined.Traffic, "교통", when (d.traffic) { Traffic.HEAVY -> "많이 막힘"; Traffic.SLOW -> "서행"; Traffic.MODERATE -> "보통"; Traffic.FAST -> "원활"; else -> "-" }, if (d.traffic == Traffic.HEAVY) P.negative else P.textPrimary))
        add(Ctx("weather", if (d.wiperOn) Icons.Outlined.WaterDrop else Icons.Outlined.CloudQueue, "와이퍼", if (d.wiperOn) "작동 중 · 비" else "꺼짐"))
        if (d.turnSignal != TurnSignal.OFF) add(Ctx("signal", Icons.Outlined.Route, "방향지시등", if (d.turnSignal == TurnSignal.LEFT) "왼쪽" else "오른쪽", P.negative))
        if (d.hasPassenger) add(Ctx("passenger", Icons.Outlined.People, "동승자", if (d.passengerRear) "뒷좌석 탑승" else "앞좌석 탑승"))
        d.landmarks.firstOrNull()?.let { add(Ctx("landmark", Icons.Outlined.Place, "앞쪽 장소", "${it.name} ${fmtDist(it.distanceM)}")) }
        d.batteryPct?.let { add(Ctx("battery", Icons.Outlined.BatteryChargingFull, "배터리", "$it%", if (it < 30) P.negative else P.textPrimary)) }
    }
    val sel = when (ui.topic?.key) {
        "directions" -> "turn"; "school_zone", "road_signs" -> "caution"; "traffic" -> "traffic"; "weather_rain" -> "weather"
        "family_quiz" -> "passenger"; "landmark" -> "landmark"; "charging" -> "battery"
        "office_meeting", "shopping", "travel", "health" -> "dest"; else -> null
    }
    return rows to sel
}

@Composable
private fun ContextRows(ui: Ui, compact: Boolean) {
    val (rows, sel) = contextData(ui)
    rows.forEach { r ->
        NavRow(r.icon, r.title, r.value, selected = r.key == sel, valueColor = r.color)
        HDivider()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ContextCells(ui: Ui) {
    val (rows, sel) = contextData(ui)
    val show = (rows.filter { it.key == sel } + rows.filter { it.key != sel && it.key in setOf("turn", "caution", "traffic") }).take(3)
    show.forEachIndexed { i, r ->
        if (i > 0) VDivider()
        Box(Modifier.weight(1f)) { NavRow(r.icon, r.title, r.value, selected = r.key == sel, valueColor = r.color) }
    }
}

@Composable
private fun CoachPanel(ui: Ui, modifier: Modifier) {
    val g = ui.gate
    val holding = ui.stage == Stage.HOLD || g.window == Window.HOLD
    val item = ui.item
    Column(modifier.background(P.surface)) {
        // 말하기 상태 줄
        Row(Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, color, text) = when {
                holding -> Triple(Icons.Outlined.Timer, P.negative, "멈춤 · ${g.reasonKo}")
                g.window == Window.LISTEN_ONLY -> Triple(Icons.Outlined.Hearing, P.positive, g.reasonKo)
                ui.stage == Stage.LISTENING -> Triple(Icons.Outlined.Mic, P.active, "말해 보세요")
                ui.stage == Stage.TARGET -> Triple(Icons.Outlined.VolumeUp, P.textPrimary, "들어 보세요")
                ui.stage == Stage.FEEDBACK -> Triple(Icons.Outlined.GraphicEq, P.textPrimary, "피드백")
                ui.stage == Stage.QUIZ -> Triple(Icons.Outlined.People, P.textPrimary, "함께 생각해 보세요")
                else -> Triple(Icons.Outlined.RecordVoiceOver, P.textPrimary, g.reasonKo.ifBlank { "준비" })
            }
            Icon(icon, null, tint = color, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Text(text, style = T.titleS.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!holding && g.window != Window.LISTEN_ONLY && g.reasonKo.isNotBlank() && ui.stage != Stage.IDLE) {
                Text(g.reasonKo, style = T.labelM.copy(color = P.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
            }
        }
        HDivider()
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp)) {
            if (item == null) {
                Text("곧 시작할게요", style = T.titleM.copy(color = P.textSecondary))
            } else {
                val promptStage = ui.stage == Stage.PROMPT || ui.stage == Stage.QUIZ
                Text(item.promptKo, style = (if (promptStage && !holding) T.titleM else T.bodyL).copy(color = if (promptStage && !holding) P.textPrimary else P.textSecondary))
                Spacer(Modifier.height(24.dp))
                val showTarget = item.kind == ItemKind.REPEAT || item.kind == ItemKind.LISTEN || ui.stage == Stage.FEEDBACK || (item.kind == ItemKind.QUIZ && ui.stage == Stage.TARGET)
                if (showTarget || holding) {
                    Text(item.target, style = T.headlineM.copy(color = if (holding) P.textTertiary else P.textPrimary))
                    Spacer(Modifier.height(8.dp))
                    Text(item.hintKo, style = T.bodyL.copy(color = P.textSecondary))
                } else if (item.kind == ItemKind.RESPOND) {
                    Text("뜻: ${item.hintKo}", style = T.bodyL.copy(color = P.textTertiary))
                }
            }
        }
        HDivider()
        HeardBlock(ui)
    }
}

@Composable
private fun HeardBlock(ui: Ui) {
    val last = ui.last
    Column(Modifier.fillMaxWidth().background(P.basic50).padding(horizontal = 40.dp, vertical = 28.dp)) {
        when {
            ui.stage == Stage.LISTENING -> {
                StatusDot(P.active, if (ui.settings.inputMode == InputMode.DEMO || ui.sdk.stt != Link.CONNECTED) "듣는 중 · 시연용 답변" else "듣는 중 · Pleos 음성 인식")
                Spacer(Modifier.height(10.dp))
                Text(ui.heard.ifBlank { "…" }, style = T.titleM)
            }
            last != null && last.heard.isNotBlank() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("내가 말한 것", style = T.labelM.copy(color = P.textSecondary))
                    Spacer(Modifier.width(16.dp))
                    Text("일치도 ${(last.score * 100).toInt()}%", style = T.labelM.copy(color = scoreColor(last.score)))
                }
                Text(last.heard, style = T.bodyL)
                if (last.better.isNotBlank() && last.better != last.heard) {
                    Spacer(Modifier.height(14.dp))
                    Text("더 자연스럽게", style = T.labelM.copy(color = P.textSecondary))
                    Text(last.better, style = T.titleMBold.copy(color = P.positive))
                }
                if (last.commentKo.isNotBlank()) Text(last.commentKo, style = T.bodyS.copy(color = P.textSecondary), maxLines = 2)
            }
            else -> Text("운전 중에는 화면을 보지 않아도 돼요. 앱이 말로 안내합니다.", style = T.bodyM.copy(color = P.textSecondary))
        }
    }
}

// ============================== 도착 ==============================

@Composable
fun ArrivalScreen(ui: Ui, vm: AppModel) {
    val spoken = ui.todayAttempts.filter { it.item.kind != ItemKind.LISTEN && it.heard.isNotBlank() }
    val cards = ui.todayAttempts.filter { it.item.kind != ItemKind.LISTEN }.groupBy { it.item.target }.map { (_, v) -> v.maxBy { it.at } }
    val avg = if (spoken.isEmpty()) 0f else spoken.map { it.score }.average().toFloat()
    val weak = cards.filter { it.score < 0.8f }.sortedBy { it.score }
    Column(Modifier.fillMaxSize()) {
        PageNav("로드토크", APP_ICON, subtitle = "도착 · 오늘 수업 정리") { NavAction("처음으로") { vm.backToStart() } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth.value >= WIDE_DP
            val stats = listOf(
                Triple(Icons.Outlined.Timer, "수업 시간", fmtMin(ui.elapsedSec)),
                Triple(Icons.Outlined.RecordVoiceOver, "말한 문장", "${spoken.size}"),
                Triple(Icons.Outlined.TrendingUp, "평균 일치도", "${(avg * 100).toInt()}%"),
                Triple(Icons.Outlined.Translate, "새 표현", "${cards.size}"),
                Triple(Icons.Outlined.Replay, "다시 볼 표현", "${weak.size}"),
            )
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    Column(Modifier.width(420.dp).fillMaxHeight().background(P.surface)) {
                        stats.forEach { (i, t, v) -> NavRow(i, t, v); HDivider() }
                        ui.events.lastOrNull { it.textKo.contains("늦어") }?.let {
                            Text(it.textKo, style = T.bodyS.copy(color = P.textSecondary), modifier = Modifier.padding(24.dp))
                        }
                    }
                    VDivider()
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight().background(P.surface)) {
                    if (!wide) item {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            stats.take(3).forEachIndexed { i, (ic, t, v) -> if (i > 0) VDivider(); Box(Modifier.weight(1f)) { NavRow(ic, t, v) } }
                        }
                        HDivider()
                    }
                    item {
                        Text("오늘의 표현 · 눌러서 다시 듣기", style = T.labelM.copy(color = P.textSecondary), modifier = Modifier.padding(start = 40.dp, top = 28.dp, bottom = 12.dp))
                        HDivider()
                    }
                    items(cards.sortedByDescending { it.at }) { a -> ExpressionRow(a) { vm.replay(a) }; HDivider() }
                    if (cards.isEmpty()) item { Text("오늘은 말한 문장이 없어요.", style = T.bodyL.copy(color = P.textSecondary), modifier = Modifier.padding(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExpressionRow(a: Attempt, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 40.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(a.better.ifBlank { a.item.target }, style = T.titleS)
            Text(a.item.hintKo, style = T.bodyS.copy(color = P.textSecondary))
            if (a.heard.isNotBlank()) Text("내가 말한 것: ${a.heard}", style = T.labelM.copy(color = P.textTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.placeLabel.isNotBlank()) Text("배운 곳: ${a.placeLabel}", style = T.labelM.copy(color = P.positive), maxLines = 1)
        }
        Spacer(Modifier.width(24.dp))
        Column(horizontalAlignment = Alignment.End) {
            Icon(Icons.Outlined.VolumeUp, null, tint = P.textPrimary, modifier = Modifier.size(36.dp))
            Text(if (a.heard.isBlank()) "듣기" else "${(a.score * 100).toInt()}%", style = T.bodyL.copy(color = scoreColor(a.score)))
        }
    }
}

// ============================== 설정 ==============================

private enum class SettingSection(val ko: String, val icon: ImageVector) {
    LANG("언어 · 수준", Icons.Outlined.Language), VOICE("음성 입력", Icons.Outlined.Mic), PASSENGER("동승자 모드", Icons.Outlined.People),
    ONLINE("온라인 코치", Icons.Outlined.GraphicEq), DEMO("데모 주행", Icons.Outlined.DirectionsCar), SDK("SDK 연결", Icons.Outlined.Directions), RECORD("학습 기록", Icons.Outlined.Delete),
}

@Composable
fun SettingsScreen(ui: Ui, vm: AppModel) {
    var section by remember { mutableStateOf(SettingSection.LANG) }
    val s = ui.settings
    Column(Modifier.fillMaxSize()) {
        PageNav("로드토크", APP_ICON, subtitle = "설정") { NavAction("완료") { vm.go(Screen.START) } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth.value >= WIDE_DP
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(if (wide) 420.dp else 300.dp).fillMaxHeight().background(P.surface).verticalScroll(rememberScrollState())) {
                    SettingSection.entries.forEach { sec -> NavRow(sec.icon, sec.ko, selected = sec == section) { section = sec } }
                }
                VDivider()
                Column(Modifier.weight(1f).fillMaxHeight().background(P.surface).verticalScroll(rememberScrollState()).padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(section.ko, style = T.titleMBold)
                    when (section) {
                        SettingSection.LANG -> {
                            Label("연습할 언어")
                            Segmented(Lang.entries.map { it to it.label }, s.lang, Modifier.fillMaxWidth()) { l -> vm.updateSettings { it.copy(lang = l) } }
                            Label("수준")
                            Segmented(listOf("beginner" to "입문", "intermediate" to "중급", "advanced" to "고급"), s.level, Modifier.fillMaxWidth()) { k -> vm.updateSettings { it.copy(level = k) } }
                            Note("모두 Pleos 음성 SDK가 지원하는 언어예요(en-US, es-MX, fr-CA).")
                        }
                        SettingSection.VOICE -> {
                            Segmented(InputMode.entries.map { it to it.ko }, s.inputMode, Modifier.fillMaxWidth()) { m -> vm.updateSettings { it.copy(inputMode = m) } }
                            Note("음성 인식은 Pleos SpeechToText SDK(차 안 엔진)로만 합니다. 마이크 소리는 차 밖으로 보내지 않고, 첨삭에는 인식된 글자만 씁니다.")
                        }
                        SettingSection.PASSENGER -> {
                            ToggleRow("동승자 모드 자동 전환", s.familyMode) { v -> vm.updateSettings { it.copy(familyMode = v) } }
                            Note("좌석 착석·안전벨트 정보로 동승자를 알아채고 함께 하는 퀴즈로 바꿔요.")
                        }
                        SettingSection.ONLINE -> {
                            ToggleRow("온라인 코치 사용", s.onlineCoach) { v -> vm.updateSettings { it.copy(onlineCoach = v) } }
                            Note("상황에 맞는 문장 만들기, 첨삭, 앞쪽 장소 찾기를 서버에서 해요. 연결이 끊기면 앱에 넣어 둔 문장으로 계속해요.")
                            Note("서버: ${ai.roadtalk.BuildConfig.SERVER_URL}")
                            Note("장소 정보 © OpenStreetMap contributors")
                        }
                        SettingSection.DEMO -> {
                            Label("시나리오")
                            Segmented(Scenario.entries.map { it to it.ko.take(6) }, s.scenario, Modifier.fillMaxWidth()) { sc -> vm.updateSettings { it.copy(scenario = sc) } }
                            Text(s.scenario.desc, style = T.bodyM.copy(color = P.textSecondary))
                            Label("재생 속도")
                            Segmented(listOf(1.0 to "1배", 4.0 to "4배", 8.0 to "8배", 15.0 to "15배"), s.speedup, Modifier.fillMaxWidth()) { x -> vm.updateSettings { it.copy(speedup = x) } }
                        }
                        SettingSection.SDK -> SdkTiles(ui, columns = if (wide) 3 else 2)
                        SettingSection.RECORD -> {
                            Note("배운 표현과 장소는 이 차 안에만 저장돼요.")
                            LineButton("학습 기록 지우기") { vm.clearRecord() }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Label(t: String) = Text(t, style = T.labelM.copy(color = P.textSecondary))
@Composable private fun Note(t: String) = Text(t, style = T.bodyS.copy(color = P.textSecondary))

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(100.dp).border(1.dp, P.divider).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.titleS, modifier = Modifier.weight(1f))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = P.selected, checkedThumbColor = P.basic00, uncheckedTrackColor = P.basic200, uncheckedThumbColor = P.basic00, uncheckedBorderColor = P.basic200),
        )
    }
}

fun scoreColor(s: Float) = when { s >= 0.85f -> P.positive; s >= 0.6f -> P.textSecondary; else -> P.negative }
