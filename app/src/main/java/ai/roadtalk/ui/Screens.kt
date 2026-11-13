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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App(vm: AppModel) {
    val ui by vm.ui.collectAsState()
    RoadTalkTheme {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            when (ui.screen) {
                Screen.START -> StartScreen(ui, vm)
                Screen.DRIVE -> DriveScreen(ui, vm)
                Screen.ARRIVAL -> ArrivalScreen(ui, vm)
                Screen.SETTINGS -> SettingsScreen(ui, vm)
            }
        }
    }
}

// =========================== 출발 화면 ===========================

@Composable
fun StartScreen(ui: Ui, vm: AppModel) {
    val d = ui.drive
    val hasRoute = d.routeActive && d.remainSec > 0
    val total = if (hasRoute) d.remainSec else 41 * 60
    val warm = (total * 0.08).toInt().coerceIn(120, 240)
    val review = (total * 0.12).toInt().coerceIn(180, 360)
    Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.35f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("로드토크", color = C.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Text("RoadTalk · 출퇴근길 회화 코치", color = C.sub, fontSize = 18.sp)
            }
            Spacer(Modifier.height(18.dp))
            Card {
                Text(if (hasRoute) "오늘 가는 길" else "데모 주행 · ${ui.settings.scenario.ko}", color = C.sub, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (hasRoute) d.destinationName else ui.settings.scenario.desc, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtMin(total), color = C.accent, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("동안 이렇게 할게요", color = C.sub, fontSize = 20.sp, modifier = Modifier.padding(bottom = 14.dp))
                }
                Spacer(Modifier.height(8.dp))
                PlanBar(total, warm, review, progress = null)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Legend(C.blue, "워밍업 ${fmtMin(warm)}"); Legend(C.accent, "본 대화 ${fmtMin(total - warm - review)}"); Legend(C.purple, "복습 ${fmtMin(review)}")
                }
                Spacer(Modifier.height(10.dp))
                Text("막히면 대화를 더하고, 목적지 2km 전부터는 오늘 틀린 표현만 다시 물어요.", color = C.dim, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("연습할 언어", color = C.sub, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Lang.entries.forEach { l -> Choice(l.label, ui.settings.lang == l) { vm.updateSettings { it.copy(lang = l) } } }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = { vm.startDrive(useDemo = !hasRoute) },
                    modifier = Modifier.height(72.dp).weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text(if (hasRoute) "출발하며 시작" else "데모 주행으로 시작", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF04201A)) }
                OutlinedButton(onClick = { vm.go(Screen.SETTINGS) }, modifier = Modifier.height(72.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("설정", fontSize = 20.sp, color = C.text)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                Text("데모 시나리오", color = C.sub, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Scenario.entries.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.updateSettings { it.copy(scenario = s) } }
                            .background(if (ui.settings.scenario == s) C.accent.copy(alpha = 0.12f) else Color.Transparent).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(if (ui.settings.scenario == s) C.accent else C.line)
                        Column {
                            Text(s.ko, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.desc, color = C.sub, fontSize = 14.sp)
                        }
                    }
                }
            }
            Card { SdkPanel(ui) }
        }
    }
}

@Composable
fun SdkPanel(ui: Ui) {
    Text("Pleos Connect SDK 연결", color = C.sub, fontSize = 16.sp)
    Spacer(Modifier.height(6.dp))
    val s = ui.sdk
    SdkRow("내비 NaviHelper", s.navi, "회전·주의 지점·도착 시간")
    SdkRow("위치 FusedLocation", s.location, "진행 방향·같은 길 복습")
    SdkRow("차량 Vehicle", s.vehicle, "와이퍼·방향지시등·기어·좌석")
    SdkRow("음성 인식 SpeechToText", s.stt, "차 안 엔진(ON_DEVICE)")
    SdkRow("음성 출력 TextToSpeech", s.tts, "원어·한국어 전환")
}

@Composable
private fun SdkRow(name: String, l: Link, what: String) {
    val (c, t) = when (l) {
        Link.CONNECTED -> C.accent to "연결됨"; Link.CONNECTING -> C.sub to "연결 중"
        Link.DEMO -> C.blue to "데모"; Link.UNAVAILABLE -> C.dim to "없음"
    }
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(c)
        Text(name, color = C.text, fontSize = 15.sp, modifier = Modifier.width(210.dp))
        Text(t, color = c, fontSize = 14.sp, modifier = Modifier.width(64.dp))
        Text(what, color = C.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =========================== 주행 중 화면 ===========================

@Composable
fun DriveScreen(ui: Ui, vm: AppModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        TopBar(ui, vm)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ContextPanel(ui, Modifier.width(300.dp).fillMaxHeight())
            CoachCard(ui, Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(14.dp))
        BottomBar(ui)
    }
}

@Composable
private fun TopBar(ui: Ui, vm: AppModel) {
    val d = ui.drive
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("로드토크", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Pill(ui.phase.ko, phaseColor(ui.phase), filled = true, size = 16)
        Spacer(Modifier.width(10.dp))
        ui.topic?.let { Pill(it.ko, C.text, size = 15) }
        Spacer(Modifier.width(10.dp))
        Pill(ui.settings.lang.label, C.blue, size = 15)
        if (ui.demo) { Spacer(Modifier.width(10.dp)); Pill("데모 주행", C.sub, size = 14) }
        if (!ui.serverOnline) { Spacer(Modifier.width(10.dp)); Pill("오프라인 문장", C.dim, size = 14) }
        Spacer(Modifier.weight(1f))
        Text("도착까지 ", color = C.sub, fontSize = 18.sp)
        Text(fmtMin(d.remainSec), color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(" · ${fmtDist(d.remainM)}", color = C.sub, fontSize = 18.sp)
        if (ui.demo) {
            Spacer(Modifier.width(14.dp))
            SmallBtn("+1분") { vm.skipDemo(60) }
        }
        Spacer(Modifier.width(8.dp))
        SmallBtn("끝내기") { vm.endDrive() }
    }
}

@Composable
private fun ContextPanel(ui: Ui, modifier: Modifier) {
    val d = ui.drive
    Column(modifier.background(C.panel, RoundedCornerShape(20.dp)).padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("지금 상황", color = C.sub, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        ui.topic?.let {
            Text("주제를 고른 이유", color = C.dim, fontSize = 13.sp)
            Text(it.why, color = C.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
        }
        d.nextTurn?.let { t ->
            Signal("다음 안내", "${Gate.turnLabel(t.kind)} ${fmtDist(t.distanceM)}", if (t.distanceM <= Gate.HOLD_TURN_M) C.amber else C.text, t.description)
        }
        d.nearestCaution?.let { c -> Signal("주의 지점", "${Gate.cautionLabel(c.kind)} ${fmtDist(c.distanceM)}", if (c.kind == Caution.SCHOOL_ZONE) C.warm else C.text) }
        Signal("교통", when (d.traffic) { Traffic.HEAVY -> "많이 막힘"; Traffic.SLOW -> "서행"; Traffic.MODERATE -> "보통"; Traffic.FAST -> "원활"; else -> "-" },
            if (d.traffic == Traffic.HEAVY) C.amber else C.text)
        if (d.wiperOn) Signal("와이퍼", "작동 중 · 비", C.blue)
        if (d.turnSignal != TurnSignal.OFF) Signal("방향지시등", if (d.turnSignal == TurnSignal.LEFT) "왼쪽" else "오른쪽", C.amber)
        if (d.hasPassenger) Signal("동승자", if (d.passengerRear) "뒷좌석 탑승" else "앞좌석 탑승", C.purple)
        d.landmarks.firstOrNull()?.let { Signal("앞쪽 장소", "${it.name} ${fmtDist(it.distanceM)}") }
        d.batteryPct?.let { Signal("배터리", "$it%", if (it < 30) C.amber else C.text) }
        if (d.speedLimit > 0) Signal("제한속도", "${d.speedLimit}km/h")
    }
}

@Composable
private fun Signal(label: String, value: String, color: Color = C.text, sub: String = "") {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, color = C.dim, fontSize = 13.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (sub.isNotBlank()) Text(sub, color = C.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CoachCard(ui: Ui, modifier: Modifier) {
    val g = ui.gate
    val holding = ui.stage == Stage.HOLD || g.window == Window.HOLD
    val border = when {
        holding -> C.amber
        g.window == Window.LISTEN_ONLY -> C.warm
        ui.stage == Stage.LISTENING -> C.accent
        ui.phase == Phase.REVIEW -> C.purple
        else -> C.line
    }
    Column(modifier.background(C.panel, RoundedCornerShape(24.dp)).border(2.dp, border, RoundedCornerShape(24.dp)).padding(28.dp)) {
        GateBanner(ui)
        Spacer(Modifier.height(16.dp))
        val item = ui.item
        AnimatedContent(targetState = Triple(item?.id, holding, ui.stage == Stage.PROMPT || ui.stage == Stage.QUIZ), transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "card") { (_, hold, promptStage) ->
            Column(Modifier.fillMaxWidth()) {
                if (item == null) {
                    Text("곧 시작할게요", color = C.sub, fontSize = 30.sp)
                } else if (hold) {
                    Text("잠시 멈췄어요", color = C.amber, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(item.target, color = C.dim, fontSize = 48.sp, fontWeight = FontWeight.SemiBold, lineHeight = 58.sp)
                } else {
                    Text(item.promptKo, color = if (promptStage) C.text else C.sub, fontSize = if (promptStage) 34.sp else 24.sp, lineHeight = if (promptStage) 44.sp else 32.sp)
                    Spacer(Modifier.height(14.dp))
                    val showTarget = item.kind == ItemKind.REPEAT || item.kind == ItemKind.LISTEN || ui.stage == Stage.FEEDBACK || (item.kind == ItemKind.QUIZ && ui.stage == Stage.TARGET)
                    if (showTarget) {
                        Text(item.target, color = C.text, fontSize = 66.sp, fontWeight = FontWeight.Bold, lineHeight = 78.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(item.hintKo, color = C.sub, fontSize = 28.sp)
                    } else if (item.kind == ItemKind.RESPOND) {
                        Text("뜻: ${item.hintKo}", color = C.dim, fontSize = 24.sp)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        HeardAndFeedback(ui)
    }
}

@Composable
private fun GateBanner(ui: Ui) {
    val g = ui.gate
    val (c, title) = when (g.window) {
        Window.HOLD -> C.amber to "멈춤"
        Window.LISTEN_ONLY -> C.warm to "듣기만"
        Window.SHORT -> C.blue to "짧게"
        Window.LONG -> C.accent to "대화"
        Window.PARKED -> C.purple to "정차"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(title, c, filled = g.window == Window.HOLD, size = 20)
        Spacer(Modifier.width(12.dp))
        Text(g.reasonKo, color = c, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(ui.stage.ko, color = when (ui.stage) { Stage.LISTENING -> C.accent; Stage.HOLD -> C.amber; else -> C.sub }, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HeardAndFeedback(ui: Ui) {
    val last = ui.last
    Column(Modifier.fillMaxWidth().background(C.panel2, RoundedCornerShape(16.dp)).padding(18.dp)) {
        if (ui.stage == Stage.LISTENING) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(C.accent, RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
                Text(if (ui.settings.inputMode == InputMode.DEMO || ui.sdk.stt != Link.CONNECTED) "듣는 중 (시연용 답변)" else "듣는 중 · Pleos 음성 인식", color = C.accent, fontSize = 16.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(ui.heard.ifBlank { "…" }, color = C.text, fontSize = 36.sp, fontWeight = FontWeight.Medium)
        } else if (last != null && last.heard.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("내가 말한 것", color = C.dim, fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                ScoreBar(last.score, Modifier.width(120.dp))
                Spacer(Modifier.width(8.dp))
                Text("${(last.score * 100).toInt()}%", color = scoreColor(last.score), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(last.heard, color = C.sub, fontSize = 28.sp)
            if (last.better.isNotBlank() && last.better != last.heard) {
                Spacer(Modifier.height(6.dp))
                Text("더 자연스럽게", color = C.dim, fontSize = 14.sp)
                Text(last.better, color = C.accent, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            }
            if (last.commentKo.isNotBlank()) Text(last.commentKo, color = C.sub, fontSize = 22.sp, maxLines = 2)
        } else {
            Text("운전 중에는 화면을 보지 않아도 돼요. 앱이 말로 안내합니다.", color = C.dim, fontSize = 16.sp)
        }
    }
}

@Composable
private fun BottomBar(ui: Ui) {
    val p = ui.plan
    Column(Modifier.fillMaxWidth()) {
        if (p != null) {
            val total = ui.planTotalSec.coerceAtLeast(1)
            PlanBar(total, p.warmupSec, p.reviewSec, progress = (ui.elapsedSec.toFloat() / total).coerceIn(0f, 1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val spoken = ui.todayAttempts.count { it.item.kind != ItemKind.LISTEN && it.heard.isNotBlank() }
            Text("말한 문장 $spoken", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(18.dp))
            val ev = ui.events.lastOrNull()
            if (ev != null) Text(ev.textKo, color = if (ev.textKo.contains("늦어")) C.amber else C.sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            ui.log.lastOrNull()?.let { Text(it, color = C.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(420.dp), textAlign = TextAlign.End) }
        }
    }
}

@Composable
fun PlanBar(total: Int, warm: Int, review: Int, progress: Float?) {
    val main = (total - warm - review).coerceAtLeast(1)
    Box(Modifier.fillMaxWidth().height(16.dp)) {
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
            Box(Modifier.weight(warm.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(C.blue.copy(alpha = 0.75f)))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(main.toFloat()).fillMaxHeight().background(C.accent.copy(alpha = 0.75f)))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(review.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(C.purple.copy(alpha = 0.75f)))
        }
        if (progress != null) {
            val anim by animateFloatAsState(progress, tween(400), label = "p")
            Row(Modifier.fillMaxSize()) {
                if (anim > 0.001f) Box(Modifier.weight(anim).fillMaxHeight().background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
                Box(Modifier.width(4.dp).fillMaxHeight().background(Color.White))
                if (anim < 0.999f) Spacer(Modifier.weight(1f - anim))
            }
        }
    }
}

// =========================== 도착 화면 ===========================

@Composable
fun ArrivalScreen(ui: Ui, vm: AppModel) {
    val spoken = ui.todayAttempts.filter { it.item.kind != ItemKind.LISTEN && it.heard.isNotBlank() }
    val cards = ui.todayAttempts.filter { it.item.kind != ItemKind.LISTEN }.groupBy { it.item.target }.map { (_, v) -> v.maxBy { it.at } }
    val avg = if (spoken.isEmpty()) 0f else spoken.map { it.score }.average().toFloat()
    val weak = cards.filter { it.score < 0.8f }.sortedBy { it.score }
    Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(1.4f).fillMaxHeight()) {
            Text("도착했어요", color = C.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("오늘 ${fmtMin(ui.elapsedSec)} 동안 ${ui.settings.lang.label}로 ${spoken.size}문장을 말했어요", color = C.sub, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Stat("말한 문장", "${spoken.size}", C.accent, Modifier.weight(1f))
                Stat("평균 일치도", "${(avg * 100).toInt()}%", scoreColor(avg), Modifier.weight(1f))
                Stat("새 표현", "${cards.size}", C.blue, Modifier.weight(1f))
                Stat("다시 볼 표현", "${weak.size}", C.purple, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text("오늘의 표현 카드 · 눌러서 다시 듣기", color = C.sub, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(cards.sortedByDescending { it.at }) { a -> ExpressionCard(a) { vm.replay(a) } }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                Text("이 길에서 다시 물어볼 표현", color = C.sub, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                if (weak.isEmpty()) Text("모두 잘 말했어요. 내일은 새 표현으로 시작해요.", color = C.text, fontSize = 17.sp)
                weak.take(4).forEach { a ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(a.better.ifBlank { a.item.target }, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (a.placeLabel.isNotBlank()) "${a.placeLabel} 근처에서 다시 물어요" else "내일 같은 길에서 다시 물어요", color = C.purple, fontSize = 14.sp)
                    }
                }
            }
            Card {
                Text("오늘 수업이 바뀐 순간", color = C.sub, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                ui.events.takeLast(4).forEach { Text("· ${it.textKo}", color = C.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { vm.backToStart() }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = C.accent)) {
                Text("처음으로", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF04201A))
            }
        }
    }
}

@Composable
private fun ExpressionCard(a: Attempt, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(C.panel, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(a.better.ifBlank { a.item.target }, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(a.item.hintKo, color = C.sub, fontSize = 15.sp)
            if (a.heard.isNotBlank()) Text("내가 말한 것: ${a.heard}", color = C.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.placeLabel.isNotBlank()) Text("배운 곳: ${a.placeLabel}", color = C.purple, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (a.heard.isBlank()) "듣기" else "${(a.score * 100).toInt()}%", color = scoreColor(a.score), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            ScoreBar(a.score, Modifier.width(90.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(C.panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(label, color = C.sub, fontSize = 14.sp)
        Text(value, color = color, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

// =========================== 설정 화면 ===========================

@Composable
fun SettingsScreen(ui: Ui, vm: AppModel) {
    val s = ui.settings
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("설정", color = C.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = { vm.go(Screen.START) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = C.accent)) { Text("완료", fontSize = 18.sp, color = Color(0xFF04201A)) }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card {
                    Text("연습할 언어", color = C.sub, fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Lang.entries.forEach { l -> Choice(l.label, s.lang == l) { vm.updateSettings { it.copy(lang = l) } } } }
                    Spacer(Modifier.height(12.dp))
                    Text("수준", color = C.sub, fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("beginner" to "입문", "intermediate" to "중급", "advanced" to "고급").forEach { (k, v) -> Choice(v, s.level == k) { vm.updateSettings { it.copy(level = k) } } }
                    }
                }
                Card {
                    Text("음성 입력", color = C.sub, fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { InputMode.entries.forEach { m -> Choice(m.ko, s.inputMode == m) { vm.updateSettings { it.copy(inputMode = m) } } } }
                    Spacer(Modifier.height(6.dp))
                    Text("음성 인식은 Pleos SpeechToText SDK로만 합니다. 마이크 소리는 차 밖으로 보내지 않고, 첨삭에는 인식된 글자만 씁니다.", color = C.dim, fontSize = 14.sp)
                }
                Card {
                    Toggle("동승자 모드 자동 전환", "좌석·안전벨트 정보로 동승자를 알아채고 함께 퀴즈로 바꿔요", s.familyMode) { v -> vm.updateSettings { it.copy(familyMode = v) } }
                    Toggle("온라인 코치", "상황에 맞는 문장 만들기·첨삭·앞쪽 장소 찾기를 서버에서 해요. 끊기면 내장 문장으로 계속해요", s.onlineCoach) { v -> vm.updateSettings { it.copy(onlineCoach = v) } }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card {
                    Text("데모 주행 속도", color = C.sub, fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(1.0, 4.0, 8.0, 15.0).forEach { x -> Choice("${x.toInt()}배", s.speedup == x) { vm.updateSettings { it.copy(speedup = x) } } } }
                }
                Card { SdkPanel(ui) }
                Card {
                    Text("학습 기록", color = C.sub, fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.clearRecord() }, shape = RoundedCornerShape(12.dp)) { Text("기록 지우기", color = C.red) }
                    Spacer(Modifier.height(6.dp))
                    Text("서버: ${ai.roadtalk.BuildConfig.SERVER_URL}", color = C.dim, fontSize = 13.sp)
                    Text("장소 정보 © OpenStreetMap contributors", color = C.dim, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = C.dim, fontSize = 14.sp)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

// =========================== 공통 ===========================

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.panel, RoundedCornerShape(20.dp)).padding(20.dp)) { content() }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .background(if (selected) C.accent else C.panel2).padding(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, color = if (selected) Color(0xFF04201A) else C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).background(C.panel2).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = C.text, fontSize = 15.sp)
    }
}

@Composable
private fun Legend(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).background(c, RoundedCornerShape(3.dp))); Spacer(Modifier.width(6.dp)); Text(t, color = C.sub, fontSize = 15.sp) }
}

@Composable
private fun ScoreBar(score: Float, modifier: Modifier) {
    Box(modifier.height(8.dp).background(C.line, RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth(score.coerceIn(0.02f, 1f)).fillMaxHeight().background(scoreColor(score), RoundedCornerShape(4.dp)))
    }
}

fun scoreColor(s: Float) = when { s >= 0.85f -> C.accent; s >= 0.6f -> C.warm; else -> C.red }
fun phaseColor(p: Phase) = when (p) { Phase.WARMUP -> C.blue; Phase.MAIN -> C.accent; Phase.REVIEW -> C.purple; Phase.DONE -> C.sub }
