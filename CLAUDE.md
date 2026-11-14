# ROAD NOVEL — Claude Code 작업 지침

이 파일은 Claude Code가 이 저장소에서 일할 때 따라야 하는 규칙과 설계입니다.
작업을 시작하기 전에 항상 이 파일을 먼저 읽으세요.

## 1. 앱 한 줄 소개

차량의 실제 주행(이동 거리, 정차)과 탑승자의 음성 선택에 따라 전개와 결말이 달라지고,
원하는 길이(10/20/30분)만큼 듣다가 다음 주행에서 이어 들을 수 있는 **차량용 인터랙티브 오디오 스토리 앱**.

- 대상 플랫폼: 현대자동차그룹 **Pleos Connect** (Android Automotive OS 기반)
- 1차 타깃: 어린이를 동반한 가족 (판타지·모험 장르)
- 시연 환경: Android Studio + **Pleos Connect Emulator**
- 일정: 11/15 서류 제출(선택 가산점: GitHub, APK, 시연 영상) → 12/3~4 최종 PT에서 에뮬레이터 시연

## 2. 가장 중요한 원칙 5가지

1. **항상 실행되는 상태 유지.** 모든 차량·음성·AI 기능은 인터페이스 뒤에 숨기고, **Mock 구현을 반드시 함께** 만든다. Pleos SDK가 없거나 에뮬레이터에서 값이 안 나와도 앱은 Mock으로 끝까지 시연 가능해야 한다.
2. **SDK API를 지어내지 않는다.** Pleos SDK의 클래스·메서드 이름은 공식 문서(https://document.pleos.ai)나 SDK 샘플에서 확인한 것만 쓴다. 확인이 안 되면 인터페이스와 `TODO(pleos-check)` 주석을 남기고 Mock으로 동작하게 둔다.
3. **이야기의 뼈대는 데이터, AI는 대사만.** 사건·분기·결말은 `assets/stories/*.json`에 미리 설계된 대로만 진행한다. LLM은 장면 사이의 짧은 추가 대사만 만들고, 실패하면 그냥 건너뛴다. (아동 대상이므로 안전 필터 필수 — 7장 참고)
4. **운전자 안전 우선.** 주행 중에는 화면 조작이 필요 없게 만든다(음성 우선). 내비 안내 음성이 나오면 이야기 소리를 줄이거나 멈춘다. Pleos의 Driving Restriction Policy·Driver Distraction 심사 가이드를 따른다.
5. **작은 단위로 확인.** 기능 하나를 만들 때마다 `./gradlew assembleDebug`와 단위 테스트를 통과시킨 뒤 다음으로 넘어간다.

## 3. 기술 스택

- Kotlin, Jetpack Compose, Coroutines/Flow
- Hilt (의존성 주입 — Pleos 공식 튜토리얼도 Hilt로 Vehicle 인스턴스를 주입함)
- Room (스토리 진행 저장), kotlinx.serialization (스토리 JSON 읽기)
- 화면: 가로형 차량 디스플레이 기준, 다크 테마, 큰 터치 영역(최소 76dp)

## 4. Pleos SDK 설정 (문서에서 확인한 내용)

- Maven 저장소 (`settings.gradle.kts`의 `dependencyResolutionManagement.repositories`):
  ```kotlin
  maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
  ```
- 의존성 그룹: `ai.pleos.playground` (예: `implementation("ai.pleos.playground:SpeechToText:<버전>")`)
  - 사용 모듈: Vehicle, NaviHelper, TextToSpeech, SpeechToText, LLM (+ 필요 시 FusedLocation)
  - **버전과 정확한 아티팩트 이름은 각 SDK의 "Importing SDK / Project Setup" 문서에서 최신값을 확인해서 넣는다.**
- 권한 예: `pleos.car.permission.STT_SERVICE` (SpeechToText). 다른 SDK의 권한도 각 문서의 Project Setup에서 확인해 `AndroidManifest.xml`에 추가한다.
- TextToSpeech는 `TextToSpeech(context, Mode.HYBRID)`로 만들고 `initialize()` → 사용 → `release()` 순서. HYBRID는 네트워크가 없으면 온디바이스로 전환된다(터널 대비).
- SpeechToText의 서버 인식은 `registerApp` 이 필요하다.
- Vehicle SDK는 기능 그룹(Brake, Odometer, TurnSignal 등)을 `vehicle.getXXX()`로 얻어 쓴다. 차종마다 지원이 다르므로 `checkXXXCapabilities` 계열 API로 먼저 지원 여부를 확인한다.
- **Vehicle SDK에는 속도 항목이 없다.** 속도·정차 판단은 AAOS 표준 `CarPropertyManager` + `VehiclePropertyIds.PERF_VEHICLE_SPEED`(또는 NaviHelper 주행 정보 이벤트)로 한다.
- 이동 거리: 1순위 Vehicle SDK **Odometer**, 2순위 AAOS `PERF_ODOMETER`, 3순위 FusedLocation 위치 변화 누적.
- **차량 데이터 — Vehicle SDK 2.0.3 jar(javap)와 에뮬레이터에서 직접 확인 (2026-11-14)** · 구현: `data/vehicle/PleosVehicleDataSource.kt`
  - 의존성 `ai.pleos.playground:Vehicle:2.0.3`. 권한 `pleos.car.permission.CAR_DRIVE`(주행거리·기어, SDK 어노테이션에 적힌 값), `VEHICLE_BINDING`은 SDK가 자동 추가.
  - 주행거리: `vehicle.getOdometer().getCurrentDistanceDriven(onOk, onFail)` + `registerDistanceDriven(DistanceDrivenListener)` → `onDistanceDrivenUpdated(Float?)` (km). ✔ 동작.
  - 기어: Pleos `getGear()`는 **deprecated ("use the Google GEAR_SELECTION property")** → AAOS `GEAR_SELECTION`(권한 `CAR_POWERTRAIN`)을 먼저 쓰고 Pleos는 예비. ✔ 콘솔 P↔D가 바로 반영됨.
  - 속도: Vehicle SDK에 `VehicleSpeedListener` 클래스는 있으나 등록하는 공개 API가 없음 → AAOS `PERF_VEHICLE_SPEED`(권한 `CAR_SPEED`, 실행 중 허용. AAOS에서는 "위치 정보" 권한 창으로 보임). 지원은 되지만 콘솔에 속도 카드가 없어 계속 0 → **0이 아닌 값이 한 번이라도 와야 속도를 믿고**, 그 전에는 기어로 판단.
  - capability: Vehicle SDK의 `CapabilityType`은 SUNROOF/AMBIENT_LIGHT 뿐 → 주행거리·기어는 "첫 읽기가 성공하면 지원"으로 판단. 둘 다 실패하면 자동으로 Mock.
  - 판단 규칙 (`effectiveSpeedKmh`): 기어 P → 정차(속도 0) / 속도 값이 있으면 속도 / 기어 D·R + 속도 모름 → 주행(정차 아님).
  - 주행거리계는 1km씩 한꺼번에 오른다 → 엔진은 0~50km 점프를 거리로 인정. 속도 없이 주행거리계만 있으면 평균 속도 보정을 끄고 30km/h 고정(10분 이야기 = 목표 5km).
  - 이 AAOS는 앱이 **user 10**(운전자 프로필)으로 돈다 → `adb shell pm grant`는 `--user 10`을 붙여야 함.
- **Virtual Console** (`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/)
  - 값 바꾸기 API (페이지가 쓰는 것): `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`, `PUT /vehicle/odometer?traveled_distance_km=N`, 상태 `GET /vehicle/state`, 목록 `GET /vehicle/catalog`.
  - **브라우저 세션이 살아 있어야 쓰기가 됨**: 없으면 `503 {"error":"emulator_inactive"}` → `POST /browser/session?client=이름`을 먼저 보내면 됨 (브라우저 탭을 열어 두면 자동).
  - 주행거리는 **줄일 수 없음** (지금 값보다 작으면 400). 늘리기만 가능.
  - 확인 결과 (10분 이야기): 기어 D + 주행거리 +1km → 다음 장면 ✔, 갈림길은 바로 질문 ✔, +3km 더 → 안개 다리 ✔, 기어 P → 5초 뒤 정차 이야기 ✔ (선택지 화면에서는 정차 이야기를 끼우지 않음).
- 남은 시간: NaviHelper(내비 연동) — 내비가 있는 차량에서만 완전 지원.
- **Pleos 음성 SDK 3.3.1 — 정리: `docs/Pleos_음성SDK_정리.md`, 아래는 SDK jar(javap)와 에뮬레이터(Pleos_Connect_v2.1.0, API 34)에서 직접 확인 (2026-11-14)**
  - 의존성: `ai.pleos.playground:TextToSpeech:3.3.1`, `ai.pleos.playground:SpeechToText:3.3.1`. 권한 `pleos.car.permission.TTS_SERVICE` / `STT_SERVICE` (설치 시 자동 허용됨).
  - 실제 import 위치: `ai.pleos.playground.tts.listener.{EventListener, OnInitializeListener, OnServerConnectionListener}`, `ai.pleos.playground.stt.listener.{ResultListener, OnInitializeListener, OnServerConnectionListener}`. Mode·ErrorType은 tts/stt 패키지에 각각 따로 있음.
  - **목소리 높이·속도 API 없음** → 모든 인물이 같은 목소리, 화면의 얼굴·이름으로 구분. (`setSpeakerType(String)`은 있으나 값이 문서에 없어 사용 안 함)
  - 모드는 기본 `ON_DEVICE`(앱 등록 불필요). `local.properties`의 `pleos.mode=HYBRID` + `pleos.clientId` / `pleos.clientSecret`이 있으면 `registerApp` (코드 준비됨, 미시험).
  - TTS(ON_DEVICE): `initialize` 성공 → `speak(text, true)` → `onDone` 정상 동작. 대사 길이에 비례해 약 4~7초. `onError`/응답 없음이면 그 줄은 자막만 보여 주고 넘어감.
  - STT(ON_DEVICE): `sendAudio(16kHz·16bit·mono PCM)` + `completeAudioSend()`로 인식 성공 ("오래된 성" → "오래된 성"). **숫자는 한글로 나올 수 있음** ("2번" → "두 번") → StoryEngine이 한글 수사를 숫자로 바꿔 비교.
  - `request()`(실제 마이크): `ReadyToStartRecognition`까지는 오지만 에뮬레이터의 호스트 마이크 입력이 꺼져 있으면 인식이 시작되지 않고 시간 초과 → 버튼/기본값.
  - Android 기본 `TextToSpeech`/`SpeechRecognizer`는 이 에뮬레이터에서 동작하지 않음 (TTS 엔진 없음, 인식 모델 파일 오류) → `VOICE_ENGINE`은 `"pleos"`가 기본.
  - 에뮬레이터 AVD: `fastboot.forceColdBoot=yes`(항상 cold boot), `hw.audioInput=yes`. 호스트 마이크는 실행 중 Extended Controls → Microphone에서 켜야 함 (설정 파일에 저장 안 됨).
  - 시연용: 디버그 빌드의 디버그 패널에서 선택지가 떠 있을 때 🎤 "오래된 성" / 🎤 "2번" 버튼 → 시험 녹음(`app/src/debug/assets/test_audio`)을 `sendAudio`로 보냄.

## 5. 폴더 구조 (목표)

```
app/src/main/
  assets/stories/            # 스토리 JSON (star_map_expedition.json 등)
  java/.../roadnovel/
    data/vehicle/            # VehicleDataSource (+ Pleos 구현, Mock 구현)
    data/navi/               # NaviDataSource (+ Pleos 구현, Mock 구현)
    data/save/               # Room: SavedProgressEntity, Dao, SaveRepository
    story/                   # StoryModels, StoryRepository, StoryEngine (순수 Kotlin)
    voice/                   # Narrator(TTS), VoiceInput(STT) (+ Pleos, Android 기본, Mock)
    ai/                      # DialogueGenerator (+ Pleos LLM, Mock), SafetyFilter
    audio/                   # AudioFocus·덕킹 처리, 효과음/배경음
    ui/                      # home, select, player, ending, debug
    di/                      # Hilt 모듈 (빌드 설정으로 Pleos/Mock 선택)
```

## 6. 핵심 인터페이스

```kotlin
interface VehicleDataSource {
    val odometerKm: Flow<Double?>   // 누적 주행거리(km). 모르면 null
    val speedKmh: Flow<Float?>      // 현재 속도. 모르면 null
}
// isStopped = speed가 0.5km/h 미만으로 5초 이상 유지

interface NaviDataSource {
    val remainingMinutes: Flow<Int?> // 목적지까지 남은 시간. 경로 없으면 null
}

interface Narrator {                 // TTS
    suspend fun speak(text: String, voice: VoiceStyle) // 끝날 때까지 suspend
    fun stop()
    fun setDucked(ducked: Boolean)
}

interface VoiceInput {               // STT
    suspend fun listen(timeoutSec: Int): String?        // 인식된 문장, 실패 시 null
}

interface DialogueGenerator {        // LLM
    suspend fun extraLines(context: StoryContext, hint: String): List<StoryLine> // 실패 시 빈 리스트
}
```

`MockVehicleDataSource`는 디버그 패널(속도 슬라이더, 정차 버튼, 시간 배속 ×1/×10)로 값을 바꿀 수 있어야 한다. **PT 시연에서 이 디버그 패널로 "차가 달리면 이야기가 진행되는 것"을 보여준다.** (에뮬레이터 자체의 차량 값 시뮬레이션이 되면 그것도 지원)

## 7. 스토리 데이터 형식 (assets/stories/*.json)

예시는 `star_map_expedition.json`. 주요 필드:

| 필드 | 뜻 |
|---|---|
| `start` | 첫 장면 id |
| `scenes.{id}.lines[]` | `who`(인물 id), `text`, 선택 `if`(`"has:아이템"`일 때만 읽음) |
| `scenes.{id}.weight` | 이 장면이 차지하는 거리 비중 (기본 1.0) |
| `scenes.{id}.optional` | true면 10분 모드에서 건너뜀 |
| `scenes.{id}.next` | 다음 장면 id |
| `scenes.{id}.choice` | `question`, `options[]`(`label`, `say`[인식 키워드], `next`, `setFlags`), `timeoutSec`, `defaultIndex` |
| `scenes.{id}.ending` | 결말 장면 (`id`, `name`, `badge`) |
| `scenes.{id}.gainItems` | 이 장면에서 얻는 아이템 (뒤 장면의 `if`에 쓰임) |
| `scenes.{id}.ai` | `enabled`, `hint` — LLM 추가 대사 생성 지점 |
| `interludes[]` | `trigger: "vehicle_stopped"`일 때 한 번씩 재생하는 짧은 장면 |

`optional` 장면에서 `next`로 이어질 때, 10분 모드에서는 그 장면을 건너뛰고 그 장면의 `next`로 바로 간다.

## 8. StoryEngine 규칙 (순수 Kotlin, 단위 테스트 필수)

1. **길이 계획**: 선택한 길이 `L`(10/20/30분). `L == 10`이면 optional 장면 제외.
2. **거리 목표**: `targetKm = L × 평균속도(km/분)`. 평균속도는 처음엔 0.5km/분(30km/h)으로 두고, 주행하면서 최근 5분 평균으로 계속 보정한다.
   `kmPerWeight = targetKm / (현재 위치부터 결말까지 가장 긴 경로의 weight 합 + 지나온 weight 합)`
3. **다음 장면으로 넘어가는 조건** — 둘 다 만족해야 함:
   - 현재 장면 대사를 다 읽었다
   - 장면 시작 후 이동 거리 ≥ `weight × kmPerWeight`
   - **선택지 장면은 예외**: 대사가 끝나면 거리를 기다리지 않고 바로 질문한다. 그 장면에서 못 채운 거리는 다음 장면으로 넘긴다 (다음 장면 필요 거리 = 넘어온 거리 + 다음 장면 거리). 그래서 전체 길이는 그대로 맞는다. (결말 장면은 자기 거리는 안 기다리지만, 넘어온 거리는 채운 뒤 끝난다.)
4. 대사는 끝났는데 거리가 부족하면: AI 추가 대사 → 그래도 남으면 배경음만 재생하며 기다린다.
5. **정차 처리**: 정차 상태가 되면 아직 안 쓴 interlude를 하나 재생한다. 단, 이번 주행(앱을 켠 뒤)에 아직 출발하지 않았으면 무시한다 (출발 전·이어 듣기 직후 주차 중).
6. **막힘 방지**: 기다린 시간이 `(weight × L / 전체weight) × 1.5분`을 넘으면 거리와 상관없이 다음 장면으로 간다 (꽉 막힌 길에서도 이야기가 멈추지 않게). 단, 기다린 시간은 이번 주행에서 차가 한 번이라도 움직인 뒤부터 센다 (출발 전 주차 중에는 넘어가지 않음).
7. **선택지**: 질문을 TTS로 읽고 → `listen(timeoutSec)` → `say` 키워드가 들어 있는지 확인 → 실패하면 한 번 더 묻고 → 그래도 실패하면 `defaultIndex`. 화면의 큰 버튼(동승자용)으로도 고를 수 있다.
8. **상태**(`StoryState`): storyId, lengthMin, sceneId, lineIndex, flags, items, choices 기록, 누적 거리, 경과 시간, endingId.

## 9. 저장·이어 듣기

- 장면이 바뀔 때마다 **자동 임시 저장**(`isTemp = true`) → 앱이 갑자기 꺼져도 복구.
- 사용자가 "그만", "이야기 끝내줘"라고 말하거나 종료 버튼을 누르면 → "지금까지 이야기를 저장할까요?" 질문 → "저장" 시 `isTemp = false`로 확정, "아니" 시 해당 기록 삭제.
- 앱 시작 시 저장된 기록이 있으면 홈 화면 첫 카드에 "이어 듣기"(장면 이름, 남은 분량)를 보여준다. 임시 저장만 있으면 "지난번에 갑자기 멈췄어요. 이어서 들을까요?"
- 결말에 도착하면 기록 삭제 + 받은 결말 배지를 별도 테이블에 저장(다시 들을 이유).

## 10. AI(LLM) 사용 규칙 — 아동 안전

- 프롬프트에 항상 넣을 것: 등장인물 설정, 현재 장면 요약, `hint`, "6세 이상 어린이용, 무섭거나 폭력적인 표현 금지, 2~3줄, 각 줄 40자 이내, 줄거리를 바꾸지 말 것".
- 응답 형식은 `인물id|대사` 줄 단위로 받아서 파싱. 형식이 틀리면 버린다.
- `SafetyFilter`: 금칙어 목록, 길이 제한, 등장하지 않는 인물 이름이면 버린다.
- 다음 장면의 AI 대사는 **현재 장면 재생 중에 미리 생성**해 둔다(네트워크 끊김 대비). 3초 안에 못 받으면 건너뛴다.
- **구현 (M6, 2026-11-14)**: `ai/` — `DialogueGenerator`(인터페이스), `PromptBuilder`, `ResponseParser`(`인물id|대사`만, 틀린 줄은 버림), `SafetyFilter`(금칙어·40자·최대 3줄·없는 인물·모르는 이름 부르기), `MockDialogueGenerator`(장면별로 미리 쓴 대사, 같은 필터 통과), `PleosDialogueGenerator`, `FallbackDialogueGenerator`(Pleos → 결과 없으면 Mock).
  - 장면에 들어가는 순간 그 장면의 AI 대사를 미리 생성 → 대사가 끝나고 필요할 때 **3초만** 기다리고 못 받으면 건너뜀.
  - **"✨ AI" 표시는 언어 모델(Pleos LLM)이 실제로 만든 대사에만** 붙인다 (`ExtraLines.byModel = true` → `LineSource.AI`). 미리 써 둔 대사는 `LineSource.EXTRA`로 표시 없이 읽는다. 출처는 디버그 패널 "마지막 AI 대사"에서 확인.
  - 엔진 선택: `app/build.gradle.kts`의 `AI_ENGINE` = `"mock"`(기본) / `"pleos"`.
- **Pleos LLM SDK — 공식 사용 안내 문서 없음, SDK jar(javap)와 에뮬레이터로 직접 확인 (2026-11-14)**
  - Maven: `ai.pleos.playground:LLM` 존재 (2.1.2 / 2.1.3.1 / **2.1.3.2** 최신).
  - API: `LLM(context, Mode.ON_DEVICE | Mode.SERVER)` → `initialize()` → `generateContent(prompt, ResultListener)`; `ResultListener.onResponse(String, Boolean)` / `onError(String)`. 그 밖에 `generateContentStream`, `startChat`/`sendMessage`, `setModelParameter(TEMPERATURE|MAX_TOKENS|MODEL_NAME)`, `registerApp(id, secret, OnServerConnectionListener)`. HYBRID 모드는 없음.
  - 권한: SDK 매니페스트에 `pleos.car.permission.LLM_SERVICE`가 주석으로만 있음 → 앱에 선언해 둠 (설치 시 허용됨).
  - **에뮬레이터(Pleos_Connect_v2.1.0) 결과: 응답 없음.** 서비스 로그 `[GAAS]OnDeviceLLMManagerImpl: startLLM ignored: on-device LLM is disabled`. → 기본값을 Mock으로 둠.
  - SERVER 모드는 `local.properties`의 `pleos.clientId` / `pleos.clientSecret`이 있으면 `registerApp` 후 시도하도록 코드만 준비 (미시험).

## 11. 오디오·안전

- AudioFocus를 요청하고, `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`(내비 안내 등)이면 볼륨을 낮추고, `LOSS_TRANSIENT`이면 일시정지했다가 돌아오면 그 줄부터 다시 읽는다.
- 주행 중 화면: 자막 한 줄, 진행 바, 선택지 버튼(최대 3개)만. 스크롤·텍스트 입력 없음.
- **구현·확인 (2026-11-14)**
  - AudioFocus: `audio/AudioFocus.kt` — 재생 시작 때 `AUDIOFOCUS_GAIN`(USAGE_MEDIA, CONTENT_TYPE_SPEECH, `setWillPauseWhenDucked(true)`), 끝낼 때 반납.
    - DUCK: 배경음 0.04로 + 낭독은 잠깐 멈춤 (Pleos TTS에는 볼륨 API가 없어 "줄이기" 대신 멈춤) → 돌아오면 그 줄부터 다시.
    - LOSS_TRANSIENT: 모두 멈춤 → GAIN에 그 줄부터 다시. LOSS: 멈춤 + "▶ 이어 듣기" 버튼.
    - 에뮬레이터: Pleos TTS(caas)가 대사를 읽어도 우리 포커스를 빼앗지 않음 ✔. 디버그 "🧭 내비 안내 끼어들기"(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, GAIN_TRANSIENT_MAY_DUCK) → AAOS는 우리에게 `LOSS_TRANSIENT`를 보냄 → 47자 대사가 2.3초에서 끊기고, 안내가 끝나면 같은 대사를 처음부터 다시 읽음 ✔.
  - 주행 중 화면 제한: `data/vehicle/DrivingRestriction.kt` — AAOS `CarUxRestrictionsManager`의 `isRequiresDistractionOptimization` (또는 Pleos 차량이 달리는 중). Pleos Driving Restriction Policy 문서는 자동으로 읽히지 않아 AAOS 표준 + 이 장의 규칙으로 점검.
    - 주행 중: 디버그 패널 숨김(그리고 디버그 빌드에서만 보임), 이야기 선택은 "출발! (추천 길이)" 하나만, 속도 권한 설명·권한 창은 정차 때까지 미룸. 재생·선택지·저장 대화상자는 그대로(큰 버튼, 스크롤·입력 없음).
    - 에뮬레이터: Virtual Console 기어 D + `PUT /vehicle/controls?speed_kph=40` → 주행 상태 MOVING, 제한 0x50 → 앱이 주행용 화면으로 바뀜 ✔, 정차하면 원래대로 ✔. (콘솔 화면에 속도 카드는 없지만 이 API로 속도를 바꿀 수 있음)
    - 디버그 "🚦 주행 중 화면 미리보기"로 PT용 캡처 가능 (끄기: 로고 길게 누르기).
  - **앱 심사 기준 대응 (2026-11-14)** — `docs/Pleos_주행제한_심사기준_정리.md`는 저장소에 없어서 요청받은 5개 항목만 처리
    - "게임"으로 보이지 않게: 들어 본 결말 목록(홈)·결말 그림·남은 결말 수(결말 화면)는 **정차 중에만**. 화면 글자도 "에피소드 완성"→"이야기 끝", "모은 결말 배지"→"들어 본 결말". README는 "오디오 이야기 앱".
    - AI 콘텐츠 신고: AI 추가 대사 자리(`LineSource.AI`/`EXTRA`)의 줄에 **정차 중에만** "🚩 이 대사 신고하기". Room `content_reports`(DB v2, `MIGRATION_1_2` — 기존 저장 유지 확인)에 이야기·장면·인물·대사·출처만 기록(개인정보·위치 없음). 서버가 생기면 `ReportUploader`로 `pending()`을 보내고 `markSent()`.
    - SafetyFilter: `UNSAFE_IN_CAR`(창밖으로 손·머리·몸 내밀기, 창문 열기, 벨트 풀기, 일어서기)·`UNSAFE_DRIVING`(빨리 달려, 과속, 신호 무시, 빨간불, 추월, 끼어들기, 운전자 재촉). 스토리 44줄도 같은 기준으로 테스트 — "요정들이 고개를 내밀었어요"를 "빼꼼 얼굴을 보였어요"로 고침. 부르는 말 검사가 "괜찮아."를 이름으로 오해하던 문제도 고침.
    - 로딩 표시: `LoadingAfterDelay` — 2초 넘게 반응이 없으면 로딩 표시 (첫 대사 준비, 저장하고 끝내기, Pleos 차량 연결). 에뮬레이터에서는 세 작업 모두 2초 안에 끝나 실제로 로딩이 뜨는 장면은 보지 못함.
    - 권한 목록: README "앱이 쓰는 권한" 표 (병합된 매니페스트 기준 9개).
- 위치 좌표는 저장하지 않는다. 거리(km)만 저장한다(위치정보 관련 위험 최소화).

## 12. 개발 순서 (마일스톤)

- **M0** 프로젝트 생성, 빌드 확인, 폴더 구조, Hilt
- **M1** 스토리 JSON 로딩 + StoryEngine + 단위 테스트 (Mock 차량 데이터로)
- **M2** 화면: 홈 → 이야기 선택(장르, 길이, 내비 남은 시간 기반 추천) → 재생 → 결말
- **M3** 저장·이어 듣기(Room), 종료 확인 대화
- **M4** 음성: Android 기본 TTS/STT로 먼저 → Pleos TTS/STT로 교체
- **M5** 차량 데이터: Pleos Vehicle(Odometer) + 속도(CarPropertyManager) + NaviHelper 남은 시간
- **M6** Pleos LLM 추가 대사 + SafetyFilter
- **M7** 시연 다듬기: 디버그 패널, 효과음/배경음, 시연 시나리오 스크립트
  - ✅ (2026-11-14) 시연 모드: 디버그 패널 "🎬 시연 시작" → 10분 이야기를 자동 진행 (`RoadNovelViewModel.runDemo`). 출발 → 장면 → 정차 이야기 → 선택(버섯 마을) → 그만하기·저장 → 이어 듣기 → 선택(노래) → 결말. 에뮬레이터에서 **1분 51초**. 시간만 재지 않고 화면 상태를 보고 다음 단계로 넘어감.
  - ✅ README.md (심사용, 팀원 개인정보 없음), docs/screenshots (디버그 패널 없는 화면 위주)
  - ✅ 효과음/배경음: 외부 음원 없이 `tools/make_sounds.py`로 직접 합성 (`res/raw`), README에 출처. `audio/SoundEffects` — SoundPool(효과음) + MediaPlayer(배경음 반복). 대사를 읽는 동안 배경음 0.12, 기다릴 땐 0.30. 그만하기 대화상자에서 일시정지, 결말에서 끝 + 팡파르, 🔇 자막만이면 모두 끔. 시연 모드에서 로그로 순서 확인.
  - ⬜ AudioFocus·덕킹(11장): 아직 안 함. Pleos TTS는 다른 앱(caas)이 소리를 내므로, 우리 앱이 포커스를 잡으면 TTS와 서로 어떻게 반응하는지 먼저 에뮬레이터에서 확인해야 함.

## 13. 작업할 때 지킬 것

- 한 번에 한 마일스톤. 끝나면 무엇을 했는지, 어떻게 확인하는지 짧게 보고한다.
- 빌드가 깨진 상태로 끝내지 않는다.
- 비밀값(API 키 등)은 `local.properties`에 두고 커밋하지 않는다. `.gitignore` 확인.
- 코드 주석은 쉬운 한국어로 짧게.
