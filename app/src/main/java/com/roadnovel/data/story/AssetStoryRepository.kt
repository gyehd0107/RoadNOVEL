package com.roadnovel.data.story

import android.content.Context
import com.roadnovel.story.Story
import com.roadnovel.story.StoryJson
import com.roadnovel.story.StoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** app/src/main/assets/stories 폴더의 .json 파일들을 읽어 온다. 한 번 읽으면 기억해 둔다. */
class AssetStoryRepository(private val context: Context) : StoryRepository {
    private val mutex = Mutex()
    private var cache: Map<String, Story>? = null

    override suspend fun listStories(): List<Story> = all().values.sortedBy { it.title }

    override suspend fun load(id: String): Story =
        all()[id] ?: error("이야기 '$id'을(를) 찾을 수 없어요")

    private suspend fun all(): Map<String, Story> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            val files = context.assets.list(DIR).orEmpty().filter { it.endsWith(".json") }
            files.map { name ->
                context.assets.open("$DIR/$name").bufferedReader().use { StoryJson.parse(it.readText()) }
            }.associateBy { it.id }
        }.also { cache = it }
    }

    private companion object {
        const val DIR = "stories"
    }
}
