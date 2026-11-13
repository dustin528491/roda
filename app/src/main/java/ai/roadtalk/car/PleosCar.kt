package ai.roadtalk.car

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.roadtalk.core.Caution
import ai.roadtalk.core.CautionAhead
import ai.roadtalk.core.DriveState
import ai.roadtalk.core.Gear
import ai.roadtalk.core.Link
import ai.roadtalk.core.NextTurn
import ai.roadtalk.core.SdkStatus
import ai.roadtalk.core.Traffic
import ai.roadtalk.core.TurnKind
import ai.roadtalk.core.TurnSignal
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Connect SDK(내비·위치·차량)를 하나의 DriveState로 모은다.
 * SDK 서비스가 없는 기기에서도 앱이 죽지 않도록 모든 호출을 감싼다.
 */
class PleosCar(private val context: Context) {
    private val tag = "RoadTalk/Car"

    private val _state = MutableStateFlow(DriveState())
    val state: StateFlow<DriveState> = _state

    private val _status = MutableStateFlow(SdkStatus(stt = Link.CONNECTING, tts = Link.CONNECTING))
    val status: StateFlow<SdkStatus> = _status

    /** 데모 주행 중이면 내비·위치 값은 데모가 채우고, 차량 값(와이퍼·방향지시등 등)은 실제 SDK 값을 그대로 쓴다. */
    @Volatile var naviFromDemo = false

    /** 실제 Vehicle SDK가 마지막으로 알려 준 값. 데모 값과 섞이지 않게 따로 둔다. */
    @Volatile var realTurnSignal = TurnSignal.OFF; private set
    @Volatile var realWiperOn = false; private set
    @Volatile var realPassengerFront = false; private set
    @Volatile var realPassengerRear = false; private set

    /** 구역 알림(같은 길 복습)이 들어오면 알린다. */
    var onPlaceEntered: ((String) -> Unit)? = null

    private var navi: NaviHelper? = null
    private var location: FusedLocation? = null
    private var vehicle: Vehicle? = null

    fun start() {
        startNavi()
        startLocation()
        startVehicle()
    }

    fun stop() {
        runCatching { navi?.removeListener(naviListener); navi?.release() }
        runCatching { location?.unregisterFusedLocationCallback(locationListener); location?.release() }
        runCatching { vehicle?.release() }
    }

    /** 데모가 내비·위치 값을 넣을 때 쓴다. */
    fun applyDemo(transform: (DriveState) -> DriveState) = _state.update(transform)

    // ---------------- 내비(NaviHelper) ----------------

    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(list: List<TBTInfo>) {
            if (naviFromDemo) return
            val all = list.map { t -> NextTurn(turnKind(t.type), t.distance.toInt(), t.description) }
            _state.update { it.copy(nextTurn = all.firstOrNull(), ahead = all.take(6)) }
        }

        override fun onCautionPointInfo(list: List<CautionPoint>) {
            if (naviFromDemo) return
            val cams = _state.value.cautions.filter { it.kind == Caution.SPEED_CAMERA }
            _state.update { it.copy(cautions = cams + list.map { c -> CautionAhead(caution(c.type), c.remainDistance) }) }
        }

        override fun onCameraAlert(list: List<CameraAlert>) {
            if (naviFromDemo) return
            val others = _state.value.cautions.filter { it.kind != Caution.SPEED_CAMERA }
            _state.update { it.copy(cautions = others + list.map { c -> CautionAhead(Caution.SPEED_CAMERA, c.remainDistance) }) }
        }

        override fun onTripInfo(info: TripInfo) {
            if (naviFromDemo) return
            _state.update { it.copy(remainM = if (info.distance > 0) info.distance else it.remainM, remainSec = etaToRemainSec(info.eta, it.remainSec)) }
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            if (naviFromDemo) return
            _state.update {
                it.copy(
                    destinationName = info.name,
                    destinationKind = guessPlaceKind(info.name + " " + info.address),
                    remainM = if (info.remainDistance > 0) info.remainDistance else it.remainM,
                    remainSec = if (info.remainTime > 0) info.remainTime else it.remainSec,
                    routeActive = true,
                )
            }
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            if (naviFromDemo) return
            val items = runCatching { info.trafficStatus?.congestionItemList.orEmpty() }.getOrDefault(emptyList())
            val worst = items.mapNotNull { runCatching { it.trafficCongestionType }.getOrNull() }.maxByOrNull { it.ordinal }
            _state.update { it.copy(traffic = traffic(worst)) }
        }

        override fun onCurrentRoadSpeedLimit(limit: Int) {
            if (naviFromDemo) return
            _state.update { it.copy(speedLimit = limit) }
        }

        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) {
            if (naviFromDemo) return
            _state.update { it.copy(routeActive = true, arrived = false) }
            refreshNavi()
        }

        override fun onDestinationChanged() { if (!naviFromDemo) refreshNavi() }

        override fun onDestinationArrived(info: ai.pleos.playground.navi.data.DestinationArrivedInfo) {
            if (naviFromDemo) return
            _state.update { it.copy(arrived = true, routeActive = false, remainM = 0, remainSec = 0) }
        }

        override fun onRouteCancelled() {
            if (naviFromDemo) return
            _state.update { it.copy(routeActive = false) }
        }
    }

    private fun startNavi() {
        try {
            val n = NaviHelper(context.applicationContext)
            navi = n
            n.initialize(
                {
                    _status.update { it.copy(navi = Link.CONNECTED) }
                    runCatching { n.addListener(naviListener) }
                    refreshNavi()
                    Unit
                },
                { e ->
                    Log.w(tag, "navi init failed", e)
                    _status.update { it.copy(navi = Link.UNAVAILABLE) }
                    Unit
                },
            )
        } catch (e: Throwable) {
            Log.w(tag, "navi unavailable", e)
            _status.update { it.copy(navi = Link.UNAVAILABLE) }
        }
    }

    /** 정보 조회 API는 값을 직접 돌려주지 않고 리스너 콜백으로 준다. */
    fun refreshNavi() {
        val n = navi ?: return
        runCatching { n.getDestinationInfo() }
        runCatching { n.getTBTInfo() }
        runCatching { n.getRouteStateInfo() }
    }

    // ---------------- 위치(FusedLocation) ----------------

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            if (naviFromDemo || data == null) return
            _state.update { it.copy(lat = data.latitude, lon = data.longitude, heading = data.heading) }
        }

        override fun onError(e: Exception) { Log.w(tag, "location error", e) }
    }

    private fun startLocation() {
        try {
            val l = FusedLocation(context.applicationContext)
            location = l
            l.initialize()
            if (!l.isSupported()) {
                _status.update { it.copy(location = Link.UNAVAILABLE) }
                return
            }
            l.registerFusedLocationCallback(locationListener)
            _status.update { it.copy(location = Link.CONNECTED) }
        } catch (e: Throwable) {
            Log.w(tag, "location unavailable", e)
            _status.update { it.copy(location = Link.UNAVAILABLE) }
        }
    }

    /** 자주 지나는 장소에 구역 알림을 건다. 들어오면 그 장소에서 배운 표현을 복습한다. */
    fun watchPlace(key: String, lat: Double, lon: Double, radiusM: Float = 150f) {
        val l = location ?: return
        runCatching {
            l.addProximityAlert(
                ProximityAlertRequest(lat, lon, radiusM, setOf(ProximityTransition.ENTER)),
                object : ProximityAlertListener {
                    override fun onRegistered(id: String) {}
                    override fun onTransition(event: ProximityAlertEvent) { onPlaceEntered?.invoke(key) }
                    override fun onError(e: Exception) { Log.w(tag, "proximity error", e) }
                },
            )
        }
    }

    // ---------------- 차량(Vehicle) ----------------

    private fun startVehicle() {
        try {
            val v = Vehicle(context.applicationContext)
            vehicle = v
            v.initialize()
            var any = false
            any = any or tryReg("wiper") {
                v.getWiper().registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), object : WiperStateListener {
                    override fun onWiperStateUpdated(s: WiperState?) { realWiperOn = s == WiperState.ON; _state.update { it.copy(wiperOn = realWiperOn) } }
                    override fun onFailed(e: Exception) { Log.w(tag, "wiper", e) }
                })
            }
            any = any or tryReg("turn") {
                v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                    override fun onTurnSignalStateUpdated(s: TurnSignalValue?) {
                        realTurnSignal = when (s) { TurnSignalValue.LEFT -> TurnSignal.LEFT; TurnSignalValue.RIGHT -> TurnSignal.RIGHT; else -> TurnSignal.OFF }
                        _state.update { it.copy(turnSignal = realTurnSignal) }
                    }
                    override fun onFailed(e: Exception) { Log.w(tag, "turn", e) }
                })
            }
            any = any or tryReg("brake") {
                v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
                    override fun onBrakePedalStatusUpdated(on: Boolean?) { _state.update { it.copy(brakePressed = on == true) } }
                    override fun onFailed(e: Exception) { Log.w(tag, "brake", e) }
                })
            }
            any = any or tryReg("gear") {
                v.getGear().registerGearSelection(object : GearSelectionListener {
                    override fun onSelected(g: VehicleGear?) { if (!naviFromDemo) _state.update { it.copy(gear = gear(g)) } }
                    override fun onFailed(e: Exception) { Log.w(tag, "gear", e) }
                })
            }
            any = any or tryReg("battery") {
                v.getEvBattery().registerEvBatteryStateOfCharge(object : EvBatteryStateOfChargeListener {
                    override fun onEvBatteryStateOfChargeUpdated(p: Int?) { _state.update { it.copy(batteryPct = p) } }
                    override fun onFailed(e: Exception) { Log.w(tag, "battery", e) }
                })
            }
            any = any or tryReg("cabin") {
                v.getHVAC().registerHvacTemperatureCurrent(SeatArea(SeatValue.ROW_1_LEFT), object : HvacTemperatureValueListener {
                    override fun onHvacTemperatureUpdated(c: Float?, f: Float?) { if (c != null) _state.update { it.copy(cabinTempC = c) } }
                    override fun onFailed(e: Exception) { Log.w(tag, "cabin", e) }
                })
            }
            val seat = v.getSeat()
            any = any or tryReg("seat-front") {
                seat.registerSeatOccupancy(SeatArea(SeatValue.ROW_1_RIGHT), object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(on: Boolean?) { realPassengerFront = on == true; if (!naviFromDemo) _state.update { it.copy(passengerFront = realPassengerFront) } }
                    override fun onFailed(e: Exception) { Log.w(tag, "seat", e) }
                })
            }
            for (sv in listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)) {
                any = any or tryReg("belt-rear") {
                    seat.registerSeatBeltBuckledState(SeatArea(sv), object : SeatBeltBuckledStateListener {
                        override fun onSeatBeltBuckledStateUpdated(on: Boolean?) { if (on == true) { realPassengerRear = true; if (!naviFromDemo) _state.update { it.copy(passengerRear = true) } } }
                        override fun onFailed(e: Exception) { Log.w(tag, "belt", e) }
                    })
                }
            }
            _status.update { it.copy(vehicle = if (any) Link.CONNECTED else Link.UNAVAILABLE) }
        } catch (e: Throwable) {
            Log.w(tag, "vehicle unavailable", e)
            _status.update { it.copy(vehicle = Link.UNAVAILABLE) }
        }
    }

    private inline fun tryReg(name: String, block: () -> Unit): Boolean = try {
        block(); true
    } catch (e: Throwable) {
        Log.w(tag, "register $name failed", e); false
    }

    fun setVoiceStatus(stt: Link? = null, tts: Link? = null) =
        _status.update { it.copy(stt = stt ?: it.stt, tts = tts ?: it.tts) }

    fun markDemo() = _status.update {
        it.copy(
            navi = if (it.navi == Link.CONNECTED) it.navi else Link.DEMO,
            location = if (it.location == Link.CONNECTED) it.location else Link.DEMO,
            vehicle = if (it.vehicle == Link.CONNECTED) it.vehicle else Link.DEMO,
        )
    }

    companion object {
        fun turnKind(t: TurnType?): TurnKind = when (t) {
            null, TurnType.TURN_UNKNOWN -> TurnKind.NONE
            TurnType.TURN_STRAIGHT -> TurnKind.STRAIGHT
            TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnKind.RIGHT
            TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> TurnKind.LEFT
            TurnType.TURN_UTURN -> TurnKind.UTURN
            TurnType.TURN_KEEP_R -> TurnKind.KEEP_RIGHT
            TurnType.TURN_KEEP_L -> TurnKind.KEEP_LEFT
            TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_C -> TurnKind.ENTER_HIGHWAY
            TurnType.TURN_EXIT_IC_R, TurnType.TURN_EXIT_IC_L, TurnType.TURN_EXIT_IC_C -> TurnKind.EXIT_HIGHWAY
            TurnType.TURN_TUNNEL -> TurnKind.TUNNEL
            TurnType.TURN_TOLL -> TurnKind.TOLL
            TurnType.TURN_ARRIVE -> TurnKind.ARRIVE
            TurnType.TURN_EXIT_ROUNDABOUT -> TurnKind.ROUNDABOUT
            else -> if (t.name.contains("ROUNDABOUT")) TurnKind.ROUNDABOUT else TurnKind.OTHER
        }

        fun caution(t: CautionPointType?): Caution = when (t) {
            CautionPointType.SCHOOL_ZONE -> Caution.SCHOOL_ZONE
            CautionPointType.SPEED_BUMP -> Caution.SPEED_BUMP
            CautionPointType.ACCIDENT_PRONE_AREA -> Caution.ACCIDENT_AREA
            CautionPointType.RAILROAD_CROSSING -> Caution.RAILROAD
            CautionPointType.SLIPPERY_ROAD_ALERT -> Caution.SLIPPERY
            CautionPointType.NARROW_ROAD -> Caution.NARROW_ROAD
            CautionPointType.HIGHWAY_CONSTRUCTION -> Caution.CONSTRUCTION
            CautionPointType.WILDLIFE_CROSSING -> Caution.WILDLIFE
            CautionPointType.FOG_AREA -> Caution.FOG
            else -> Caution.OTHER
        }

        fun traffic(t: TrafficCongestionType?): Traffic = when (t) {
            TrafficCongestionType.FAST -> Traffic.FAST
            TrafficCongestionType.MODERATE -> Traffic.MODERATE
            TrafficCongestionType.SLOW -> Traffic.SLOW
            TrafficCongestionType.HEAVY -> Traffic.HEAVY
            else -> Traffic.UNKNOWN
        }

        fun gear(g: VehicleGear?): Gear = when (g) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.DRIVE
        }

        /** TripInfo.eta가 '남은 초'인지 '도착 시각'인지 문서에 없어 둘 다 받아들인다. */
        fun etaToRemainSec(eta: Int, fallback: Int): Int {
            if (eta <= 0) return fallback
            val nowSec = System.currentTimeMillis() / 1000
            return when {
                eta > 1_000_000_000 -> (eta - nowSec).toInt().coerceAtLeast(0)   // epoch 초
                eta > 24 * 3600 -> fallback
                else -> eta
            }
        }

        fun guessPlaceKind(text: String): String {
            val t = text.lowercase()
            return when {
                listOf("회사", "사옥", "본사", "오피스", "타워", "센터", "office", "hq").any { t.contains(it) } -> "office"
                listOf("마트", "이마트", "홈플러스", "코스트코", "시장", "mart", "market").any { t.contains(it) } -> "mart"
                listOf("충전", "charger", "주유").any { t.contains(it) } -> "charger"
                listOf("공항", "airport").any { t.contains(it) } -> "airport"
                listOf("학교", "초등", "어린이집", "유치원", "school").any { t.contains(it) } -> "school"
                listOf("집", "아파트", "home").any { t.contains(it) } -> "home"
                listOf("병원", "의원", "hospital", "clinic").any { t.contains(it) } -> "hospital"
                else -> "other"
            }
        }
    }
}
