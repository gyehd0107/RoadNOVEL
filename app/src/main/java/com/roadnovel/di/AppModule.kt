package com.roadnovel.di

import android.content.Context
import com.roadnovel.data.navi.MockNaviDataSource
import com.roadnovel.data.navi.NaviDataSource
import androidx.room.Room
import com.roadnovel.audio.AndroidSoundEffects
import com.roadnovel.audio.AudioFocusController
import com.roadnovel.audio.NavPromptSimulator
import com.roadnovel.audio.SoundEffects
import com.roadnovel.data.save.RoadNovelDatabase
import com.roadnovel.data.save.RoomSaveRepository
import com.roadnovel.data.save.SaveRepository
import com.roadnovel.data.story.AssetStoryRepository
import com.roadnovel.data.vehicle.DrivingRestriction
import com.roadnovel.data.vehicle.MockVehicleDataSource
import com.roadnovel.data.vehicle.PleosVehicleDataSource
import com.roadnovel.story.StoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 앱 전체에서 쓰는 객체를 여기서 만든다. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun storyRepository(@ApplicationContext context: Context): StoryRepository =
        AssetStoryRepository(context)

    /** 디버그 패널이 직접 조작해야 해서 Mock 자체도 꺼내 쓸 수 있게 둔다 */
    @Provides
    @Singleton
    fun mockVehicle(): MockVehicleDataSource = MockVehicleDataSource()

    /** Pleos 차량 데이터. 디버그 패널에서 "Pleos 차량"을 고르면 그때 연결한다 */
    @Provides
    @Singleton
    fun pleosVehicle(@ApplicationContext context: Context): PleosVehicleDataSource = PleosVehicleDataSource(context)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RoadNovelDatabase =
        Room.databaseBuilder(context, RoadNovelDatabase::class.java, "roadnovel.db").build()

    @Provides
    @Singleton
    fun saveRepository(db: RoadNovelDatabase): SaveRepository = RoomSaveRepository(db.saveDao())

    @Provides
    @Singleton
    fun mockNavi(): MockNaviDataSource = MockNaviDataSource()

    /** 효과음·배경음 (tools/make_sounds.py로 직접 만든 소리) */
    @Provides
    @Singleton
    fun soundEffects(@ApplicationContext context: Context): SoundEffects = AndroidSoundEffects(context)

    /** 오디오 포커스 (내비 안내가 나오면 이야기 소리를 줄이거나 멈춤) */
    @Provides
    @Singleton
    fun audioFocus(@ApplicationContext context: Context): AudioFocusController = AudioFocusController(context)

    /** 주행 중 화면 제한 (AAOS CarUxRestrictions) */
    @Provides
    @Singleton
    fun drivingRestriction(@ApplicationContext context: Context): DrivingRestriction = DrivingRestriction(context)

    @Provides
    @Singleton
    fun navPromptSimulator(@ApplicationContext context: Context): NavPromptSimulator = NavPromptSimulator(context)

    // TODO(M5): Pleos NaviHelper 구현이 생기면 여기서 고른다
    @Provides
    @Singleton
    fun naviDataSource(mock: MockNaviDataSource): NaviDataSource = mock
}
