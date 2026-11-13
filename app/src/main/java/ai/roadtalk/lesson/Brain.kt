package ai.roadtalk.lesson

import ai.roadtalk.core.Caution
import ai.roadtalk.core.DriveState
import ai.roadtalk.core.Gear
import ai.roadtalk.core.Traffic
import ai.roadtalk.core.TurnKind
import ai.roadtalk.core.TurnSignal
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ======================= 말하기 게이트 =======================

enum class Window { HOLD, LISTEN_ONLY, SHORT, LONG, PARKED }

data class GateDecision(val window: Window, val reasonKo: String, val runwayM: Int)

/**
 * 지금 말을 걸어도 되는지 판단한다.
 * 회전·주의 지점까지 남은 거리(앞으로 곧게 갈 수 있는 거리)와 방향지시등·기어로 정한다.
 */
object Gate {
    const val HOLD_TURN_M = 300
    const val HOLD_CAUTION_M = 150
    const val LISTEN_SCHOOL_M = 300
    const val SHORT_RUNWAY_M = 900

    fun decide(s: DriveState): GateDecision {
        if (s.gear == Gear.PARK) return GateDecision(Window.PARKED, "주차 중", Int.MAX_VALUE)
        if (s.gear == Gear.REVERSE) return GateDecision(Window.HOLD, "후진 중이라 잠시 멈춰요", 0)
        if (s.turnSignal != TurnSignal.OFF) {
            val dir = if (s.turnSignal == TurnSignal.LEFT) "왼쪽" else "오른쪽"
            return GateDecision(Window.HOLD, "$dir 방향지시등 · 차선 변경 끝나면 이어 갈게요", 0)
        }
        val turn = s.nextTurn?.takeIf { it.kind != TurnKind.NONE && it.kind != TurnKind.STRAIGHT && it.kind != TurnKind.TUNNEL }
        val turnM = turn?.distanceM ?: Int.MAX_VALUE
        if (turnM <= HOLD_TURN_M) {
            return GateDecision(Window.HOLD, "${turnLabel(turn!!.kind)} ${turnM}m · 지나면 이어 갈게요", turnM)
        }
        val school = s.cautions.filter { it.kind == Caution.SCHOOL_ZONE }.minByOrNull { it.distanceM }
        val hard = s.cautions.filter { it.kind != Caution.SCHOOL_ZONE }.minByOrNull { it.distanceM }
        if (hard != null && hard.distanceM <= HOLD_CAUTION_M) {
            return GateDecision(Window.HOLD, "${cautionLabel(hard.kind)} ${hard.distanceM}m · 잠시 멈춰요", hard.distanceM)
        }
        if (school != null && school.distanceM <= LISTEN_SCHOOL_M) {
            return GateDecision(Window.LISTEN_ONLY, "어린이 보호구역 · 듣기만 할게요", school.distanceM)
        }
        val runway = min(turnM, hard?.distanceM ?: Int.MAX_VALUE)
        return if (runway <= SHORT_RUNWAY_M) GateDecision(Window.SHORT, "다음 ${if (turn != null) turnLabel(turn.kind) else "구간"}까지 ${runway}m · 짧게", runway)
        else GateDecision(Window.LONG, if (runway == Int.MAX_VALUE) "긴 직진 구간" else "다음 ${if (turn != null) turnLabel(turn.kind) else "구간"}까지 ${fmtDist(runway)} · 대화하기 좋아요", runway)
    }

    fun turnLabel(k: TurnKind) = when (k) {
        TurnKind.LEFT -> "좌회전"; TurnKind.RIGHT -> "우회전"; TurnKind.UTURN -> "유턴"
        TurnKind.KEEP_LEFT -> "왼쪽 차로 유지"; TurnKind.KEEP_RIGHT -> "오른쪽 차로 유지"
        TurnKind.ENTER_HIGHWAY -> "고속도로 진입"; TurnKind.EXIT_HIGHWAY -> "고속도로 진출"
        TurnKind.ROUNDABOUT -> "회전교차로"; TurnKind.TOLL -> "톨게이트"; TurnKind.ARRIVE -> "도착"
        TurnKind.TUNNEL -> "터널"; else -> "회전"
    }

    fun cautionLabel(k: Caution) = when (k) {
        Caution.SCHOOL_ZONE -> "어린이 보호구역"; Caution.SPEED_BUMP -> "과속방지턱"; Caution.SPEED_CAMERA -> "과속 카메라"
        Caution.ACCIDENT_AREA -> "사고 다발 구간"; Caution.RAILROAD -> "철길 건널목"; Caution.SLIPPERY -> "미끄러운 길"
        Caution.NARROW_ROAD -> "좁은 길"; Caution.CONSTRUCTION -> "공사 구간"; Caution.WILDLIFE -> "야생동물 출몰"
        Caution.FOG -> "안개 구간"; Caution.OTHER -> "주의 구간"
    }
}

fun fmtDist(m: Int): String = if (m >= 1000) "%.1fkm".format(m / 1000f) else "${m}m"
fun fmtMin(sec: Int): String = "${max(0, (sec / 60.0).roundToInt())}분"

// ======================= 도착 시간 맞춤 커리큘럼 =======================

enum class Phase(val ko: String) { WARMUP("워밍업"), MAIN("본 대화"), REVIEW("복습"), DONE("도착") }

data class Plan(
    val totalSec: Int,
    val warmupSec: Int,
    val reviewSec: Int,
    val plannedItems: Int,
) {
    val mainSec get() = max(0, totalSec - warmupSec - reviewSec)
}

data class PlanEvent(val atElapsedSec: Int, val textKo: String)

/**
 * 출발할 때 도착 예정 시간으로 수업을 나누고, 도착 시간이 바뀌면 다시 짠다.
 * 목적지 2km 전부터는 무조건 복습으로 넘어가 매번 하나의 수업이 끝까지 완성되게 한다.
 */
class Planner {
    var plan: Plan? = null; private set
    var startedAtSec = 0; private set
    private var lastTotal = 0
    val events = mutableListOf<PlanEvent>()

    fun start(remainSec: Int, elapsedSec: Int = 0) {
        val total = max(remainSec, 5 * 60)
        plan = make(total)
        lastTotal = total
        startedAtSec = elapsedSec
        events.clear()
        events += PlanEvent(0, "오늘 ${fmtMin(total)} · 워밍업 ${fmtMin(plan!!.warmupSec)} / 본 대화 ${fmtMin(plan!!.mainSec)} / 복습 ${fmtMin(plan!!.reviewSec)}")
    }

    private fun make(total: Int): Plan {
        val warm = (total * 0.08).toInt().coerceIn(120, 240)
        val review = (total * 0.12).toInt().coerceIn(180, 360)
        val main = max(0, total - warm - review)
        return Plan(total, warm, review, plannedItems = max(3, main / 75))
    }

    /** 남은 시간이 바뀌면 전체 길이를 다시 계산한다. 3분 이상 차이 날 때만 반영한다. */
    fun onRemainChanged(elapsedSec: Int, remainSec: Int): PlanEvent? {
        val p = plan ?: return null
        val newTotal = elapsedSec - startedAtSec + remainSec
        val diff = newTotal - lastTotal
        if (kotlin.math.abs(diff) < 180) return null
        lastTotal = newTotal
        val oldItems = p.plannedItems
        plan = make(newTotal).copy(warmupSec = p.warmupSec)
        val delta = plan!!.plannedItems - oldItems
        val ev = if (diff > 0) PlanEvent(elapsedSec, "막혀서 도착이 ${fmtMin(diff)} 늦어져요 → 대화 ${max(1, delta)}개 추가")
        else PlanEvent(elapsedSec, "도착이 ${fmtMin(-diff)} 빨라져요 → 복습을 앞당겨요")
        events += ev
        return ev
    }

    fun phase(elapsedSec: Int, remainSec: Int, remainM: Int, arrived: Boolean): Phase {
        val p = plan ?: return Phase.WARMUP
        if (arrived) return Phase.DONE
        val e = elapsedSec - startedAtSec
        if ((remainM in 1..2000) || remainSec in 1..p.reviewSec) return Phase.REVIEW
        if (e < p.warmupSec) return Phase.WARMUP
        return Phase.MAIN
    }

    /** 진행 막대용 0~1 위치. */
    fun progress(elapsedSec: Int): Float {
        val p = plan ?: return 0f
        return ((elapsedSec - startedAtSec).toFloat() / lastTotal.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    val totalSec get() = lastTotal
}

// ======================= 창밖 주제 고르기 =======================

data class Topic(val key: String, val ko: String, val why: String)

/**
 * 운전자가 이미 보고 있는 것에서 대화 주제를 고른다. 앞에서부터 우선순위가 높다.
 * 같은 주제가 연달아 나오지 않도록 최근 주제를 피한다.
 */
object TopicPicker {
    fun pick(s: DriveState, phase: Phase, window: Window, recent: List<String>, familyMode: Boolean): Topic {
        val c = candidates(s, phase, window, familyMode)
        return c.firstOrNull { it.key !in recent.takeLast(2) } ?: c.first()
    }

    fun candidates(s: DriveState, phase: Phase, window: Window, familyMode: Boolean): List<Topic> {
        val out = mutableListOf<Topic>()
        if (phase == Phase.REVIEW) out += Topic("review", "오늘 복습", "목적지가 가까워요")
        if (window == Window.LISTEN_ONLY) out += Topic("school_zone", "어린이 보호구역", "앞에 어린이 보호구역")
        if (familyMode && s.hasPassenger && window != Window.LISTEN_ONLY) out += Topic("family_quiz", "함께 퀴즈", if (s.passengerRear) "뒷좌석 탑승" else "동승자 탑승")
        s.cautions.firstOrNull { it.kind == Caution.SPEED_CAMERA && it.distanceM < 1500 }?.let { out += Topic("road_signs", "교통 표지·규칙", "과속 카메라 ${fmtDist(it.distanceM)}") }
        s.nextTurn?.let { t ->
            if (t.kind != TurnKind.NONE && t.distanceM in 300..1500) out += Topic("directions", "길 안내 표현", "${Gate.turnLabel(t.kind)} ${fmtDist(t.distanceM)}")
        }
        if (s.traffic == Traffic.HEAVY || s.traffic == Traffic.SLOW) out += Topic("traffic", "정체 상황 말하기", "앞이 막혀요")
        if (s.wiperOn) out += Topic("weather_rain", "날씨 이야기", "와이퍼 작동 중")
        s.landmarks.firstOrNull { it.distanceM in 80..600 }?.let { out += Topic("landmark", "앞에 보이는 곳", "${it.name} ${fmtDist(it.distanceM)}") }
        s.batteryPct?.let { if (it < 30) out += Topic("charging", "충전·주유 회화", "배터리 $it%") }
        s.cabinTempC?.let { if (it < 18f || it > 27f) out += Topic("temperature", "몸 상태·온도", "실내 ${it.toInt()}℃") }
        when (s.destinationKind) {
            "office" -> out += Topic("office_meeting", "회의 표현", "목적지: ${s.destinationName.ifBlank { "회사" }}")
            "mart" -> out += Topic("shopping", "장보기 표현", "목적지: ${s.destinationName}")
            "airport" -> out += Topic("travel", "여행 회화", "목적지: ${s.destinationName}")
            "charger" -> out += Topic("charging", "충전·주유 회화", "목적지: ${s.destinationName}")
            "hospital" -> out += Topic("health", "병원 표현", "목적지: ${s.destinationName}")
        }
        if (phase == Phase.WARMUP) out.add(0, Topic("warmup", "가볍게 인사", "출발 워밍업"))
        out += Topic("smalltalk", "일상 대화", "기본 주제")
        return out
    }
}
