package com.jian.nemo.core.data.mapper

import com.jian.nemo.core.data.local.entity.GrammarEntity
import com.jian.nemo.core.data.local.entity.GrammarExampleEntity
import com.jian.nemo.core.data.local.entity.GrammarStudyStateEntity
import com.jian.nemo.core.data.local.entity.GrammarUsageEntity
import com.jian.nemo.core.data.local.entity.relations.GrammarWithUsages
import com.jian.nemo.core.data.local.entity.relations.UsageWithExamples
import com.jian.nemo.core.data.mapper.GrammarMapper.toDomainModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarMapperTest {

    @Test
    fun `toDomainModel should preserve study state for learned grammar`() {
        val relation = GrammarWithUsages(
            grammar = GrammarEntity(
                id = 1,
                grammar = "〜ている",
                grammarLevel = "N5",
                isDelisted = false
            ),
            usages = listOf(
                UsageWithExamples(
                    usage = GrammarUsageEntity(
                        id = 10,
                        grammarId = 1,
                        subtype = null,
                        connection = "Vて + いる",
                        explanation = "表示动作正在进行",
                        notes = null,
                        usageOrder = 0
                    ),
                    examples = listOf(
                        GrammarExampleEntity(
                            id = 100,
                            usageId = 10,
                            sentence = "今、本を読んでいる。",
                            translation = "现在正在看书。",
                            source = null,
                            isDialog = false,
                            exampleOrder = 0
                        )
                    )
                )
            ),
            state = GrammarStudyStateEntity(
                grammarId = 1,
                repetitionCount = 4,
                stability = 6.8f,
                difficulty = 5.2f,
                interval = 9,
                nextReviewDate = 20000L,
                lastReviewedDate = 19900L,
                firstLearnedDate = 15000L,
                isFavorite = true,
                isSkipped = false,
                buriedUntilDay = 0,
                lastModifiedTime = 30000L
            )
        )

        val domain = relation.toDomainModel()

        assertEquals(1, domain.id)
        assertEquals("〜ている", domain.grammar)
        assertEquals("N5", domain.grammarLevel)

        // Regression lock: mapped grammar must keep study-state values.
        assertEquals(4, domain.repetitionCount)
        assertEquals(9, domain.interval)
        assertEquals(6.8f, domain.stability, 0.001f)
        assertEquals(5.2f, domain.difficulty, 0.001f)
        assertEquals(20000L, domain.nextReviewDate)
        assertEquals(19900L, domain.lastReviewedDate)
        assertEquals(15000L, domain.firstLearnedDate)
        assertTrue(domain.isFavorite)
        assertFalse(domain.isSkipped)
        assertEquals(30000L, domain.lastModifiedTime)

        assertEquals(1, domain.usages.size)
        assertEquals(1, domain.usages.first().examples.size)
    }

    @Test
    fun `toDomainModel should fallback to defaults when study state is missing`() {
        val relation = GrammarWithUsages(
            grammar = GrammarEntity(
                id = 2,
                grammar = "〜ながら",
                grammarLevel = "N4",
                isDelisted = false
            ),
            usages = emptyList(),
            state = null
        )

        val domain = relation.toDomainModel()

        assertEquals(0, domain.repetitionCount)
        assertEquals(0, domain.interval)
        assertEquals(0f, domain.stability, 0.001f)
        assertEquals(0f, domain.difficulty, 0.001f)
        assertEquals(0L, domain.nextReviewDate)
        assertNull(domain.lastReviewedDate)
        assertNull(domain.firstLearnedDate)
        assertFalse(domain.isFavorite)
        assertFalse(domain.isSkipped)
        assertEquals(0L, domain.lastModifiedTime)
    }
}
