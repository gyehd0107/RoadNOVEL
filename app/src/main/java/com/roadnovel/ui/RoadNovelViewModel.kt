package com.roadnovel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadnovel.data.navi.MockNaviDataSource
import com.roadnovel.data.navi.NaviDataSource
import com.roadnovel.data.save.EarnedBadge
import com.roadnovel.data.save.SavedProgress
import com.roadnovel.data.save.SaveRepository
import com.roadnovel.data.vehicle.MockVehicleDataSource
import com.roadnovel.data.vehicle.VehicleDataSource
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

data class UiState(
    val screen: ScreenId = ScreenId.HOME,
    val stories: List<Story> = emptyList(),
    val selectedStoryId: String? = null,
    val lengthMin: Int = 20,
    /** 사용자가 길이를 직접 골랐으면 내비 추천으로 덮어쓰지 않음 */
    val lengthTouched: Boolean = false,
    val etaMin: Int? = null,
    val speedKmh: Float = 0f,
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
    private val vehicle: VehicleDataSource,
    private val mockNavi: MockNaviDataSource,
    private val navi: NaviDataSource,
    private val saves: SaveRepository,
    private val narrator: Narrator,
    private val voiceInput: VoiceInput,
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
    private var latestSave: SavedProgress? = null
    /** "저장할까요?" 대화상자가 떠 있는 동안 이야기를 멈춤 */
    private val paused = MutableStateFlow(false)

    // 차량 값 최신본 (VehicleDataSource 흐름에서 받음)
    private var speed: Float? = 0f
    private var odometer: Double? = null

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
        viewModelScope.launch { vehicle.speedKmh.collect { speed = it } }
        viewModelScope.launch { vehicle.odometerKm.collect { odometer = it } }
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
    // 재생 반복 작업
    // ------------------------------------------------------------------

    /** 0.1초마다 차를 움직이고 엔진에 값을 넣는다 */
    private suspend fun clockLoop(e: StoryEngine) = coroutineScope {
        while (isActive) {
            delay(TICK_MS)
            val simSec = car.advance(TICK_MS / 1000.0)
            e.tick(simSec, speed, odometer)
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
            }
            when (val a = e.nextAction()) {
                is EngineAction.Speak -> {
                    if (a.source == LineSource.INTERLUDE && _ui.value.player?.caption?.source != LineSource.INTERLUDE) {
                        showToast("🏕️ 정차 이벤트: 탐험대도 잠깐 쉬어요")
                    }
                    setCaption(e, a)
                    activeNarrator.speak(a.line.text, voiceOf(e, a.line.who))
                    // 읽는 도중 "그만하기"로 멈췄으면 이 줄은 끝난 게 아님 → 나중에 다시 읽음
                    if (!paused.value) e.lineDone()
                }
                // TODO(M6): Pleos LLM으로 추가 대사 생성. 지금은 건너뜀
                is EngineAction.RequestAiLines -> e.supplyAiLines(emptyList())
                is EngineAction.Wait -> delay(WAIT_POLL_MS)
                is EngineAction.AskChoice -> handleChoice(e, a)
                is EngineAction.Finished -> {
                    // 결말 도착: 진행 기록 삭제 + 배지 저장
                    saves.delete(e.story.id)
                    saves.addBadge(e.story.id, a.ending)
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
                val how = if (result.method == com.roadnovel.story.ChoiceMethod.DEFAULT) " (대답이 없어서 기본 선택)" else ""
                showToast("🗺️ 탐험대의 선택: ${result.option.label}$how")
            }
            ChoiceResult.AskAgain -> Unit // 다음 nextAction이 두 번째 질문
        }
        refreshPlayer(e)
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
        const val TEST_AUDIO_DIR = "test_audio"
        /** 시험 녹음 파일 이름 → 화면에 보일 말 */
        val TEST_AUDIO_LABELS = mapOf("old_castle" to "오래된 성", "two" to "2번")
    }
}
