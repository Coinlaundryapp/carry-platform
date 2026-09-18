package com.carry.infra.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionMappingTest {
    @Test
    fun `replaceAllFrom replaces existing list elements via transform`() {
        val target = mutableListOf("old1", "old2")
        target.replaceAllFrom(listOf(1, 2, 3)) { "n$it" }
        assertEquals(listOf("n1", "n2", "n3"), target)
    }

    @Test
    fun `replaceAllFrom with empty source clears the collection`() {
        val target = mutableListOf("a", "b")
        target.replaceAllFrom(emptyList<Int>()) { "n$it" }
        assertTrue(target.isEmpty())
    }

    @Test
    fun `replaceAllFrom works on a MutableSet`() {
        val target = mutableSetOf("old")
        target.replaceAllFrom(listOf(1, 2)) { "n$it" }
        assertEquals(setOf("n1", "n2"), target)
    }
}
