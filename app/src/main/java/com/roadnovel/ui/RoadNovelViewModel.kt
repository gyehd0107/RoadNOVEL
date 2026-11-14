package com.roadnovel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadnovel.data.navi.MockNaviDataSource
import com.roadnovel.data.navi.NaviDataSource
import com.roadnovel.data.save.EarnedBadge
import com.roadnovel.data.save.SavedProgress
import com.roadnovel.data.save.SaveRepository
import com.roadnovel.data.vehicle.MockVehicleDataSource
import com.roadnovel.data.vehicle.Gear
import com.roadnovel.data.vehicle.PleosVehicleDataSource
import com.roadnovel.data.vehicle.effectiveSpeedKmh
import com.roadnovel.data.vehicle.isMoving
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import com.roadnovel.story.ChoiceResult
import com.roadnovel.story.EngineAction
import com.roadnovel.story.Ending
import com.roadnovel.story.LineSource
import com.roadnovel.story.Phase
import com.roadnovel.story.Story
import com.roadnovel.story.StoryEngine
import com.roadnovel.story.StoryRepository
import com.roadnovel.story.VoiceStyle
import com.roadnovel.voice.Narrator
import com.roadnovel.ai.DialogueGenerator
import com.roadnovel.ai.ExtraLines
import com.roadnovel.audio.Sfx
import com.roadnovel.audio.SoundEffects
import com.roadnovel.ai.StoryContext
import com.roadnovel.story.StoryLine
import kotlinx.coroutines.Deferred
import com.roadnovel.voice.SubtitleNarrator
import com.roadnovel.voice.VoiceInput
import com.roadnovel.voice.TestAudioInput
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class ScreenId { HOME, SELECT, PLAYER, ENDING }

/** 차량 데이터 출처 (디버그 패널에서 고름) */
enum class VehicleSource { MOCK, PLEOS }

data class UiState(
    val screen: ScreenId = ScreenId.HOME,
    val stories: List<Story> = emptyList(),
    val selectedStoryId: String? = null,
    val lengthMin: Int = 20,
    /** 사용자가 길이를 직접 골랐으면 내비 추천으로 덮어쓰지 않음 */
    val lengthTouched: Boolean = false,
    val etaMin: Int? = null,
    /** Mock 속도 슬라이더 값 */
    val speedKmh: Float = 0f,
    val vehicleSource: VehicleSource = VehicleSource.MOCK,
    /** 지금 출처에서 읽은 값 (화면 표시용) */
    val liveSpeedKmh: Float? = 0f,
    val gear: Gear? = null,
    val odometerKm: Double? = null,
    val moving: Boolean = false,
    /** Pleos 차량 연결 상태 한 줄 */
    val pleosStatus: String = "",
    val timeScale: Int = 10,
    val debugOpen: Boolean = false,
    /** 디버그 패널을 완전히 숨김 (스크린샷·실제 사용용). 로고를 길게 누르면 다시 보임 */
    val debugHidden: Boolean = false,
    val player: PlayerUi? = null,
    val ending: EndingUi? = null,
    val toast: String? = null,
    /** 홈 화면 이어 듣기 카드 (저장 기록이 없으면 null) */
    val resume: ResumeUi? = null,
    val badges: List<EarnedBadge> = emptyList(),
    /** "저장할까요?" 대화상자 */
    val quitDialog: Boolean = false,
    /** false면 소리 없이 자막만 */
    val soundOn: Boolean = true,
    /** 디버그: 마이크 대신 보낼 수 있는 시험 녹음 이름들 (assets/test_audio 폴더의 .pcm 파일) */
    val testAudios: List<String> = emptyList(),
    /** 디버그: 마지막 AI 대사를 어디서 받았는지 */
    val aiStatus: String = "-",
    val loadError: String? = null,
) {
    val recommendedMin: Int? get() = etaMin?.let(::recommendLength)
    val selectedStory: Story? get() = stories.firstOrNull { it.id == selectedStoryId }
}

/** 내비 남은 시간으로 이야기 길이 추천 (프로토타입과 같은 기준) */
fun recommendLength(etaMin: Int): Int = when {
    etaMin <= 14 -> 10
    etaMin <= 25 -> 20
    else -> 30
}

data class ResumeUi(
    val storyId: String,
    val storyTitle: String,
    val sceneTitle: String,
    val lengthMin: Int,
    val choices: List<String>,
    val totalKm: Double,
    val remainingMin: Int,
    /** 임시 저장만 있음 = 지난번에 갑자기 멈춤 */
    val isTemp: Boolean,
)

data class Caption(val who: String, val name: String, val text: String, val source: LineSource)

data class ChoiceUi(
    val question: String,
    val options: List<String>,
    val defaultIndex: Int,
    val secondsLeft: Int,
    val attempt: Int,
)

data class DebugStats(
    val totalKm: Double = 0.0,
    val elapsedSec: Double = 0.0,
    val avgKmh: Double = 30.0,
    val kmPerWeight: Double = 0.0,
    val itemsAndFlags: String = "-",
)

data class PlayerUi(
    val story: Story,
    val lengthMin: Int,
    val sceneId: String,
    val path: List<String>,
    val caption: Caption? = null,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val progressRight: String = "",
    val choice: ChoiceUi? = null,
    val stats: DebugStats = DebugStats(),
)

data class EndingUi(
    val ending: Ending,
    val storyTitle: String,
    val totalKm: Double,
    val elapsedSec: Double,
    val choices: List<String>,
    val unseenCount: Int,
)

@HiltViewModel
class RoadNovelViewModel @Inject constructor(
    private val repository: StoryRepository,
    private val car: MockVehicleDataSource,
    private val pleosVehicle: PleosVehicleDataSource,
    private val mockNavi: MockNaviDataSource,
    private val navi: NaviDataSource,
    private val saves: SaveRepository,
    private val narrator: Narrator,
    private val voiceInput: VoiceInput,
    private val dialogue: DialogueGenerator,
    private val sound: SoundEffects,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** "자막만" 모드일 때 쓰는 조용한 낭독기 */
    private val silent = SubtitleNarrator()
    private val activeNarrator: Narrator get() = if (_ui.value.soundOn) narrator else silent

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var engine: StoryEngine? = null
    private var playJob: Job? = null
    private var toastJob: Job? = null
    private var choiceAnswer: CompletableDeferred<Int>? = null
    /** 장면에 들어갈 때 미리 만들어 두는 AI 대사 (장면 id → 생성 작업) */
    private val aiPrefetch = mutableMapOf<String, Deferred<ExtraLines>>()
    /** 방금 선택 효과음을 냈으면 바로 이어지는 장면 차임은 생략 (두 소리가 겹치지 않게) */
    private var skipNextSceneChime = false
    private var latestSave: SavedProgress? = null
    /** "저장할까요?" 대화상자가 떠 있는 동안 이야기를 멈춤 */
    private val paused = MutableStateFlow(false)

    // 차량 값 최신본 (VehicleDataSource 흐름에서 받음)
    private var speed: Float? = 0f
    private var odometer: Double? = null
    private var gear: Gear? = null
    private val source = MutableStateFlow(VehicleSource.MOCK)

    init {
        car.timeScale = _ui.value.timeScale.toDouble()
        // 시험 녹음은 디버그 빌드에만 들어 있다 (app/src/debug/assets/test_audio)
        if (voiceInput is TestAudioInput) {
            val names = runCatching { appContext.assets.list(TEST_AUDIO_DIR).orEmpty().filter { it.endsWith(".pcm") } }.getOrDefault(emptyList())
            _ui.update { it.copy(testAudios = names.map { it.removeSuffix(".pcm") }) }
        }
        viewModelScope.launch {
            runCatching { repository.listStories() }
                .onSuccess { list -> _ui.update { it.copy(stories = list, selectedStoryId = list.firstOrNull()?.id) } }
                .onFailure { e -> _ui.update { it.copy(loadError = e.message) } }
        }
        viewModelScope.launch {
            val storiesFlow = _ui.map { it.stories }.distinctUntilChanged()
            saves.latestProgress.combine(storiesFlow) { save, stories -> save to stories }.collect { (save, stories) ->
                latestSave = save
                _ui.update { it.copy(resume = save?.let { s -> resumeUi(s, stories) }) }
            }
        }
        viewModelScope.launch { saves.badges.collect { b -> _ui.update { it.copy(badges = b) } } }
        collectVehicle()
        viewModelScope.launch {
            navi.remainingMinutes.collect { eta ->
                _ui.update { s ->
                    val rec = eta?.let(::recommendLength)
                    s.copy(etaMin = eta, lengthMin = if (!s.lengthTouched && rec != null) rec else s.lengthMin)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 화면 이동
    // ------------------------------------------------------------------

    fun goSelect() = _ui.update { it.copy(screen = ScreenId.SELECT) }

    fun goHome() {
        stopPlaying()
        _ui.update { it.copy(screen = ScreenId.HOME, player = null, ending = null, quitDialog = false) }
    }

    fun selectStory(id: String) = _ui.update { it.copy(selectedStoryId = id) }

    fun selectLength(min: Int) = _ui.update { it.copy(lengthMin = min, lengthTouched = true) }

    /** 출발! 이야기 시작 */
    fun startStory() {
        val s = _ui.value
        val story = s.selectedStory ?: return
        launchEngine(StoryEngine(story, s.lengthMin))
        showToast("🚗 출발하면 이야기가 앞으로 나아가요. 디버그 패널에서 속도를 올려 보세요!")
    }

    /** 홈의 이어 듣기 카드: 저장된 장면부터 다시 */
    fun resumeStory() {
        val save = latestSave ?: return
        val story = _ui.value.stories.firstOrNull { it.id == save.state.storyId } ?: return
        launchEngine(StoryEngine(story, save.state.lengthMin, restore = save.state))
        showToast("▶ 지난번 그 장면부터 이어서 들려줄게요")
    }

    private fun launchEngine(e: StoryEngine) {
        engine = e
        updateLearnAverage()
        paused.value = false
        _ui.update {
            it.copy(
                screen = ScreenId.PLAYER,
                player = PlayerUi(story = e.story, lengthMin = e.state.lengthMin, sceneId = e.state.sceneId, path = e.state.path),
                ending = null,
                quitDialog = false,
            )
        }
        refreshPlayer(e)
        sound.startBgm()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            launch { clockLoop(e) }
            storyLoop(e)
        }
    }

    /** 이야기 그만하기 → "저장할까요?" (그동안 이야기는 멈춤) */
    fun quitStory() {
        if (engine == null) return goHome()
        paused.value = true
        narrator.stop() // 읽던 줄은 "계속 듣기" 하면 처음부터 다시 읽음
        sound.pauseBgm()
        _ui.update { it.copy(quitDialog = true) }
    }

    /** 대화상자: 저장하고 끝내기(true) / 저장 안 함(false) */
    fun finishQuit(save: Boolean) {
        val e = engine ?: return goHome()
        val state = e.state
        stopPlaying()
        viewModelScope.launch {
            if (save) saves.save(state, isTemp = false) else saves.delete(state.storyId)
            _ui.update { it.copy(quitDialog = false) }
            goHome()
            showToast(if (save) "💾 저장했어요. 다음 주행에서 이어서 들을 수 있어요" else "이번 이야기는 저장하지 않았어요")
        }
    }

    /** 대화상자: 계속 듣기 */
    fun cancelQuit() {
        _ui.update { it.copy(quitDialog = false) }
        paused.value = false
        sound.startBgm()
    }

    /** 디버그: 앱을 진짜로 강제 종료 (임시 저장 복구 시연용) */
    fun forceKill() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** 디버그: 마이크 대신 시험 녹음을 보낸다 (선택지에서 음성 대답 시험) */
    fun sendTestAudio(name: String) {
        val input = voiceInput as? TestAudioInput ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { appContext.assets.open("$TEST_AUDIO_DIR/$name.pcm").use { it.readBytes() } }
                .onSuccess { input.sendTestAudio(it) }
                .onFailure { Log.w("RoadNovelViewModel", "시험 녹음 읽기 실패: $name", it) }
        }
        showToast("🎤 시험 녹음 보냄: ${TEST_AUDIO_LABELS[name] ?: name}")
    }

    /** 🔊 소리 켜짐 ↔ 🔇 자막만 */
    fun toggleSound() {
        val on = !_ui.value.soundOn
        if (!on) narrator.stop()
        sound.setEnabled(on)
        _ui.update { it.copy(soundOn = on) }
    }

    // ------------------------------------------------------------------
    // 선택지 버튼 (동승자용)
    // ------------------------------------------------------------------

    fun pickChoice(index: Int) {
        choiceAnswer?.complete(index)
    }

    // ------------------------------------------------------------------
    // 디버그 패널
    // ------------------------------------------------------------------

    fun toggleDebug() = _ui.update { it.copy(debugOpen = !it.debugOpen) }

    fun hideDebug() = _ui.update { it.copy(debugHidden = true, debugOpen = false) }

    fun showDebug() = _ui.update { it.copy(debugHidden = false) }

    fun setSpeed(kmh: Float) {
        car.setSpeed(kmh)
        _ui.update { it.copy(speedKmh = kmh) }
    }

    fun setTimeScale(scale: Int) {
        car.timeScale = scale.toDouble()
        _ui.update { it.copy(timeScale = scale) }
    }

    fun setEta(min: Int) = mockNavi.setRemainingMinutes(min)

    // ------------------------------------------------------------------
    // 시연 모드 (녹화용, 약 2분)
    // ------------------------------------------------------------------

    private var demoJob: Job? = null

    /**
     * 10분 이야기를 처음부터 자동으로 진행한다:
     * 출발 → 장면 진행 → 정차 이야기 → 선택 → 그만하기·저장 → 이어 듣기 → 결말.
     * 시간만 재지 않고 화면 상태(자막 종류·선택지·장면)를 보고 다음 단계로 넘어간다.
     */
    fun startDemo() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch { runDemo() }
    }

    private suspend fun runDemo() {
        val story = _ui.value.stories.firstOrNull() ?: return
        setVehicleSource(VehicleSource.MOCK)
        hideDebug()
        selectStory(story.id)
        selectLength(DEMO_LENGTH_MIN)
        setSpeed(0f)
        setTimeScale(10)
        startStory()
        showToast("🎬 시연 모드: 약 2분 동안 자동으로 진행돼요")

        // 1) 출발
        delay(2_500)
        setSpeed(60f)
        // 2) 0.5km쯤 달린 뒤 정차 → 정차 이야기 (출발 전 정차는 무시되므로 먼저 달린다)
        delay(3_000)
        setSpeed(0f)
        until(25_000) { it.player?.caption?.source == LineSource.INTERLUDE }
        until(30_000) { it.player?.caption?.source != LineSource.INTERLUDE }
        setSpeed(60f)
        // 3) 첫 갈림길: 잠깐 보여 준 뒤 2번(버섯 마을)
        until(120_000) { it.player?.choice != null }
        delay(DEMO_CHOICE_SHOW_MS)
        pickChoice(1)
        // 4) 버섯 마을을 조금 듣다가 그만하기 → 저장
        until(15_000) { it.player?.sceneId == "s3b_village" }
        delay(7_000)
        quitStory()
        delay(2_500)
        finishQuit(save = true)
        // 5) 홈의 이어 듣기 카드 → 이어 듣기
        until(10_000) { it.screen == ScreenId.HOME && it.resume != null }
        delay(3_500)
        resumeStory()
        setSpeed(60f)
        // 6) 두 번째 갈림길: 1번(노래) → 결말
        until(120_000) { it.player?.choice != null }
        delay(DEMO_CHOICE_SHOW_MS)
        pickChoice(0)
        until(120_000) { it.screen == ScreenId.ENDING }
    }

    /** [cond]가 될 때까지 기다림 (시간이 지나면 그냥 다음으로) */
    private suspend fun until(timeoutMs: Long, cond: (UiState) -> Boolean): Boolean =
        withTimeoutOrNull(timeoutMs) { _ui.first(cond); true } ?: false

    /** 디버그: 차량 데이터 출처 [Mock(슬라이더) / Pleos 차량] */
    fun setVehicleSource(src: VehicleSource) {
        if (src == VehicleSource.PLEOS) pleosVehicle.start()
        source.value = src
        engine?.resetOdometerBaseline()
        updateLearnAverage()
        _ui.update { it.copy(vehicleSource = src) }
    }

    /** 속도 값 없이 주행거리계(1km 단위)만 있으면 평균 속도 보정을 끈다 */
    private fun updateLearnAverage() {
        engine?.learnAverageSpeed = source.value == VehicleSource.MOCK || speed != null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectVehicle() {
        fun <T> active(mock: kotlinx.coroutines.flow.Flow<T>, pleos: kotlinx.coroutines.flow.Flow<T>) =
            source.flatMapLatest { if (it == VehicleSource.MOCK) mock else pleos }
        viewModelScope.launch { active(car.speedKmh, pleosVehicle.speedKmh).collect { speed = it; onVehicleChanged() } }
        viewModelScope.launch { active(car.odometerKm, pleosVehicle.odometerKm).collect { odometer = it; onVehicleChanged() } }
        viewModelScope.launch { active(car.gear, pleosVehicle.gear).collect { gear = it; onVehicleChanged() } }
        // Pleos 차량을 쓸 수 없으면 자동으로 Mock으로 돌아간다
        viewModelScope.launch {
            pleosVehicle.status.collect { st ->
                val text = when (st) {
                    PleosVehicleDataSource.Status.Idle -> ""
                    PleosVehicleDataSource.Status.Checking -> "연결 확인 중…"
                    is PleosVehicleDataSource.Status.Available ->
                        "연결됨 · 주행거리 ${if (st.odometer) "O" else "X"} · 기어 ${if (st.gear) "O" else "X"} · 속도 ${if (st.speed) "O" else "X(기어로 판단)"}"
                    is PleosVehicleDataSource.Status.Unavailable -> "사용 불가: ${st.reason}"
                }
                _ui.update { it.copy(pleosStatus = text) }
                if (st is PleosVehicleDataSource.Status.Unavailable && source.value == VehicleSource.PLEOS) {
                    setVehicleSource(VehicleSource.MOCK)
                    showToast("🚗 Pleos 차량 데이터를 쓸 수 없어 Mock으로 돌아왔어요")
                }
            }
        }
    }

    private fun onVehicleChanged() {
        updateLearnAverage()
        _ui.update {
            it.copy(liveSpeedKmh = speed, gear = gear, odometerKm = odometer, moving = isMoving(speed, gear))
        }
    }

    // ------------------------------------------------------------------
    // 재생 반복 작업
    // ------------------------------------------------------------------

    /** 0.1초마다 차를 움직이고 엔진에 값을 넣는다 */
    private suspend fun clockLoop(e: StoryEngine) = coroutineScope {
        while (isActive) {
            delay(TICK_MS)
            // Mock은 배속을 적용해 가짜 차를 움직이고, Pleos 차량은 실제 시간 그대로
            val simSec = if (source.value == VehicleSource.MOCK) car.advance(TICK_MS / 1000.0) else TICK_MS / 1000.0
            e.tick(simSec, effectiveSpeedKmh(speed, gear), odometer)
            refreshPlayer(e)
        }
    }

    /** 엔진이 시키는 일을 차례로 처리 */
    private suspend fun storyLoop(e: StoryEngine) {
        var savedSceneId: String? = null
        while (true) {
            paused.first { !it }
            // 장면이 바뀔 때마다 자동 임시 저장 (앱이 갑자기 꺼져도 복구)
            val st = e.state
            if (st.phase != Phase.ENDED && st.sceneId != savedSceneId) {
                saves.save(st, isTemp = true)
                savedSceneId = st.sceneId
                prefetchAi(e)
                if (skipNextSceneChime) skipNextSceneChime = false else sound.play(Sfx.SCENE)
            }
            when (val a = e.nextAction()) {
                is EngineAction.Speak -> {
                    if (a.source == LineSource.INTERLUDE && _ui.value.player?.caption?.source != LineSource.INTERLUDE) {
                        showToast("🏕️ 정차 이벤트: 탐험대도 잠깐 쉬어요")
                        sound.play(Sfx.REST)
                    }
                    setCaption(e, a)
                    sound.setSpeaking(true)
                    activeNarrator.speak(a.line.text, voiceOf(e, a.line.who))
                    sound.setSpeaking(false)
                    // 읽는 도중 "그만하기"로 멈췄으면 이 줄은 끝난 게 아님 → 나중에 다시 읽음
                    if (!paused.value) e.lineDone()
                }
                // TODO(M6): Pleos LLM으로 추가 대사 생성. 지금은 건너뜀
                is EngineAction.RequestAiLines -> awaitAi(e, a).let { e.supplyAiLines(it.lines, it.byModel) }
                is EngineAction.Wait -> delay(WAIT_POLL_MS)
                is EngineAction.AskChoice -> handleChoice(e, a)
                is EngineAction.Finished -> {
                    // 결말 도착: 진행 기록 삭제 + 배지 저장
                    saves.delete(e.story.id)
                    saves.addBadge(e.story.id, a.ending)
                    sound.stopBgm()
                    sound.play(Sfx.ENDING)
                    showEnding(e, a.ending)
                    return
                }
            }
        }
    }

    /**
     * 질문을 읽고(TTS) → 음성 대답을 듣는다(STT). 동승자는 화면 버튼으로도 고를 수 있다.
     * 못 알아들으면 엔진 규칙대로 한 번 더 묻고, 그래도 안 되면 기본값.
     */
    private suspend fun handleChoice(e: StoryEngine, a: EngineAction.AskChoice) {
        val answer = CompletableDeferred<Int>()
        choiceAnswer = answer
        val timeout = a.choice.timeoutSec
        val question = if (a.attempt > 1) "다시 한 번 물어볼게요. ${a.choice.question}" else a.choice.question
        fun show(left: Int) = setChoice(
            ChoiceUi(
                question = question,
                options = a.choice.options.map { it.label },
                defaultIndex = a.choice.defaultIndex,
                secondsLeft = left,
                attempt = a.attempt,
            )
        )
        show(timeout)
        if (a.attempt == 1) sound.play(Sfx.CHOICE)

        val outcome: ChoiceOutcome? = coroutineScope {
            // 질문 읽는 동안에도 버튼은 누를 수 있다
            val asking = async { activeNarrator.speak(question, voiceOf(e, "narrator")) }
            select<Unit> {
                asking.onAwait { }
                answer.onAwait { }
            }
            if (answer.isCompleted) {
                activeNarrator.stop(); asking.cancel()
                return@coroutineScope ChoiceOutcome.Button(answer.await())
            }
            val countdown = launch {
                for (left in timeout downTo 1) { show(left); delay(1000) }
            }
            val result = withTimeoutOrNull(timeout * 1000L) {
                val listening = async { voiceInput.listen(timeout) }
                val first = select<ChoiceOutcome?> {
                    answer.onAwait { ChoiceOutcome.Button(it) }
                    listening.onAwait { heard -> heard?.let { ChoiceOutcome.Voice(it) } }
                }
                listening.cancel()
                // 음성 인식이 실패하면(인식기 없음 등) 남은 시간 동안 버튼을 기다린다
                first ?: ChoiceOutcome.Button(answer.await())
            }
            countdown.cancel()
            result
        }
        choiceAnswer = null
        setChoice(null)

        val result = when (outcome) {
            is ChoiceOutcome.Button -> e.pickChoice(outcome.index)
            is ChoiceOutcome.Voice -> {
                showToast("🎤 \"${outcome.heard}\" 라고 들었어요")
                e.answerChoice(outcome.heard)
            }
            null -> e.answerChoice(null) // 시간 초과
        }
        when (result) {
            is ChoiceResult.Picked -> {
                sound.play(Sfx.PICK)
                skipNextSceneChime = true
                val how = if (result.method == com.roadnovel.story.ChoiceMethod.DEFAULT) " (대답이 없어서 기본 선택)" else ""
                showToast("🗺️ 탐험대의 선택: ${result.option.label}$how")
            }
            ChoiceResult.AskAgain -> Unit // 다음 nextAction이 두 번째 질문
        }
        refreshPlayer(e)
    }

    /** 장면 대사를 읽는 동안 AI 대사를 미리 만든다 (네트워크·모델이 느려도 이야기가 멈추지 않게) */
    private fun prefetchAi(e: StoryEngine) {
        val scene = e.currentScene
        val ai = scene.ai ?: return
        if (!ai.enabled || e.state.sceneId in aiPrefetch) return
        val ctx = StoryContext(e.story, e.state.sceneId, e.state.items)
        aiPrefetch[e.state.sceneId] = viewModelScope.async {
            runCatching { dialogue.generate(ctx, ai.hint) }.getOrDefault(ExtraLines.NONE)
        }
    }

    /** CLAUDE.md 10장: 3초 안에 못 받으면 건너뛴다 */
    private suspend fun awaitAi(e: StoryEngine, a: EngineAction.RequestAiLines): ExtraLines {
        val job = aiPrefetch.remove(a.sceneId) ?: viewModelScope.async {
            runCatching { dialogue.generate(StoryContext(e.story, a.sceneId, e.state.items), a.hint) }.getOrDefault(ExtraLines.NONE)
        }
        val result = withTimeoutOrNull(AI_WAIT_MS) { job.await() }
        if (result == null) Log.i("RoadNovelViewModel", "추가 대사 ${AI_WAIT_MS}ms 안에 못 받음 → 건너뜀")
        _ui.update { it.copy(aiStatus = if (result == null || result.lines.isEmpty()) "건너뜀" else result.from) }
        return result ?: ExtraLines.NONE
    }

    private sealed interface ChoiceOutcome {
        data class Button(val index: Int) : ChoiceOutcome
        data class Voice(val heard: String) : ChoiceOutcome
    }

    private fun voiceOf(e: StoryEngine, who: String): VoiceStyle = e.story.character(who)?.voice ?: VoiceStyle()

    private fun showEnding(e: StoryEngine, ending: Ending) {
        val st = e.state
        val allEndings = e.story.scenes.values.mapNotNull { it.ending?.id }.distinct()
        _ui.update {
            it.copy(
                screen = ScreenId.ENDING,
                ending = EndingUi(
                    ending = ending,
                    storyTitle = e.story.title,
                    totalKm = st.totalKm,
                    elapsedSec = st.elapsedSec,
                    choices = st.choices.map { c -> c.label },
                    unseenCount = (allEndings.toSet() - earnedEndings(e.story.id) - ending.id).size,
                ),
            )
        }
        stopPlaying()
    }

    private fun stopPlaying() {
        narrator.stop()
        if (_ui.value.screen != ScreenId.ENDING) sound.stopBgm()
        aiPrefetch.values.forEach { it.cancel() }
        aiPrefetch.clear()
        playJob?.cancel()
        playJob = null
        choiceAnswer = null
        engine = null
    }

    // ------------------------------------------------------------------
    // 화면 상태 만들기
    // ------------------------------------------------------------------

    private fun setCaption(e: StoryEngine, a: EngineAction.Speak) {
        val name = e.story.character(a.line.who)?.name ?: a.line.who
        updatePlayer { it.copy(caption = Caption(a.line.who, name, a.line.text, a.source)) }
        refreshPlayer(e)
    }

    private fun setChoice(choice: ChoiceUi?) = updatePlayer { it.copy(choice = choice) }

    private fun refreshPlayer(e: StoryEngine) {
        val st = e.state
        val scene = e.currentScene
        val need = e.requiredKm()
        val moved = e.movedInScene()
        val (label, right) = when {
            st.phase == Phase.CHOICE -> "탐험대의 선택을 기다려요" to ""
            scene.choice != null -> "곧 갈림길이 나와요" to ""
            scene.ending != null && need <= 0.0 -> "마지막 장면" to ""
            else -> "다음 장면까지" to "%.2f km 남음".format((need - moved).coerceAtLeast(0.0))
        }
        val progress = if (need <= 0.0) 1f else (moved / need).toFloat().coerceIn(0f, 1f)
        updatePlayer {
            it.copy(
                sceneId = st.sceneId,
                path = st.path,
                progress = progress,
                progressLabel = label,
                progressRight = right,
                stats = DebugStats(
                    totalKm = st.totalKm,
                    elapsedSec = st.elapsedSec,
                    avgKmh = st.avgKmPerMin * 60,
                    kmPerWeight = e.kmPerWeight(),
                    itemsAndFlags = (st.items + st.flags).joinToString(", ").ifEmpty { "-" },
                ),
            )
        }
    }

    private inline fun updatePlayer(crossinline f: (PlayerUi) -> PlayerUi) =
        _ui.update { s -> s.player?.let { s.copy(player = f(it)) } ?: s }

    private fun earnedEndings(storyId: String): Set<String> =
        _ui.value.badges.filter { it.storyId == storyId }.map { it.endingId }.toSet()

    private fun resumeUi(save: SavedProgress, stories: List<Story>): ResumeUi? {
        val story = stories.firstOrNull { it.id == save.state.storyId } ?: return null
        val st = save.state
        val scene = story.scenes[st.sceneId] ?: return null
        val remaining = runCatching { StoryEngine(story, st.lengthMin, restore = st).remainingMinutesEstimate() }.getOrDefault(0)
        return ResumeUi(
            storyId = story.id,
            storyTitle = story.title,
            sceneTitle = scene.title,
            lengthMin = st.lengthMin,
            choices = st.choices.map { it.label },
            totalKm = st.totalKm,
            remainingMin = remaining,
            isTemp = save.isTemp,
        )
    }

    private fun showToast(msg: String) {
        toastJob?.cancel()
        _ui.update { it.copy(toast = msg) }
        toastJob = viewModelScope.launch {
            delay(TOAST_MS)
            _ui.update { it.copy(toast = null) }
        }
    }

    override fun onCleared() {
        stopPlaying()
    }

    companion object {
        const val TICK_MS = 100L
        const val WAIT_POLL_MS = 150L
        const val TOAST_MS = 3000L
        const val AI_WAIT_MS = 3_000L
        const val DEMO_LENGTH_MIN = 10
        const val DEMO_CHOICE_SHOW_MS = 3_500L
        const val TEST_AUDIO_DIR = "test_audio"
        /** 시험 녹음 파일 이름 → 화면에 보일 말 */
        val TEST_AUDIO_LABELS = mapOf("old_castle" to "오래된 성", "two" to "2번")
    }
}
