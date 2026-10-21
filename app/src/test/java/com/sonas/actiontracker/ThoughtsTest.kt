package com.sonas.actiontracker

import com.sonas.actiontracker.data.Thoughts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThoughtsTest {
    @Test fun neverTheSameTwiceInARow() {
        val random = Random(5)
        var last = -1
        repeat(500) {
            val i = Thoughts.next(last, random)
            assertTrue(i in Thoughts.all.indices)
            assertNotEquals(last, i)
            last = i
        }
    }

    @Test fun everyThoughtCanCome() {
        val random = Random(9)
        val seen = (0 until 5_000).map { Thoughts.next(it % Thoughts.all.size, random) }.toSet()
        assertEquals(Thoughts.all.indices.toSet(), seen)
    }

    @Test fun shortLinesFromEverySource() {
        assertTrue(Thoughts.all.all { it.text.length <= 110 && it.source.isNotBlank() })
        listOf("Seneca", "Marcus Aurelius", "Epictetus", "Bhagavad Gita", "Thirukkural", "Rumi", "Peter Drucker").forEach { s ->
            assertTrue(s, Thoughts.all.any { it.source.startsWith(s) })
        }
    }
}
