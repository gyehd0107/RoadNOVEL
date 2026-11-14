package com.roadnovel.data.save

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 이야기 진행 기록 (이야기 하나당 한 칸).
 * 위치 좌표는 저장하지 않는다 — StoryState에는 거리(km)만 있다.
 */
@Entity(tableName = "saved_progress")
data class SavedProgressEntity(
    @androidx.room.PrimaryKey val storyId: String,
    /** StoryState를 JSON으로 통째로 */
    val stateJson: String,
    /** true = 장면이 바뀔 때 자동 저장된 것 (사용자가 확정하지 않음) */
    val isTemp: Boolean,
    val savedAt: Long,
)

/** 받은 결말 배지 (다시 들을 이유) */
@Entity(tableName = "badges", primaryKeys = ["storyId", "endingId"])
data class BadgeEntity(
    val storyId: String,
    val endingId: String,
    val name: String,
    val badge: String,
    val earnedAt: Long,
)

@Dao
interface SaveDao {
    @Query("SELECT * FROM saved_progress ORDER BY savedAt DESC LIMIT 1")
    fun latest(): Flow<SavedProgressEntity?>

    @Upsert
    suspend fun upsert(e: SavedProgressEntity)

    @Query("DELETE FROM saved_progress WHERE storyId = :storyId")
    suspend fun delete(storyId: String)

    @Query("SELECT * FROM badges ORDER BY earnedAt")
    fun badges(): Flow<List<BadgeEntity>>

    /** 이미 받은 배지면 처음 받은 날짜를 그대로 둔다 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBadge(b: BadgeEntity)
}

@Database(entities = [SavedProgressEntity::class, BadgeEntity::class], version = 1, exportSchema = false)
abstract class RoadNovelDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao
}
