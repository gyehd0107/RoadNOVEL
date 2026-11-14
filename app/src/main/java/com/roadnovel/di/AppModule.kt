package com.roadnovel.di

import android.content.Context
import com.roadnovel.data.navi.MockNaviDataSource
import com.roadnovel.data.navi.NaviDataSource
import com.roadnovel.data.story.AssetStoryRepository
import com.roadnovel.data.vehicle.MockVehicleDataSource
import com.roadnovel.data.vehicle.VehicleDataSource
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

    // TODO(M5): Pleos 차량 구현이 생기면 여기서 Pleos/Mock 중 고르게 한다
    @Provides
    @Singleton
    fun vehicleDataSource(mock: MockVehicleDataSource): VehicleDataSource = mock

    @Provides
    @Singleton
    fun mockNavi(): MockNaviDataSource = MockNaviDataSource()

    // TODO(M5): Pleos NaviHelper 구현이 생기면 여기서 고른다
    @Provides
    @Singleton
    fun naviDataSource(mock: MockNaviDataSource): NaviDataSource = mock
}
