package com.roadnovel.data.save

import com.roadnovel.story.Ending
import com.roadnovel.story.StoryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

data class SavedProgress(val state: StoryState, val isTemp: Boolean, val savedAt: Long)

data class EarnedBadge(val storyId: String, val endingId: String, val name: String, val badge: String)

/** 진행 저장·배지 (CLAUDE.md 9장) */
interface SaveRepository {
    /** 가장 최근 저장 기록 (없으면 null) */
    val latestProgress: Flow<SavedProgress?>
    val badges: Flow<List<EarnedBadge>>

    /** isTemp=true: 장면 바뀔 때 자동 저장 / false: 사용자가 "저장" 확정 */
    suspend fun save(state: StoryState, isTemp: Boolean)
    suspend fun delete(storyId: String)
    suspend fun addBadge(storyId: String, ending: Ending)
}

class RoomSaveRepository(
    private val dao: SaveDao,
    private val now: () -> Long = System::currentTimeMillis,
) : SaveRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val latestProgress: Flow<SavedProgress?> = dao.latest().map { e ->
        // 저장 형식이 바뀌어서 못 읽으면 그냥 없는 것으로 (앱이 죽지 않게)
        e?.let { runCatching { SavedProgress(json.decodeFromString(StoryState.serializer(), it.stateJson), it.isTemp, it.savedAt) }.getOrNull() }
    }

    override val badges: Flow<List<EarnedBadge>> = dao.badges().map { list ->
        list.map { EarnedBadge(it.storyId, it.endingId, it.name, it.badge) }
    }

    override suspend fun save(state: StoryState, isTemp: Boolean) {
        dao.upsert(
            SavedProgressEntity(
                storyId = state.storyId,
                stateJson = json.encodeToString(StoryState.serializer(), state),
                isTemp = isTemp,
                savedAt = now(),
            )
        )
    }

    override suspend fun delete(storyId: String) = dao.delete(storyId)

    override suspend fun addBadge(storyId: String, ending: Ending) =
        dao.addBadge(BadgeEntity(storyId, ending.id, ending.name, ending.badge, now()))
}
