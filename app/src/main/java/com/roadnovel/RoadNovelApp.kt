package com.roadnovel

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** 앱 시작점. Hilt가 여기서부터 의존성을 준비한다. */
@HiltAndroidApp
class RoadNovelApp : Application()
