package com.roadnovel.data.navi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 내비 정보 공급원 (CLAUDE.md 6장). Pleos NaviHelper 구현은 M5. */
interface NaviDataSource {
    /** 목적지까지 남은 시간(분). 경로가 없으면 null */
    val remainingMinutes: Flow<Int?>
}

/** 가짜 내비. 디버그 패널에서 남은 시간을 바꿀 수 있다. */
class MockNaviDataSource(startMinutes: Int? = 23) : NaviDataSource {
    private val _remaining = MutableStateFlow(startMinutes)
    override val remainingMinutes: StateFlow<Int?> = _remaining.asStateFlow()

    fun setRemainingMinutes(min: Int?) {
        _remaining.value = min?.coerceAtLeast(0)
    }
}
