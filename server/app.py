"""RoadTalk 서버.
앱이 보낸 '지금 상황'으로 회화 문항을 만들고(/v1/generate), 인식된 글자를 첨삭하고(/v1/feedback),
진행 방향 앞쪽의 장소를 OpenStreetMap에서 찾는다(/v1/landmarks).
음성은 받지 않는다. 글자만 받는다.
"""
import json, math, os, time, logging, threading
from collections import defaultdict, deque
from pathlib import Path

import httpx
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

log = logging.getLogger("roadtalk")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

LLM_BASE = os.environ.get("LLM_BASE", "http://100.89.128.73:8791/v1")
LLM_MODEL = os.environ.get("LLM_MODEL", "gemini-3.5-flash")
LLM_MODEL_FAST = os.environ.get("LLM_MODEL_FAST", "gemini-3.5-flash-lite")
OVERPASS = os.environ.get("OVERPASS", "https://overpass-api.de/api/interpreter,https://overpass.private.coffee/api/interpreter,https://maps.mail.ru/osm/tools/overpass/api/interpreter").split(",")
RATE_PER_MIN = int(os.environ.get("RATE_PER_MIN", "40"))
DAILY_LIMIT = int(os.environ.get("DAILY_LIMIT", "4000"))


def llm_key() -> str:
    k = os.environ.get("GEMINI_PROXY_API_KEY", "")
    if not k:
        raise RuntimeError("no key")
    return k


app = FastAPI(title="RoadTalk", docs_url=None, redoc_url=None)
http = httpx.Client(timeout=httpx.Timeout(12.0, connect=4.0))

_hits: dict[str, deque] = defaultdict(deque)
_day = {"date": "", "n": 0}
_lock = threading.Lock()


def guard(req: Request, cost: int = 1):
    ip = req.headers.get("x-forwarded-for", req.client.host if req.client else "?").split(",")[0].strip()
    now = time.time()
    with _lock:
        q = _hits[ip]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= RATE_PER_MIN:
            raise HTTPException(429, "too many requests")
        q.append(now)
        today = time.strftime("%Y-%m-%d")
        if _day["date"] != today:
            _day.update(date=today, n=0)
        if _day["n"] + cost > DAILY_LIMIT:
            raise HTTPException(429, "daily limit")
        _day["n"] += cost


LANG_NAME = {"en": "American English", "es": "Mexican Spanish", "fr": "Canadian French"}
LEVEL = {"beginner": "beginner (A1-A2, 4-7 words)", "intermediate": "intermediate (B1, 6-12 words)", "advanced": "advanced (B2-C1, natural idioms)"}


def _parse(text: str) -> dict:
    a, b = text.find("{"), text.rfind("}")
    if a < 0 or b <= a:
        raise ValueError("no json")
    return json.loads(text[a:b + 1])


def chat(messages, model=None, temperature=0.7, max_tokens=2000) -> dict:
    r = http.post(f"{LLM_BASE}/chat/completions",
                  headers={"Authorization": f"Bearer {llm_key()}"},
                  json={"model": model or LLM_MODEL, "messages": messages, "temperature": temperature,
                        "max_tokens": max_tokens, "response_format": {"type": "json_object"}})
    r.raise_for_status()
    text = r.json()["choices"][0]["message"]["content"]
    try:
        return _parse(text)
    except Exception:
        log.warning("bad json from llm: %r", text[:300])
        raise


class TurnContext(BaseModel):
    phase: str = "MAIN"
    window: str = "LONG"
    topic: str = "smalltalk"
    topicKo: str = ""
    why: str = ""
    destination: str = ""
    destinationKind: str = ""
    nextTurn: str = ""
    traffic: str = ""
    raining: bool = False
    passengers: bool = False
    landmark: str = ""
    landmarkCategory: str = ""


class GenerateReq(BaseModel):
    lang: str = "en"
    level: str = "intermediate"
    ctx: TurnContext
    avoid: list[str] = Field(default_factory=list)


GEN_SYSTEM = """You are RoadTalk, a spoken conversation coach for a Korean driver commuting by car.
The driver cannot look at the screen. Everything is heard, so keep it short and speakable.
Make ONE practice item about what the driver is experiencing right now on the road (the context).
Never ask the driver to look sideways, read signs, or do anything that takes attention off driving.
Return JSON only:
{"kind":"respond"|"repeat","promptKo":"Korean instruction (1 sentence, polite 해요체, under 45 Korean chars)",
 "target":"model sentence in the target language","hintKo":"Korean meaning of target (short)",
 "expected":"same as target","long":true|false}
Rules:
- window SHORT -> kind "repeat", a short phrase (<=6 words), long=false.
- window LONG -> usually kind "respond": a mini role-play situation in promptKo the driver answers aloud; long=true.
- Tie it to the context concretely (destination type, upcoming turn, traffic jam, rain, landmark ahead, passengers).
- Use natural everyday expressions a native speaker would say. No quotes inside target.
- Do not repeat any sentence in the avoid list.
- The target sentence must contain NO Korean characters. For Korean place names, use the official English name if well known
  (e.g. 판교역 -> Pangyo Station, 국가기록원 -> the National Archives) or a short romanization; promptKo and hintKo keep the Korean name."""


@app.post("/v1/generate")
def generate(body: GenerateReq, request: Request):
    guard(request)
    c = body.ctx
    ctx_lines = [f"phase: {c.phase}", f"window: {c.window}", f"topic: {c.topic} ({c.topicKo}) because {c.why}"]
    if c.destination: ctx_lines.append(f"destination: {c.destination} (type {c.destinationKind})")
    if c.nextTurn: ctx_lines.append(f"next navigation instruction: {c.nextTurn}")
    if c.traffic: ctx_lines.append(f"traffic: {c.traffic}")
    if c.raining: ctx_lines.append("it is raining (wipers on)")
    if c.passengers: ctx_lines.append("there are passengers in the car")
    if c.landmark: ctx_lines.append(f"landmark straight ahead: {c.landmark} ({c.landmarkCategory})")
    user = (f"Target language: {LANG_NAME.get(body.lang, 'English')}. Level: {LEVEL.get(body.level, LEVEL['intermediate'])}.\n"
            "Context:\n- " + "\n- ".join(ctx_lines) + "\nAvoid: " + json.dumps(body.avoid[-8:], ensure_ascii=False))
    t0 = time.time()
    out = None
    for model in ((LLM_MODEL_FAST if c.window == "SHORT" else LLM_MODEL), LLM_MODEL_FAST):
        try:
            out = chat([{"role": "system", "content": GEN_SYSTEM}, {"role": "user", "content": user}], model=model, temperature=0.8)
            break
        except Exception as e:
            log.warning("generate failed (%s): %s", model, e)
    if out is None:
        raise HTTPException(502, "llm failed")
    log.info("generate %s %s %.1fs -> %s", body.lang, c.topic, time.time() - t0, out.get("target"))
    kind = out.get("kind", "respond")
    if c.window == "SHORT":
        kind = "repeat"
    return {"kind": kind if kind in ("respond", "repeat") else "respond",
            "promptKo": str(out.get("promptKo", ""))[:120], "target": str(out.get("target", ""))[:160],
            "hintKo": str(out.get("hintKo", ""))[:120], "expected": str(out.get("expected") or out.get("target", ""))[:160],
            "long": bool(out.get("long", c.window == "LONG"))}


class FeedbackReq(BaseModel):
    lang: str = "en"
    level: str = "intermediate"
    promptKo: str
    expected: str
    heard: str


FB_SYSTEM = """You coach a Korean learner who just answered aloud while driving. The answer came from speech recognition, so ignore punctuation and capitalization.
Judge whether the learner's sentence works for the situation. Accept other natural answers, not only the model answer.
Return JSON only: {"ok":true|false,"better":"one natural sentence in the target language (fix grammar, keep the learner's idea)","commentKo":"one short Korean tip in polite 해요체, under 40 Korean chars, say what to fix (e.g. 관사 the가 빠졌어요)"}
If the answer is already natural, set ok=true, better = the learner's sentence cleaned up, and praise briefly."""


@app.post("/v1/feedback")
def feedback(body: FeedbackReq, request: Request):
    guard(request)
    user = (f"Target language: {LANG_NAME.get(body.lang, 'English')}. Level: {body.level}.\n"
            f"Situation (Korean): {body.promptKo}\nModel answer: {body.expected}\nLearner said: {body.heard}")
    t0 = time.time()
    try:
        out = chat([{"role": "system", "content": FB_SYSTEM}, {"role": "user", "content": user}], model=LLM_MODEL_FAST, temperature=0.3)
    except Exception as e:
        log.warning("feedback failed: %s", e)
        raise HTTPException(502, "llm failed")
    log.info("feedback %.1fs '%s' -> '%s'", time.time() - t0, body.heard, out.get("better"))
    return {"ok": bool(out.get("ok")), "better": str(out.get("better", ""))[:160], "commentKo": str(out.get("commentKo", ""))[:100], "followUp": ""}


# ---------------- 앞쪽 장소(OpenStreetMap) ----------------

CATS = {
    "amenity": ["hospital", "clinic", "school", "university", "library", "police", "bank", "cafe", "fuel", "charging_station", "townhall", "post_office", "cinema"],
    "railway": ["station"], "leisure": ["park", "stadium"], "shop": ["department_store", "mall", "supermarket"],
    "tourism": ["museum", "hotel", "attraction"],
}
_lm_cache: dict[str, tuple[float, list]] = {}

# 서울·분당 일대(37.33~37.70N, 126.80~127.20E)는 미리 받아 둔 OpenStreetMap 장소를 쓴다(공개 Overpass가 느리다).
_BBOX = (37.33, 126.80, 37.70, 127.20)
_grid: dict[tuple[int, int], list] = defaultdict(list)
try:
    for n, c, la, lo in json.load(open(Path(__file__).parent / "data" / "pois.json", encoding="utf-8")):
        _grid[(int(la * 100), int(lo * 100))].append({"name": n, "category": c, "lat": la, "lon": lo})
    log.info("local pois loaded: %d cells", len(_grid))
except Exception as e:
    log.warning("no local pois: %s", e)


def _local(lat, lon):
    gy, gx = int(lat * 100), int(lon * 100)
    out = []
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            out += _grid.get((gy + dy, gx + dx), [])
    return out


def _bearing(lat1, lon1, lat2, lon2):
    y = math.sin(math.radians(lon2 - lon1)) * math.cos(math.radians(lat2))
    x = math.cos(math.radians(lat1)) * math.sin(math.radians(lat2)) - math.sin(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.cos(math.radians(lon2 - lon1))
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def _dist(lat1, lon1, lat2, lon2):
    dy = (lat1 - lat2) * 111320
    dx = (lon1 - lon2) * 111320 * math.cos(math.radians((lat1 + lat2) / 2))
    return math.hypot(dx, dy)


@app.get("/v1/landmarks")
def landmarks(lat: float, lon: float, heading: float = 0.0, request: Request = None):
    guard(request)
    key = f"{lat:.3f},{lon:.3f}"
    now = time.time()
    if _grid and _BBOX[0] <= lat <= _BBOX[2] and _BBOX[1] <= lon <= _BBOX[3]:
        raw = _local(lat, lon)
    elif key in _lm_cache and now - _lm_cache[key][0] < 3600:
        raw = _lm_cache[key][1]
    else:
        parts = []
        for k, vals in CATS.items():
            parts.append(f'nwr(around:700,{lat},{lon})["{k}"~"^({"|".join(vals)})$"]["name"];')
        q = f"[out:json][timeout:10];({''.join(parts)});out center 60;"
        try:
            r = None
            for url in OVERPASS:
                try:
                    r = http.post(url, data={"data": q}, headers={"User-Agent": "RoadTalk/0.1 (contest demo)"})
                    r.raise_for_status()
                    break
                except Exception as e:
                    log.warning("overpass %s failed: %s", url, e)
                    r = None
            if r is None:
                raise RuntimeError("all overpass failed")
            raw = []
            for el in r.json().get("elements", []):
                tags = el.get("tags", {})
                c = el.get("center") or {"lat": el.get("lat"), "lon": el.get("lon")}
                if c.get("lat") is None:
                    continue
                cat = next((tags[k] for k in CATS if k in tags), "place")
                raw.append({"name": tags.get("name:ko") or tags.get("name"), "category": cat, "lat": c["lat"], "lon": c["lon"]})
            _lm_cache[key] = (now, raw)
        except Exception as e:
            log.warning("overpass failed: %s", e)
            raw = []
    out = []
    for p in raw:
        d = _dist(lat, lon, p["lat"], p["lon"])
        if d < 60:
            continue
        diff = (_bearing(lat, lon, p["lat"], p["lon"]) - heading + 540) % 360 - 180
        if abs(diff) > 35:  # 정면 부채꼴(±35°)만: 옆을 볼 필요 없는 것
            continue
        out.append({"name": p["name"], "category": p["category"], "distanceM": int(d), "side": "ahead"})
    # 눈에 잘 띄는 큰 장소(역·병원·공원·학교·백화점)를 은행·편의 시설보다 앞에 둔다.
    weight = {"station": 0, "hospital": 0, "university": 0, "department_store": 0, "mall": 0, "stadium": 0, "museum": 0, "park": 1,
              "school": 1, "library": 1, "townhall": 1, "hotel": 1, "attraction": 1, "cinema": 1, "police": 2, "supermarket": 2,
              "charging_station": 2, "fuel": 2, "post_office": 2, "clinic": 3, "bank": 3, "cafe": 3}
    out = [o for o in out if o["distanceM"] <= 800 and "층" not in o["name"]]
    out.sort(key=lambda x: (weight.get(x["category"], 2), x["distanceM"]))
    seen, uniq = set(), []
    for o in out:
        if o["name"] in seen:
            continue
        seen.add(o["name"]); uniq.append(o)
    return {"items": uniq[:5]}


@app.get("/v1/health")
def health():
    return {"ok": True, "model": LLM_MODEL, "today": _day}
