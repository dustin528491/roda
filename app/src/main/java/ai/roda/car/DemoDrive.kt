package ai.roda.car

import ai.roda.core.Caution
import ai.roda.core.CautionAhead
import ai.roda.core.DriveState
import ai.roda.core.Gear
import ai.roda.core.Lang
import ai.roda.core.Landmark
import ai.roda.core.NextTurn
import ai.roda.core.Traffic
import ai.roda.core.TurnKind
import ai.roda.core.TurnSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Scenario(val ko: String, val desc: String, val dest: String, val destKind: String) {
    COMMUTE("출근길", "분당 정자동 → 강남대로 회사 · 41분 · 중간에 정체와 비", "현대자동차 강남대로사옥", "office"),
    RAINY_HOME("비 오는 퇴근길", "회사 → 마트 들러 집 · 와이퍼 작동", "이마트 양재점", "mart"),
    FAMILY("주말 가족 드라이브", "뒷좌석에 아이 탑승 · 함께 퀴즈", "서울숲 공원", "other"),
    TRAVEL("여행 2주 전", "스페인어 렌터카·주유 표현 · 배터리 낮음", "강남대로 충전소", "charger"),
}

/**
 * 시연용 주행. 실제 내비 경로가 없을 때 정해진 출근길을 시간을 압축해 흘려보낸다.
 * 내비·위치 값만 대신 넣고, 와이퍼·방향지시등·좌석 같은 차량 값은 Virtual Console(실제 Vehicle SDK)에서 바꾸면 그대로 반영된다.
 */
class DemoDrive(private val car: PleosCar, private val scope: CoroutineScope) {
    var scenario = Scenario.COMMUTE; private set
    var speedup = 8.0
    var simSec = 0.0; private set
    var running = false; private set
    private var job: Job? = null
    private var vehicleReal = false

    /** 경로: 정자역 → 판교 → 경부고속도로 → 양재 → 강남대로. 좌표는 대략값이다. */
    private val route = listOf(
        37.3670 to 127.1083, 37.3725 to 127.1101, 37.3800 to 127.1112, 37.3948 to 127.1111,
        37.4050 to 127.1055, 37.4150 to 127.0990, 37.4300 to 127.0830, 37.4420 to 127.0690,
        37.4520 to 127.0560, 37.4610 to 127.0470, 37.4690 to 127.0400, 37.4770 to 127.0370,
        37.4842 to 127.0346, 37.4885 to 127.0322, 37.4935 to 127.0300,
    )
    private val cum: List<Double> = route.indices.map { i -> (1..i).sumOf { dist(route[it - 1], route[it]) } }
    private val total get() = cum.last()

    private data class Turn(val at: Double, val kind: TurnKind, val desc: String)
    private data class Mark(val at: Double, val kind: Caution)
    private data class Lm(val at: Double, val name: String, val cat: String)

    private val turns = listOf(
        Turn(0.05, TurnKind.RIGHT, "정자일로 · 우회전"),
        Turn(0.27, TurnKind.ENTER_HIGHWAY, "판교IC · 경부고속도로 서울 방향"),
        Turn(0.74, TurnKind.EXIT_HIGHWAY, "양재IC · 진출"),
        Turn(0.83, TurnKind.RIGHT, "양재역 · 강남대로 방향 우회전"),
        Turn(1.00, TurnKind.ARRIVE, "목적지"),
    )
    private val marks = listOf(Mark(0.12, Caution.SCHOOL_ZONE), Mark(0.50, Caution.SPEED_CAMERA), Mark(0.90, Caution.SPEED_CAMERA))
    private val lms = listOf(
        Lm(0.09, "정자역", "station"), Lm(0.20, "판교역", "station"), Lm(0.23, "현대백화점 판교점", "department_store"),
        Lm(0.86, "양재역", "station"), Lm(0.93, "강남세브란스병원", "hospital"),
    )

    // 시나리오별 시간표(시뮬레이션 초). 계획 41분, 정체로 실제 47분.
    private val plannedSec = 41 * 60
    private val actualSec = 47 * 60
    private val jamStart = 15 * 60
    private val jamEnd = 24 * 60
    private val rainStart = 26 * 60

    fun start(s: Scenario, vehicleConnected: Boolean) {
        stop()
        scenario = s
        vehicleReal = vehicleConnected
        simSec = 0.0
        running = true
        car.naviFromDemo = true
        car.markDemo()
        val dest = s.dest to s.destKind
        car.applyDemo {
            it.copy(
                destinationName = dest.first, destinationKind = dest.second, routeActive = true, arrived = false,
                gear = Gear.DRIVE,
                passengerRear = if (s == Scenario.FAMILY) true else it.passengerRear && vehicleReal,
                batteryPct = if (s == Scenario.TRAVEL) 24 else (it.batteryPct ?: 68),
                wiperOn = if (s == Scenario.RAINY_HOME) true else it.wiperOn && vehicleReal,
            )
        }
        job = scope.launch {
            val tickMs = 400L
            while (isActive && running) {
                tick()
                delay(tickMs)
                simSec += tickMs / 1000.0 * speedup
                if (simSec >= actualSec) { finish(); break }
            }
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        car.naviFromDemo = false
    }

    /** 한 번에 1분씩 앞으로(시연 중 건너뛰기). */
    fun skip(seconds: Int) { simSec = (simSec + seconds).coerceAtMost(actualSec - 1.0) }

    private fun progressFrac(t: Double): Double {
        // 정체 구간에서는 거의 서 있다(시뮬레이션 시간은 흐르지만 거리는 적게 간다).
        val jamLen = (jamEnd - jamStart).toDouble()
        val moving = actualSec - jamLen * 0.8
        val eff = when {
            t < jamStart -> t
            t < jamEnd -> jamStart + (t - jamStart) * 0.2
            else -> t - jamLen * 0.8
        }
        return (eff / moving).coerceIn(0.0, 1.0)
    }

    private fun tick() {
        val t = simSec
        val f = progressFrac(t)
        val d = f * total
        val (lat, lon, heading) = pointAt(d)
        val jamKnown = t >= jamStart
        val remain = ((if (jamKnown) actualSec else plannedSec) - t).roundToInt().coerceAtLeast(0)
        val remainM = (total - d).roundToInt().coerceAtLeast(0)
        val next = turns.firstOrNull { it.at * total > d + 5 }
        val nextTurn = next?.let { NextTurn(it.kind, (it.at * total - d).roundToInt(), it.desc) }
        val ahead = turns.filter { it.at * total > d + 5 }.map { NextTurn(it.kind, (it.at * total - d).roundToInt(), it.desc) }
        val cautions = marks.map { CautionAhead(it.kind, (it.at * total - d).roundToInt()) }.filter { it.distanceM in 0..1500 }
        val landmarks = lms.map { Landmark(it.name, it.cat, (it.at * total - d).roundToInt()) }.filter { it.distanceM in 50..800 }
        val traffic = when {
            t in jamStart.toDouble()..jamEnd.toDouble() -> Traffic.HEAVY
            t in (jamStart - 120.0)..jamStart.toDouble() || t in jamEnd.toDouble()..(jamEnd + 120.0) -> Traffic.SLOW
            else -> Traffic.FAST
        }
        val signal = if (nextTurn != null && nextTurn.distanceM < 120 && (nextTurn.kind == TurnKind.RIGHT || nextTurn.kind == TurnKind.EXIT_HIGHWAY)) TurnSignal.RIGHT
        else if (nextTurn != null && nextTurn.distanceM < 120 && nextTurn.kind == TurnKind.LEFT) TurnSignal.LEFT else TurnSignal.OFF
        val rain = scenario == Scenario.RAINY_HOME || (scenario == Scenario.COMMUTE && t >= rainStart)
        car.applyDemo {
            it.copy(
                lat = lat, lon = lon, heading = heading,
                remainSec = remain, remainM = remainM, nextTurn = nextTurn, ahead = ahead, cautions = cautions,
                traffic = traffic, speedLimit = if (f in 0.27..0.74) 100 else 50, landmarks = landmarks,
                // 차량 SDK가 실제로 연결돼 있으면 Virtual Console 값을 존중하고, 아니면 시나리오 값을 넣는다.
                // Virtual Console에서 켠 방향지시등·와이퍼는 그대로 반영하고, 아니면 시나리오 값을 쓴다.
                turnSignal = if (signal != TurnSignal.OFF) signal else car.realTurnSignal,
                wiperOn = rain || car.realWiperOn,
                passengerFront = scenario == Scenario.FAMILY,
                passengerRear = scenario == Scenario.FAMILY,
                gear = Gear.DRIVE,  // 에뮬레이터는 P 상태로 켜지므로 데모 중에는 주행으로 둔다
                batteryPct = (it.batteryPct ?: 68).let { b -> if (vehicleReal) b else ((if (scenario == Scenario.TRAVEL) 24 else 68) - (f * 6).toInt()) },
                cabinTempC = it.cabinTempC ?: 22f,
            )
        }
    }

    private fun finish() {
        running = false
        car.applyDemo { it.copy(remainSec = 0, remainM = 0, nextTurn = null, cautions = emptyList(), arrived = true, routeActive = false, gear = Gear.PARK, traffic = Traffic.UNKNOWN) }
        car.naviFromDemo = false
    }

    val lang get() = if (scenario == Scenario.TRAVEL) Lang.ES else null

    private fun pointAt(d: Double): Triple<Double, Double, Float> {
        val i = cum.indexOfFirst { it >= d }.let { if (it <= 0) 1 else it }
        val a = route[i - 1]; val b = route[i]
        val seg = (cum[i] - cum[i - 1]).coerceAtLeast(1.0)
        val r = ((d - cum[i - 1]) / seg).coerceIn(0.0, 1.0)
        val lat = a.first + (b.first - a.first) * r
        val lon = a.second + (b.second - a.second) * r
        val hd = Math.toDegrees(atan2((b.second - a.second) * cos(Math.toRadians(a.first)), b.first - a.first)).toFloat()
        return Triple(lat, lon, (hd + 360f) % 360f)
    }

    private fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dy = (a.first - b.first) * 111_320.0
        val dx = (a.second - b.second) * 111_320.0 * cos(Math.toRadians(a.first))
        return sqrt(dx * dx + dy * dy)
    }
}
