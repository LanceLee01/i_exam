package com.examhelper.app.knowledge

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    // ══════════════════════════════════════════════════════════════════════
    // computeBigrams
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `computeBigrams exact 2 chars returns one bigram`() {
        assertEquals(setOf("ab"), KBEntry.computeBigrams("ab"))
    }

    @Test
    fun `computeBigrams less than 2 chars returns empty`() {
        assertEquals(emptySet<String>(), KBEntry.computeBigrams("a"))
    }

    @Test
    fun `computeBigrams Chinese produces correct bigrams`() {
        assertEquals(setOf("你好", "好世", "世界"), KBEntry.computeBigrams("你好世界"))
    }

    @Test
    fun `computeBigrams with punctuation filters correctly`() {
        assertEquals(setOf("ab"), KBEntry.computeBigrams("a.b"))
    }

    @Test
    fun `computeBigrams with whitespace filters correctly`() {
        assertEquals(setOf("ab", "bc", "cd"), KBEntry.computeBigrams("ab cd"))
    }

    // ══════════════════════════════════════════════════════════════════════
    // computeTokenSplits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `computeTokenSplits simple words`() {
        assertEquals(setOf("hello", "world"), KBEntry.computeTokenSplits("hello world"))
    }

    @Test
    fun `computeTokenSplits filters numbers and short tokens`() {
        // "第123条" split on digits → ["第", "条"], both < 2 chars → filtered
        assertEquals(emptySet<String>(), KBEntry.computeTokenSplits("第123条"))
    }

    @Test
    fun `computeTokenSplits Chinese single token`() {
        assertEquals(setOf("安全生产管理办法"), KBEntry.computeTokenSplits("安全生产管理办法"))
    }

    @Test
    fun `computeTokenSplits empty string`() {
        assertEquals(emptySet<String>(), KBEntry.computeTokenSplits(""))
    }

    // ══════════════════════════════════════════════════════════════════════
    // computeCharSet
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `computeCharSet simple dedup`() {
        assertEquals(setOf('a', 'b', 'c'), KBEntry.computeCharSet("abca"))
    }

    @Test
    fun `computeCharSet Chinese`() {
        assertEquals(setOf('安', '全', '生', '产'), KBEntry.computeCharSet("安全生产"))
    }

    @Test
    fun `computeCharSet ignores punctuation`() {
        assertEquals(setOf('a', 'b'), KBEntry.computeCharSet("a.b,"))
    }

    // ══════════════════════════════════════════════════════════════════════
    // computeLCSRatio
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `computeLCSRatio identical strings`() {
        assertEquals(1.0f, KBEntry.computeLCSRatio("hello", "hello"))
    }

    @Test
    fun `computeLCSRatio completely different`() {
        assertEquals(0.0f, KBEntry.computeLCSRatio("abc", "xyz"))
    }

    @Test
    fun `computeLCSRatio partial overlap`() {
        // LCS of "abcdef" and "acf" is "acf" = 3
        // Dice: 2*3/(6+3) = 6/9 = 0.666...
        assertEquals(6.0f / 9.0f, KBEntry.computeLCSRatio("abcdef", "acf"), 0.001f)
    }

    @Test
    fun `computeLCSRatio Chinese partial`() {
        // "安全生产责任" vs "安全管理责任": LCS = "安全责任" = 4 chars
        // Dice: 2*4/(6+6) = 8/12 = 0.666...
        val ratio = KBEntry.computeLCSRatio("安全生产责任", "安全管理责任")
        assertTrue(ratio > 0.5f, "Expected > 0.5 but was $ratio")
    }

    @Test
    fun `computeLCSRatio one empty`() {
        assertEquals(0.0f, KBEntry.computeLCSRatio("", "abc"))
    }

    // ══════════════════════════════════════════════════════════════════════
    // hybridTextScore
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `hybridTextScore identical short Chinese`() {
        // "安全生产" vs "安全生产" — bigrams + tokens contribute
        val score = KBEntry.hybridTextScore("安全生产", "安全生产")
        assertTrue(score > 0.5f, "Expected >0.5 for identical text, got $score")
    }

    @Test
    fun `hybridTextScore similar Chinese higher than dissimilar`() {
        val similar = KBEntry.hybridTextScore("安全生产", "安全操作")
        val different = KBEntry.hybridTextScore("安全生产", "质量控制")
        assertTrue(similar > different, "Similar texts should score higher")
    }

    @Test
    fun `hybridTextScore returns value in 0 to 1 range`() {
        val score = KBEntry.hybridTextScore("hello world", "hello there")
        assertTrue(score in 0f..1f, "Score should be in [0,1], got $score")
    }
}
