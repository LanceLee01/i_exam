package com.examhelper.app.knowledge

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class KBEntryTest {

    // ── computeTrigrams ──────────────────────────────────────────────

    @Test
    fun `computeTrigrams exact 3 chars returns set with one trigram`() {
        assertEquals(setOf("abc"), KBEntry.computeTrigrams("abc"))
    }

    @Test
    fun `computeTrigrams length less than 3 returns empty set`() {
        assertAll(
            { assertEquals(emptySet<String>(), KBEntry.computeTrigrams("ab")) },
            { assertEquals(emptySet<String>(), KBEntry.computeTrigrams("a")) },
            { assertEquals(emptySet<String>(), KBEntry.computeTrigrams("")) },
        )
    }

    @Test
    fun `computeTrigrams Chinese characters generates correct trigrams`() {
        assertEquals(setOf("你好世", "好世界"), KBEntry.computeTrigrams("你好世界"))
    }

    @Test
    fun `computeTrigrams punctuation is filtered before trigrams`() {
        assertEquals(setOf("abc"), KBEntry.computeTrigrams("a.b,c"))
    }

    @Test
    fun `computeTrigrams empty string returns empty set`() {
        assertEquals(emptySet<String>(), KBEntry.computeTrigrams(""))
    }

    @Test
    fun `computeTrigrams whitespace is filtered before trigrams`() {
        assertEquals(
            setOf("hel", "ell", "llo", "low", "owo", "wor", "orl", "rld"),
            KBEntry.computeTrigrams("hello world")
        )
    }

    @Test
    fun `computeTrigrams multiple punctuation and whitespace combined`() {
        assertEquals(
            setOf("hel", "ell", "llo", "low", "owo", "wor", "orl", "rld"),
            KBEntry.computeTrigrams("  he.l,l o  wo,rld!  ")
        )
    }

    @Test
    fun `computeTrigrams longer string produces correct trigram count`() {
        val trigrams = KBEntry.computeTrigrams("abcdefgh")
        // "abcdefgh" → 8 chars → 6 trigrams: abc bcd cde def efg fgh
        assertEquals(setOf("abc", "bcd", "cde", "def", "efg", "fgh"), trigrams)
        assertEquals(6, trigrams.size)
    }

    // ── jaccard ───────────────────────────────────────────────────────

    @Test
    fun `jaccard same set returns 1`() {
        assertEquals(1.0f, KBEntry.jaccard(setOf("a", "b", "c"), setOf("a", "b", "c")))
    }

    @Test
    fun `jaccard disjoint sets returns 0`() {
        assertEquals(0.0f, KBEntry.jaccard(setOf("a", "b"), setOf("c", "d")))
    }

    @Test
    fun `jaccard partial overlap returns value between 0 and 1 exclusive`() {
        val result = KBEntry.jaccard(setOf("a", "b", "c"), setOf("b", "c", "d"))
        // intersection = {b, c} size 2, union = {a, b, c, d} size 4 → 2/4 = 0.5
        assertEquals(0.5f, result)
    }

    @ParameterizedTest
    @MethodSource("emptySetProvider")
    fun `jaccard either set empty returns 0`(a: Set<String>, b: Set<String>) {
        assertEquals(0.0f, KBEntry.jaccard(a, b))
    }

    companion object {
        @JvmStatic
        fun emptySetProvider() = listOf(
            Arguments.of(emptySet<String>(), setOf("a", "b")),
            Arguments.of(setOf("a", "b"), emptySet<String>()),
            Arguments.of(emptySet<String>(), emptySet<String>()),
        )
    }

    @Test
    fun `jaccard one element overlap`() {
        val result = KBEntry.jaccard(setOf("x"), setOf("x", "y", "z"))
        // intersection = {x} size 1, union = {x, y, z} size 3 → 1/3
        assertEquals(1.0f / 3.0f, result, 0.0001f)
    }

    // ── computeSHA256 ─────────────────────────────────────────────────

    @Test
    fun `computeSHA256 same input produces same hash`() {
        val input = "hello world".toByteArray()
        assertEquals(KBEntry.computeSHA256(input), KBEntry.computeSHA256(input))
    }

    @Test
    fun `computeSHA256 different input produces different hash`() {
        val hash1 = KBEntry.computeSHA256("hello".toByteArray())
        val hash2 = KBEntry.computeSHA256("world".toByteArray())
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeSHA256 known vector`() {
        // SHA-256 of empty string is known
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, KBEntry.computeSHA256(ByteArray(0)))
    }

    @Test
    fun `computeSHA256 non-empty produces 64 char hex string`() {
        val hash = KBEntry.computeSHA256("test data".toByteArray())
        assertEquals(64, hash.length)
        assert(hash.matches(Regex("[0-9a-f]{64}")))
    }
}
