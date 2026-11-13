package ai.roda.core

import java.util.Locale

/** 학습 언어. 모두 Pleos 음성 SDK가 지원하는 로케일이다. */
enum class Lang(val code: String, val label: String, val locale: Locale) {
    EN("en", "English", Locale.US),
    ES("es", "Español", Locale("es", "MX")),
    FR("fr", "Français", Locale.CANADA_FRENCH);
}

val KOREAN: Locale = Locale.KOREA

enum class TurnKind { NONE, STRAIGHT, LEFT, RIGHT, UTURN, KEEP_LEFT, KEEP_RIGHT, ENTER_HIGHWAY, EXIT_HIGHWAY, ROUNDABOUT, TUNNEL, TOLL, ARRIVE, OTHER }

enum class Caution { SCHOOL_ZONE, SPEED_BUMP, SPEED_CAMERA, ACCIDENT_AREA, RAILROAD, SLIPPERY, NARROW_ROAD, CONSTRUCTION, WILDLIFE, FOG, OTHER }

enum class Traffic { UNKNOWN, FAST, MODERATE, SLOW, HEAVY }

enum class Gear { UNKNOWN, PARK, DRIVE, REVERSE, NEUTRAL }

enum class TurnSignal { OFF, LEFT, RIGHT }

data class NextTurn(val kind: TurnKind, val distanceM: Int, val description: String = "")

data class CautionAhead(val kind: Caution, val distanceM: Int)

data class Landmark(val name: String, val category: String, val distanceM: Int, val side: String = "ahead")

/** 앱이 판단에 쓰는 '지금 상황' 한 장. 내비·위치·차량 SDK 값이 모두 여기로 모인다. */
data class DriveState(
    val destinationName: String = "",
    val destinationKind: String = "other",      // office, mart, home, charger, school, airport, other
    val remainSec: Int = 0,
    val remainM: Int = 0,
    val nextTurn: NextTurn? = null,
    /** 앞으로 지나갈 안내 지점들(가까운 순). 화면 아래 길 그림에 쓴다. */
    val ahead: List<NextTurn> = emptyList(),
    val cautions: List<CautionAhead> = emptyList(),
    val traffic: Traffic = Traffic.UNKNOWN,
    val speedLimit: Int = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val heading: Float = 0f,
    val wiperOn: Boolean = false,
    val turnSignal: TurnSignal = TurnSignal.OFF,
    val brakePressed: Boolean = false,
    val gear: Gear = Gear.UNKNOWN,
    val cabinTempC: Float? = null,
    val batteryPct: Int? = null,
    val passengerFront: Boolean = false,
    val passengerRear: Boolean = false,
    val landmarks: List<Landmark> = emptyList(),
    val routeActive: Boolean = false,
    val arrived: Boolean = false,
) {
    val hasPassenger get() = passengerFront || passengerRear
    val nearestCaution get() = cautions.minByOrNull { it.distanceM }
}

/** SDK별 연결 상태. 화면 하단에 그대로 보여 준다. */
enum class Link { CONNECTING, CONNECTED, UNAVAILABLE, DEMO }

data class SdkStatus(
    val navi: Link = Link.CONNECTING,
    val location: Link = Link.CONNECTING,
    val vehicle: Link = Link.CONNECTING,
    val stt: Link = Link.CONNECTING,
    val tts: Link = Link.CONNECTING,
)

/** 한 번의 연습 문항. */
enum class ItemKind { LISTEN, REPEAT, RESPOND, QUIZ }

data class Item(
    val id: String,
    val topic: String,
    val kind: ItemKind,
    val promptKo: String,
    val target: String,
    val hintKo: String,
    val expected: String = target,
    val long: Boolean = false,
    val source: String = "bank",     // bank, coach(LLM), review, place
)

data class Attempt(
    val item: Item,
    val heard: String,
    val score: Float,
    val better: String = "",
    val commentKo: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val placeLabel: String = "",
    val at: Long = System.currentTimeMillis(),
)
