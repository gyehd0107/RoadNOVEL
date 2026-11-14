package com.roadnovel.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 앱 전체에서 쓰는 객체를 여기서 만든다.
 * M1부터 StoryRepository, VehicleDataSource(Mock/Pleos) 등을 여기에 연결한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
