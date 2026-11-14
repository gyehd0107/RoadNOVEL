package com.roadnovel.ai

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.OnServerConnectionListener
import ai.pleos.playground.llm.listener.ResultListener
import com.roadnovel.voice.PleosConfig
import kotlinx.coroutines.CompletableDeferred
import android.content.Context
import android.util.Log
import com.roadnovel.story.StoryLine
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos LLM SDK 2.1.3.2.
 * ⚠️ Pleos_Connect_v2.1.0 에뮬레이터에서는 "on-device LLM is disabled" (서비스 로그) → ON_DEVICE 응답 없음.
 *    그래서 기본 AI_ENGINE은 "mock". Client ID/Secret이 있으면 SERVER 모드 + registerApp으로 시도한다 (미시험).
 * 공식 사용 안내 문서가 내려가 있어서 SDK jar를 javap로 직접 열어 확인한 공개 API만 쓴다:
 *   LLM(context, Mode.ON_DEVICE) → initialize() → generateContent(prompt, ResultListener)
 *   ResultListener.onResponse(text: String, 두 번째 값: Boolean) / onError(message: String)
 *   (두 번째 Boolean의 뜻은 문서가 없어 확인 못 함 → true면 끝, false면 아직 오는 중으로 보고 마지막 값을 쓴다)
 * 응답은 ResponseParser → SafetyFilter를 반드시 거친다.
 */
class PleosDialogueGenerator(context: Context, config: PleosConfig) : DialogueGenerator {

    /** Client ID가 있으면 SERVER(앱 등록), 없으면 ON_DEVICE */
    private val server = config.canRegister
    override val name = if (server) "Pleos LLM (SERVER)" else "Pleos LLM (ON_DEVICE)"

    /** SERVER 모드에서 registerApp → onConnected 까지 기다림. ON_DEVICE는 바로 준비 */
    private val connected = CompletableDeferred<Boolean>().apply { if (!server) complete(true) }

    private val llm: LLM? = runCatching {
        LLM(context.applicationContext, if (server) Mode.SERVER else Mode.ON_DEVICE).also { llm ->
            llm.initialize()
            if (server) llm.registerApp(config.clientId, config.clientSecret, object : OnServerConnectionListener {
                override fun onConnected() { connected.complete(true) }
                override fun onFailed(msg: String) {
                    Log.w(TAG, "LLM 서버 연결 실패: $msg")
                    connected.complete(false)
                }
            })
        }
    }.onFailure { Log.w(TAG, "LLM 초기화 실패: ${it.message}") }.getOrNull()

    override suspend fun generate(context: StoryContext, hint: String): ExtraLines =
        ExtraLines(lines(context, hint), byModel = true, from = name)

    private suspend fun lines(context: StoryContext, hint: String): List<StoryLine> {
        val llm = llm ?: return emptyList()
        if (withTimeoutOrNull(CONNECT_WAIT_MS) { connected.await() } != true) return emptyList()
        val prompt = PromptBuilder.build(context, hint)
        val started = System.currentTimeMillis()
        val text = withTimeoutOrNull(GENERATE_TIMEOUT_MS) { generate(llm, prompt) }
        Log.i(TAG, "응답 (${System.currentTimeMillis() - started}ms): ${text?.take(300)?.replace("\n", " ⏎ ") ?: "없음(시간 초과/오류)"}")
        if (text.isNullOrBlank()) return emptyList()

        val parsed = ResponseParser.parse(text)
        val result = SafetyFilter.check(parsed, context.story)
        result.dropped.forEach { (line, why) -> Log.i(TAG, "버림 [$why] ${line.who}|${line.text}") }
        return result.kept
    }

    private suspend fun generate(llm: LLM, prompt: String): String? = suspendCancellableCoroutine { cont ->
        var last: String? = null
        runCatching {
            llm.generateContent(prompt, object : ResultListener {
                override fun onResponse(response: String, completed: Boolean) {
                    last = response
                    if (completed && cont.isActive) cont.resume(response)
                }
                override fun onError(reason: String) {
                    Log.w(TAG, "LLM 오류: $reason")
                    if (cont.isActive) cont.resume(last)
                }
            })
        }.onFailure {
            Log.w(TAG, "generateContent 호출 실패: ${it.message}")
            if (cont.isActive) cont.resume(null)
        }
    }

    private companion object {
        const val TAG = "PleosLLM"
        /** 미리 생성(장면 시작할 때)용 넉넉한 시간. 실제 재생 때는 3초만 기다린다 (ViewModel) */
        const val GENERATE_TIMEOUT_MS = 25_000L
        const val CONNECT_WAIT_MS = 5_000L
    }
}
