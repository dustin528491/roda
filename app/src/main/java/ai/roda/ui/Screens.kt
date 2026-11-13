package ai.roda.ui

import ai.roda.AppModel
import ai.roda.InputMode
import ai.roda.RouteMark
import ai.roda.Screen
import ai.roda.Stage
import ai.roda.Ui
import ai.roda.car.Scenario
import ai.roda.core.ItemKind
import ai.roda.core.Lang
import ai.roda.core.Link
import ai.roda.lesson.Phase
import ai.roda.lesson.Window
import ai.roda.lesson.fmtMin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun App(vm: AppModel) {
    val ui by vm.ui.collectAsState()
    RodaTheme {
        Box(Modifier.fillMaxSize().background(P.surface)) {
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

@Composable
private fun Sub(text: String, dim: String? = null) {
    Text(text, style = T.titleS, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (dim != null) Text(" · $dim", style = T.titleS.copy(color = P.textSecondary), maxLines = 1)
}

// ============================== 출발 ==============================

@Composable
fun StartScreen(ui: Ui, vm: AppModel) {
    val d = ui.drive
    val hasRoute = d.routeActive && d.remainSec > 0
    val total = if (hasRoute) d.remainSec else 41 * 60
    val warm = (total * 0.08).toInt().coerceIn(120, 240)
    val review = (total * 0.12).toInt().coerceIn(180, 360)
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header(APP_ICON, subtitle = {
            if (hasRoute) Sub("내비 경로")
            else Box {
                Row(Modifier.pressable { menu = true }, verticalAlignment = Alignment.CenterVertically) {
                    Sub(ui.settings.scenario.ko)
                    Icon(Icons.Outlined.KeyboardArrowDown, "시나리오 선택", tint = P.textSecondary, modifier = Modifier.size(36.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = P.surface) {
                    Scenario.entries.forEach { s ->
                        DropdownMenuItem(text = { Column(Modifier.padding(vertical = 10.dp)) { Text(s.ko, style = T.bodyL); Text(s.desc, style = T.labelM.copy(color = P.textSecondary)) } },
                            onClick = { vm.updateSettings { it.copy(scenario = s) }; menu = false })
                    }
                }
            }
        }) { ChipButton("설정") { vm.go(Screen.SETTINGS) } }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(40.dp))
            Text("출퇴근길, 나만의 ${ui.settings.lang.ko} 교실", style = T.titleMBold.copy(color = P.accentText), textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Text("${if (hasRoute) d.destinationName else ui.settings.scenario.dest}까지", style = T.titleS.copy(color = P.textSecondary), textAlign = TextAlign.Center)
            Text(fmtMin(total), style = T.display)
            Text("워밍업 ${fmtMin(warm)} · 대화 ${fmtMin(total - warm - review)} · 복습 ${fmtMin(review)}", style = T.titleS.copy(color = P.textSecondary), textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            PillChoices(Lang.entries.map { it to it.label }, ui.settings.lang) { l -> vm.updateSettings { it.copy(lang = l) } }
            Spacer(Modifier.height(48.dp))
            PrimaryButton("시작") { vm.startDrive(useDemo = !hasRoute) }
            Spacer(Modifier.height(36.dp))
            Text((if (hasRoute) "" else "데모 주행 · ") + "지나는 길이 오늘의 교재예요. 운전이 바쁠 땐 멈추고, 길이 열리면 말을 걸어요.", style = T.bodyS.copy(color = P.textTertiary), textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ============================== 주행 중 ==============================

@Composable
fun DriveScreen(ui: Ui, vm: AppModel) {
    val d = ui.drive
    Column(Modifier.fillMaxSize()) {
        Header(APP_ICON, subtitle = { Sub(if (ui.demo) ui.settings.scenario.ko else d.destinationName.ifBlank { "주행 중" }, ui.settings.lang.label) }) {
            Text("도착까지 ", style = T.bodyM.copy(color = P.textSecondary))
            Text(fmtMin(d.remainSec), style = T.bodyLStrong)
            Spacer(Modifier.width(24.dp))
            if (ui.demo) { ChipButton("+1분") { vm.skipDemo(60) }; Spacer(Modifier.width(12.dp)) }
            ChipButton("끝내기") { vm.endDrive() }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val narrow = maxWidth.value < 1100
            Column(Modifier.fillMaxSize().padding(horizontal = if (narrow) 40.dp else 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Coach(ui, narrow)
            }
        }
        RouteStrip(ui)
    }
}

@Composable
private fun Coach(ui: Ui, narrow: Boolean) {
    val item = ui.item
    val g = ui.gate
    val holding = ui.stage == Stage.HOLD || g.window == Window.HOLD
    val sentence = if (narrow) T.sentenceS else T.sentence
    if (item == null) {
        StatePill("곧 시작할게요", P.holdBg, P.holdFg)
        return
    }
    if (holding) {
        Text(item.target, style = sentence.copy(color = P.basic300), textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        StatePill(if (g.window == Window.HOLD) g.reasonKo.ifBlank { "잠시 멈췄어요" } else "이어서 할게요", P.holdBg, P.holdFg)
        return
    }
    val last = ui.last?.takeIf { it.item.id == item.id && it.heard.isNotBlank() }
    val feedback = ui.stage == Stage.FEEDBACK && last != null
    // 지금 상황(할 수 있게 되는 일)과 고른 이유, 차례 표시
    val sc = ui.scene
    Row(Modifier.clip(RoundedCornerShape(50)).background(P.panel).padding(horizontal = 26.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(P.accent))
        Spacer(Modifier.width(12.dp))
        val why = when (item.source) { "place" -> "이 길에서 배웠던 표현"; "review" -> "오늘 복습"; else -> ui.topic?.why ?: "" }
        Text(listOfNotNull(sc?.canDoKo?.takeIf { it.isNotBlank() && item.source != "review" }, why.takeIf { it.isNotBlank() }).joinToString(" · "), style = T.bodyS.copy(color = P.basic600), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (ui.turnTotal > 1) {
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(ui.turnTotal) { i -> Box(Modifier.size(if (i == ui.turnIdx) 30.dp else 14.dp, 14.dp).clip(RoundedCornerShape(7.dp)).background(if (i < ui.turnIdx) P.basic900 else if (i == ui.turnIdx) P.accent else P.basic200)) }
        }
    }
    Spacer(Modifier.height(34.dp))
    val showTarget = feedback || item.kind == ItemKind.REPEAT || item.kind == ItemKind.LISTEN || (item.kind == ItemKind.QUIZ && ui.stage == Stage.TARGET)
    if (showTarget) {
        Text(if (feedback) last!!.better.ifBlank { item.target } else item.target, style = sentence, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(item.hintKo, style = T.bodyL.copy(color = P.textTertiary), textAlign = TextAlign.Center)
    } else {
        Text(item.promptKo, style = T.titleM.copy(color = P.basic600), textAlign = TextAlign.Center)
    }
    if (feedback) {
        Spacer(Modifier.height(40.dp))
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(P.panel).padding(horizontal = 44.dp, vertical = 30.dp)) {
            Text("내가 말한 것 · 일치도 ${(last!!.score * 100).toInt()}%", style = T.labelM.copy(color = P.textSecondary))
            Text(last.heard, style = T.bodyL.copy(color = P.textSecondary))
            if (last.commentKo.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(last.commentKo, style = T.bodyLStrong.copy(color = P.accentText), maxLines = 2)
            }
        }
        return
    }
    Spacer(Modifier.height(48.dp))
    when {
        g.window == Window.LISTEN_ONLY -> StatePill("어린이 보호구역 · 듣기만 하세요", P.speakBg, P.speakFg, Icons.Outlined.Hearing)
        ui.stage == Stage.LISTENING -> {
            StatePill("듣는 중", P.listenBg, P.listenFg, Icons.Outlined.GraphicEq)
            Spacer(Modifier.height(24.dp))
            Text(ui.heard.ifBlank { " " }, style = T.titleM.copy(color = P.basic600), textAlign = TextAlign.Center, maxLines = 2)
        }
        ui.stage == Stage.QUIZ -> StatePill("함께 생각해 보세요", P.speakBg, P.speakFg, Icons.Outlined.People)
        else -> StatePill("들어 보세요", P.speakBg, P.speakFg, Icons.Outlined.VolumeUp)
    }
}

/** 길 그림: 선 하나에 지금 위치, 앞의 안내 지점, 수업 단계(워밍업·본 대화·복습)를 함께 보여 준다. */
@Composable
private fun RouteStrip(ui: Ui) {
    val p = ui.plan ?: return
    val total = ui.planTotalSec.coerceAtLeast(1).toFloat()
    val warmF = p.warmupSec / total
    val reviewF = 1f - p.reviewSec / total
    val now = ui.routeFrac
    BoxWithConstraints(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 60.dp)) {
        val w = maxWidth
        fun x(f: Float): Dp = w * f.coerceIn(0f, 1f)
        // 수업 단계 이름
        listOf(Triple(Phase.WARMUP, "워밍업", 0f), Triple(Phase.MAIN, "본 대화", warmF), Triple(Phase.REVIEW, "복습", reviewF)).forEach { (ph, name, f) ->
            val on = ui.phase == ph
            Text(name, style = T.labelS.copy(color = if (on) P.textPrimary else P.textTertiary, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
                modifier = Modifier.offset(x = if (f > 0.85f) x(f) - 20.dp else x(f), y = 14.dp))
        }
        // 선
        Box(Modifier.offset(y = 62.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.basic100))
        Box(Modifier.offset(y = 62.dp).width(x(now)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.basic900))
        // 예전에 말할 수 있게 된 구간(옅게)과 오늘 영어로 말하며 지나온 구간(진하게)
        ui.known.forEach { b -> Box(Modifier.offset(x = x(b / 100f), y = 76.dp).width(w * 0.011f).height(8.dp).background(P.accentSoft)) }
        ui.painted.forEach { (a0, a1) -> Box(Modifier.offset(x = x(a0), y = 76.dp).width((x(a1) - x(a0)).coerceAtLeast(4.dp)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.accent)) }
        // 단계 경계 눈금
        listOf(warmF, reviewF).forEach { f -> Box(Modifier.offset(x = x(f), y = 56.dp).width(2.dp).height(20.dp).background(P.basic300)) }
        // 안내 지점
        ui.marks.forEach { m: RouteMark ->
            val passed = m.frac <= now
            Box(Modifier.offset(x = x(m.frac) - 14.dp, y = 52.dp).size(28.dp).clip(CircleShape).background(P.surface).border(4.dp, if (m.next) P.accent else if (passed) P.basic900 else P.basic400, CircleShape))
            Text(m.label, style = T.labelS.copy(color = if (m.next) P.accentText else P.textSecondary, fontWeight = if (m.next) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
                maxLines = 1, modifier = Modifier.offset(x = (x(m.frac) - 50.dp).coerceIn(0.dp, w - 110.dp), y = 100.dp).width(110.dp), textAlign = TextAlign.Center)
        }
        // 지금 위치
        Box(Modifier.offset(x = x(now) - 22.dp, y = 44.dp).size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(P.surface).padding(6.dp).clip(CircleShape).background(P.basic900))
        // 수업이 바뀐 순간(예: 막혀서 대화 추가)
        ui.events.lastOrNull()?.takeIf { it.atElapsedSec > 0 && ui.elapsedSec - it.atElapsedSec < 240 }?.let {
            Text(it.textKo, style = T.labelS.copy(color = P.accentText), maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        }
    }
}

// ============================== 도착 ==============================

fun coverage(ui: Ui): Int {
    val cells = BooleanArray(100)
    ui.known.forEach { if (it in 0..99) cells[it] = true }
    ui.painted.forEach { (a, b) -> for (i in (a * 100).toInt().coerceIn(0, 99)..(b * 100).toInt().coerceIn(0, 99)) cells[i] = true }
    return cells.count { it }
}

private fun fmtDur(sec: Int) = if (sec >= 60) "${sec / 60}분 ${sec % 60}초" else "${sec}초"

@Composable
fun ArrivalScreen(ui: Ui, vm: AppModel) {
    val share = if (ui.userSpeakSec + ui.appSpeakSec == 0) 0 else ui.userSpeakSec * 100 / (ui.userSpeakSec + ui.appSpeakSec)
    val cov = coverage(ui)
    val done = ui.canDos.sortedByDescending { it.score }
    Column(Modifier.fillMaxSize()) {
        Header(APP_ICON, subtitle = { Sub("오늘 수업") }) { ChipButton("닫기") { vm.backToStart() } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cols = if (maxWidth.value < 1100) 1 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxSize().padding(horizontal = if (cols == 1) 32.dp else 170.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(cols) }) {
                    Column(Modifier.fillMaxWidth().padding(top = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("오늘 ${ui.settings.lang.ko}로 말한 시간", style = T.titleS.copy(color = P.textSecondary))
                        Text(fmtDur(ui.userSpeakSec), style = T.display, textAlign = TextAlign.Center)
                        Text("대화의 ${share}%를 내가 말했어요 · ${ui.userTurns}번", style = T.titleS.copy(color = P.textSecondary), textAlign = TextAlign.Center)
                        if (ui.demo && ui.lessonRealSec > 0) Text("데모 주행은 ${ui.settings.speedup.toInt()}배속이라 실제 수업은 ${fmtDur(ui.lessonRealSec)}였어요 · 같은 속도라면 ${ui.planTotalSec / 60}분 길에 약 ${(ui.userSpeakSec.toLong() * ui.planTotalSec / ui.lessonRealSec / 60).toInt()}분", style = T.labelM.copy(color = P.textTertiary), modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item(span = { GridItemSpan(cols) }) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(P.panel).padding(horizontal = 36.dp, vertical = 26.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("내 출근길 영어 지도", style = T.bodyLStrong, modifier = Modifier.weight(1f))
                            Text("$cov%", style = T.titleMBold.copy(color = P.accentText))
                        }
                        Text("이 길의 ${cov}%를 ${ui.settings.lang.ko}로 말하며 왔어요. 같은 길을 다시 지나면 그 자리에서 다시 물어요.", style = T.labelM.copy(color = P.textSecondary))
                        Spacer(Modifier.height(18.dp))
                        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
                            val w = maxWidth
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(P.basic200))
                            ui.known.forEach { b -> Box(Modifier.offset(x = w * (b / 100f)).width(w * 0.011f).fillMaxHeight().background(P.accentSoft)) }
                            ui.painted.forEach { (a0, a1) -> Box(Modifier.offset(x = w * a0.coerceIn(0f, 1f)).width((w * (a1 - a0).coerceIn(0f, 1f)).coerceAtLeast(4.dp)).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(P.accent)) }
                        }
                    }
                }
                item(span = { GridItemSpan(cols) }) {
                    Text("오늘 말할 수 있게 된 상황 ${done.count { it.score >= 0.8f }}가지", style = T.bodyLStrong, modifier = Modifier.padding(top = 10.dp, start = 8.dp))
                }
                items(done) { c -> CanDoCard(c) { vm.replayText(c.sentence) } }
                item(span = { GridItemSpan(cols) }) { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanDoCard(c: ai.roda.CanDo, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(P.panel).pressable(onClick).padding(horizontal = 32.dp, vertical = 26.dp)) {
        Text(c.labelKo, style = T.bodyLStrong.copy(color = P.accentText))
        Spacer(Modifier.height(6.dp))
        Text(c.sentence, style = T.bodyLStrong)
        Text(c.hintKo, style = T.labelM.copy(color = P.textSecondary))
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.score < 0.8f) Tag("내일 이 길에서 다시", accent = true)
            Tag(listOfNotNull(c.place.take(14).ifBlank { null }, "${c.turns}번 말함", "${(c.score * 100).toInt()}%").joinToString(" · "))
        }
    }
}

// ============================== 설정 ==============================

@Composable
fun SettingsScreen(ui: Ui, vm: AppModel) {
    val s = ui.settings
    Column(Modifier.fillMaxSize()) {
        Header(APP_ICON, subtitle = { Sub("설정") }) { ChipButton("완료") { vm.go(Screen.START) } }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel("연습할 언어와 수준") {
                PillChoices(Lang.entries.map { it to it.label }, s.lang) { l -> vm.updateSettings { it.copy(lang = l) } }
                Spacer(Modifier.height(16.dp))
                PillChoices(listOf("beginner" to "입문", "intermediate" to "중급", "advanced" to "고급"), s.level) { k -> vm.updateSettings { it.copy(level = k) } }
            }
            Panel("음성 입력", "음성 인식은 Pleos SpeechToText SDK(차 안 엔진)로만 합니다. 마이크 소리는 차 밖으로 보내지 않고, 첨삭에는 인식된 글자만 씁니다.") {
                PillChoices(InputMode.entries.map { it to it.ko }, s.inputMode) { m -> vm.updateSettings { it.copy(inputMode = m) } }
            }
            Panel("수업 방식") {
                ToggleRow("동승자가 타면 함께 하는 퀴즈로", s.familyMode) { v -> vm.updateSettings { it.copy(familyMode = v) } }
                ToggleRow("온라인 코치 (상황 맞춤 문장·첨삭·앞쪽 장소)", s.onlineCoach) { v -> vm.updateSettings { it.copy(onlineCoach = v) } }
            }
            Panel("데모 주행 속도") {
                PillChoices(listOf(1.0 to "1배", 4.0 to "4배", 8.0 to "8배", 15.0 to "15배"), s.speedup) { x -> vm.updateSettings { it.copy(speedup = x) } }
            }
            Panel("Pleos Connect SDK 연결") {
                val k = ui.sdk
                listOf("NaviHelper · 회전 안내·도착 시간" to k.navi, "FusedLocation · 위치·같은 길 복습" to k.location, "Vehicle · 방향지시등·와이퍼·좌석" to k.vehicle, "SpeechToText · 음성 인식" to k.stt, "TextToSpeech · 음성 출력" to k.tts).forEach { (name, link) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = T.bodyM, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Tag(when (link) { Link.CONNECTED -> "연결됨"; Link.CONNECTING -> "연결 중"; Link.DEMO -> "데모"; Link.UNAVAILABLE -> "없음" }, accent = link == Link.CONNECTED)
                    }
                }
            }
            Panel("학습 기록", "배운 표현과 장소는 이 차 안에만 저장돼요. 장소 정보 © OpenStreetMap contributors") {
                ChipButton("학습 기록 지우기") { vm.clearRecord() }
            }
        }
    }
}

@Composable
private fun Panel(title: String, note: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(P.panel).padding(horizontal = 36.dp, vertical = 30.dp)) {
        Text(title, style = T.bodyLStrong)
        Spacer(Modifier.height(18.dp))
        content()
        if (note != null) { Spacer(Modifier.height(16.dp)); Text(note, style = T.labelM.copy(color = P.textSecondary)) }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.bodyM, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = P.accent, checkedThumbColor = P.basic00, uncheckedTrackColor = P.basic200, uncheckedThumbColor = P.basic00, uncheckedBorderColor = P.basic200))
    }
}
