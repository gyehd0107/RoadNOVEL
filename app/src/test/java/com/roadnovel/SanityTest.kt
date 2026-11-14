package com.roadnovel

import org.junit.Assert.assertTrue
import org.junit.Test

/** 단위 테스트 환경이 동작하는지 확인용. M1에서 StoryEngine 테스트로 채운다. */
class SanityTest {
    @Test
    fun storyJsonIsInAssets() {
        assertTrue(java.io.File("src/main/assets/stories/star_map_expedition.json").exists())
    }
}
