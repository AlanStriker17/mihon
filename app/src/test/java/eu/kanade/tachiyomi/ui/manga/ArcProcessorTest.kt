package eu.kanade.tachiyomi.ui.manga

import org.junit.jupiter.api.Test
import io.kotest.matchers.shouldBe

class ArcProcessorTest {

    @Test
    fun `groups consecutive seasons and arcs while preserving chapter order`() {
        val groups = ArcProcessor.group(
            listOf("Season 1 Arc: Training 1", "Season 1 Arc: Training 2", "S2 Arc: War 1", "Chapter 8"),
        ) { it }

        groups.map { it.title to it.chapters } shouldBe listOf(
                "Season 1 · Arc Training" to listOf("Season 1 Arc: Training 1", "Season 1 Arc: Training 2"),
                "Season 2 · Arc War" to listOf("S2 Arc: War 1"),
                "Chapters" to listOf("Chapter 8"),
            )
    }

    @Test
    fun `keeps distinct repeated groups separate when source order changes`() {
        val groups = ArcProcessor.group(listOf("Arc: North", "Arc: South", "Arc: North")) { it }

        groups.map { it.title } shouldBe listOf("Arc North", "Arc South", "Arc North")
    }
}
