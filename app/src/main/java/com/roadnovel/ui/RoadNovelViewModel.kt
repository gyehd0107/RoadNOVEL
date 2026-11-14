package com.roadnovel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadnovel.data.navi.MockNaviDataSource
import com.roadnovel.data.navi.NaviDataSource
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val player: PlayerUi? = null,
    val ending: EndingUi? = null,
    val toast: String? = null,
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
) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var engine: StoryEngine? = null
    private var playJob: Job? = null
    private var toastJob: Job? = null
    private var choiceAnswer: CompletableDeferred<Int>? = null

    // 차량 값 최신본 (VehicleDataSource 흐름에서 받음)
    private var speed: Float? = 0f
    private var odometer: Double? = null

    init {
        car.timeScale = _ui.value.timeScale.toDouble()
        viewModelScope.launch {
            runCatching { repository.listStories() }
                .onSuccess { list -> _ui.update { it.copy(stories = list, selectedStoryId = list.firstOrNull()?.id) } }
                .onFailure { e -> _ui.update { it.copy(loadError = e.message) } }
        }
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
        _ui.update { it.copy(screen = ScreenId.HOME, player = null, ending = null) }
    }

    fun selectStory(id: String) = _ui.update { it.copy(selectedStoryId = id) }

    fun selectLength(min: Int) = _ui.update { it.copy(lengthMin = min, lengthTouched = true) }

    /** 출발! 이야기 시작 */
    fun startStory() {
        val s = _ui.value
        val story = s.selectedStory ?: return
        val e = StoryEngine(story, s.lengthMin)
        engine = e
        _ui.update {
            it.copy(
                screen = ScreenId.PLAYER,
                player = PlayerUi(story = story, lengthMin = s.lengthMin, sceneId = e.state.sceneId, path = e.state.path),
                ending = null,
            )
        }
        showToast("🚗 출발하면 이야기가 앞으로 나아가요. 디버그 패널에서 속도를 올려 보세요!")
        playJob?.cancel()
        playJob = viewModelScope.launch {
            launch { clockLoop(e) }
            storyLoop(e)
        }
    }

    /** 이야기 그만하기 (저장 확인은 M3에서) */
    fun quitStory() = goHome()

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
        while (true) {
            when (val a = e.nextAction()) {
                is EngineAction.Speak -> {
                    if (a.source == LineSource.INTERLUDE && _ui.value.player?.caption?.source != LineSource.INTERLUDE) {
                        showToast("🏕️ 정차 이벤트: 탐험대도 잠깐 쉬어요")
                    }
                    setCaption(e, a)
                    // 음성(M4) 대신 글자 수만큼 자막을 보여 준다
                    delay(lineMillis(a.line.text))
                    e.lineDone()
                }
                // TODO(M6): Pleos LLM으로 추가 대사 생성. 지금은 건너뜀
                is EngineAction.RequestAiLines -> e.supplyAiLines(emptyList())
                is EngineAction.Wait -> delay(WAIT_POLL_MS)
                is EngineAction.AskChoice -> handleChoice(e, a)
                is EngineAction.Finished -> {
                    showEnding(e, a.ending)
                    return
                }
            }
        }
    }

    /** 질문을 띄우고 버튼을 기다린다. 시간이 지나면 엔진 규칙대로 한 번 더 묻거나 기본값 */
    private suspend fun handleChoice(e: StoryEngine, a: EngineAction.AskChoice) {
        val answer = CompletableDeferred<Int>()
        choiceAnswer = answer
        val timeout = a.choice.timeoutSec
        val picked = coroutineScope {
            val countdown = launch {
                for (left in timeout downTo 1) {
                    setChoice(
                        ChoiceUi(
                            question = if (a.attempt > 1) "다시 한 번 물어볼게요. ${a.choice.question}" else a.choice.question,
                            options = a.choice.options.map { it.label },
                            defaultIndex = a.choice.defaultIndex,
                            secondsLeft = left,
                            attempt = a.attempt,
                        )
                    )
                    delay(1000)
                }
            }
            withTimeoutOrNull(timeout * 1000L) { answer.await() }.also { countdown.cancel() }
        }
        choiceAnswer = null
        setChoice(null)
        // TODO(M4): 음성 인식 결과를 e.answerChoice(들은 말)로 넘긴다
        val result = if (picked != null) e.pickChoice(picked) else e.answerChoice(null)
        if (result is ChoiceResult.Picked) {
            val how = if (result.method == com.roadnovel.story.ChoiceMethod.DEFAULT) " (대답이 없어서 기본 선택)" else ""
            showToast("🗺️ 탐험대의 선택: ${result.option.label}$how")
        }
        refreshPlayer(e)
    }

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
                    // TODO(M3): 받은 배지를 저장해서 정확히 센다
                    unseenCount = (allEndings - ending.id).size,
                ),
            )
        }
        stopPlaying()
    }

    private fun stopPlaying() {
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

        /** 글자 수에 맞춘 자막 표시 시간 (프로토타입과 같은 계산 + 줄 사이 쉼) */
        fun lineMillis(text: String): Long = 1200L + text.length * 95L + 400L
    }
}
