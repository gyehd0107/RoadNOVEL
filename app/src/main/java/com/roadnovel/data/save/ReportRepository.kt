package com.roadnovel.data.save

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * AI 콘텐츠 신고 기록. 지금은 서버가 없어 앱 안에만 남긴다.
 * 나중에 서버가 생기면 [ReportRepository.pending]을 보내고 [ReportRepository.markSent]로 표시.
 * (개인정보·위치는 담지 않는다: 이야기·장면·대사 내용과 시각만)
 */
@Entity(tableName = "content_reports")
data class ContentReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val sceneId: String,
    val who: String,
    val text: String,
    /** "AI"(언어 모델이 만든 대사) / "EXTRA"(미리 써 둔 추가 대사) / "SCENE" */
    val source: String,
    val createdAt: Long,
    val sent: Boolean = false,
)

@Dao
interface ReportDao {
    @Insert
    suspend fun insert(r: ContentReportEntity): Long

    @Query("SELECT * FROM content_reports WHERE sent = 0 ORDER BY createdAt")
    suspend fun pending(): List<ContentReportEntity>

    @Query("UPDATE content_reports SET sent = 1 WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM content_reports")
    suspend fun count(): Int
}

data class ContentReport(
    val storyId: String,
    val sceneId: String,
    val who: String,
    val text: String,
    val source: String,
)

/** 신고 저장소. 서버로 보내는 부분은 나중에 [ReportUploader] 구현을 붙이면 된다 */
interface ReportRepository {
    suspend fun report(r: ContentReport)
    suspend fun pending(): List<ContentReportEntity>
    suspend fun markSent(ids: List<Long>)
}

/** TODO(server): 서버가 생기면 구현해서 pending()을 보내고 markSent() */
interface ReportUploader {
    suspend fun upload(reports: List<ContentReportEntity>): Boolean
}

class RoomReportRepository(
    private val dao: ReportDao,
    private val now: () -> Long = System::currentTimeMillis,
) : ReportRepository {
    override suspend fun report(r: ContentReport) {
        dao.insert(ContentReportEntity(storyId = r.storyId, sceneId = r.sceneId, who = r.who, text = r.text, source = r.source, createdAt = now()))
    }
    override suspend fun pending() = dao.pending()
    override suspend fun markSent(ids: List<Long>) = dao.markSent(ids)
}

/** DB 1 → 2: 신고 표 추가 (기존 저장·배지는 그대로) */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `content_reports` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `storyId` TEXT NOT NULL, `sceneId` TEXT NOT NULL, `who` TEXT NOT NULL,
                `text` TEXT NOT NULL, `source` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `sent` INTEGER NOT NULL)"""
        )
    }
}
