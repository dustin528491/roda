# 로드토크 (RoadTalk)

**출퇴근길 앞에 보이는 도로·표지·건물을 교재로 삼고, 남은 운전 시간에 맞춰 수업을 그때그때 짜 주는 차량 전용 외국어 회화 코치**

현대자동차그룹 차량용 앱 개발 공모전 출품작입니다. Pleos Connect SDK 5종(NaviHelper, FusedLocation, Vehicle, SpeechToText, TextToSpeech)을 모두 씁니다.

## 무엇을 하나

| 기능 | 쓰는 SDK | 설명 |
|---|---|---|
| 운전 부하 맞춤 말하기 타이밍 | NaviHelper(회전 안내·주의 지점·과속 카메라), Vehicle(방향지시등·기어) | 회전 300m 전, 방향지시등 작동 중에는 말을 멈추고, 어린이 보호구역에서는 듣기만 합니다. 다음 회전까지 남은 거리로 질문 길이를 정합니다. |
| 도착 시간 맞춤 커리큘럼 | NaviHelper(도착 예정 시간·남은 거리·교통 상황) | 출발할 때 워밍업·본 대화·복습으로 나누고, 막혀서 늦어지면 대화를 더합니다. 목적지 2km 전부터는 오늘 틀린 표현만 복습합니다. |
| 창밖 주제 엔진 | NaviHelper, Vehicle(와이퍼·실내 온도·배터리), FusedLocation(진행 방향) | 목적지 종류(회사→회의 표현), 다음 교차로, 정체, 비, 진행 방향 정면의 랜드마크로 주제를 고릅니다. |
| 같은 길 반복 복습 | FusedLocation(구역 알림 addProximityAlert) | 표현을 배운 장소를 기억했다가 다음 날 같은 곳을 지날 때 다시 묻습니다. |
| 동승자 모드 | Vehicle(좌석 착석·안전벨트) | 동승자가 있으면 함께 하는 퀴즈로 바꿉니다. |
| 음성 대화 | SpeechToText(ON_DEVICE), TextToSpeech | 원어 문장과 한국어 설명을 로케일을 바꿔 가며 들려주고, 발화를 인식해 목표 문장과 비교합니다. |

- 지원 언어: 영어(en-US), 스페인어(es-MX), 프랑스어(fr-CA). 모두 Pleos 음성 SDK 지원 로케일입니다.
- 음성은 차 밖으로 보내지 않습니다. 인식은 Pleos SpeechToText SDK로 하고, 첨삭에는 **인식된 글자만** 서버로 보냅니다.
- 서버가 없거나 인터넷이 끊기면 앱에 넣어 둔 문장 81개(영·스·프)로 계속합니다.

## 실행 방법

1. `apk/RoadTalk_release.apk` 를 Pleos Connect 에뮬레이터에 설치합니다.
   ```
   adb install -r -g apk/RoadTalk_release.apk
   ```
2. 앱 목록에서 **로드토크**를 실행하고 권한을 허용합니다(위치·내비·음성·차량 조회).
3. 내비에 목적지가 잡혀 있으면 **출발하며 시작**, 없으면 **데모 주행으로 시작**을 누릅니다.
   - 데모 주행은 분당 정자동 → 강남대로 출근길(41분, 중간 정체·비)을 8배 빠르게 재현합니다. 내비·위치 값만 대신 넣고, 와이퍼·방향지시등 등 차량 값은 Virtual Console에서 바꾸면 그대로 반영됩니다.
   - 에뮬레이터에 마이크 입력이 없으면 데모 주행 중에는 시연용 답변으로 이어 갑니다. 설정 > 음성 입력에서 고를 수 있습니다.
4. 도착(또는 끝내기)하면 오늘의 표현 카드와 다시 볼 표현이 나옵니다.

## 테스트 환경

- Pleos Connect Emulator **v2.1.0** (Android API 34, `system-images;android-34;pleos_car_emulator;x86_64`)
- 화면 규격: **2560 × 1440, 160dpi** (Connect-S-17, 앱 창 가로 1682 × 1210). 분할 화면 세로 창(840dp)에서는 한 줄 배치로 바뀝니다.
- 디자인: Pleos Connect 디자인 가이드의 색·글자 토큰과, 대화형 기본 앱인 Gleo AI의 화면 형식(가운데 정렬, 둥근 패널, 상태별 색 알약)을 따릅니다.
- Pleos Connect SDK: Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, SpeechToText 3.3.1, TextToSpeech 3.3.1
- minSdk 28, targetSdk 34, Kotlin 2.0.21, Jetpack Compose, Gradle 8.11.1, AGP 8.7.3
- 서버: `https://roadtalk.dustinlab.duckdns.org` (문항 생성·첨삭·앞쪽 장소). 소스는 `server/` 에 있습니다.

## 빌드

```
./gradlew :app:assembleRelease
```

서명 키는 저장소에 없습니다. `signing.properties` 가 없으면 디버그 키로 서명됩니다.

## 구성

```
app/src/main/java/ai/roadtalk/
  car/PleosCar.kt      Pleos 내비·위치·차량 SDK → 하나의 '지금 상황'
  car/DemoDrive.kt     시연용 주행 시나리오 4종
  voice/Voice.kt       Pleos TTS·STT (ON_DEVICE)
  lesson/Brain.kt      말하기 게이트, 도착 시간 맞춤 커리큘럼, 창밖 주제 고르기
  lesson/Bank.kt       오프라인 문장 꾸러미
  lesson/Store.kt      일치도 계산, 장소–표현 학습 기록
  net/Api.kt           서버 호출(글자만)
  AppModel.kt          수업 진행
  ui/                  출발·주행 중·도착·설정 화면
server/                FastAPI 서버(문항 생성·첨삭: LLM, 앞쪽 장소: OpenStreetMap)
```

## APK

- `apk/RoadTalk_release.apk`

장소 정보 © OpenStreetMap contributors (ODbL).
