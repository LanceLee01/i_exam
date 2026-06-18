package com.examhelper.app.knowledge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KnowledgeBaseManagerTest {

    // ── ImportRecord ──────────────────────────────────────────────

    @Test
    fun `ImportRecord default values`() {
        val record = ImportRecord(fileName = "test.xls", hash = "abc123")
        assertEquals("test.xls", record.fileName)
        assertEquals("abc123", record.hash)
        assertEquals(0, record.entryCount)
        assertTrue(record.importedAt > 0)
    }

    @Test
    fun `ImportRecord with entry count`() {
        val record = ImportRecord(fileName = "test.xls", hash = "def456", entryCount = 42)
        assertEquals(42, record.entryCount)
    }

    // ── KnowledgeBase updateEntry ─────────────────────────────────

    @Test
    fun `updateEntry replaces entry at correct index`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))
        kb.entries.add(KBEntry(question = "Q3", answer = "A3"))

        kb.updateEntry(1, KBEntry(question = "Q2-edited", answer = "A2-edited"))

        assertEquals(3, kb.entries.size)
        assertEquals("Q1", kb.entries[0].question)
        assertEquals("Q2-edited", kb.entries[1].question)
        assertEquals("Q3", kb.entries[2].question)
    }

    @Test
    fun `updateEntry out of bounds does nothing`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))

        kb.updateEntry(5, KBEntry(question = "Q5", answer = "A5"))

        assertEquals(1, kb.entries.size)
        assertEquals("Q1", kb.entries[0].question)
    }

    @Test
    fun `updateEntry rebuilds feature cache`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "安全生产管理办法是什么", answer = "A1"))
        kb.buildFeatureCache()

        kb.updateEntry(0, KBEntry(question = "安全管理规定有哪些", answer = "A2"))

        // 搜索新题目应该能匹配
        val results = kb.search("安全管理规定")
        assertTrue(results.isNotEmpty())
    }

    // ── KnowledgeBase deleteEntries ───────────────────────────────

    @Test
    fun `deleteEntries removes specified indices`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))
        kb.entries.add(KBEntry(question = "Q3", answer = "A3"))
        kb.entries.add(KBEntry(question = "Q4", answer = "A4"))

        kb.deleteEntries(setOf(1, 3))

        assertEquals(2, kb.entries.size)
        assertEquals("Q1", kb.entries[0].question)
        assertEquals("Q3", kb.entries[1].question)
    }

    @Test
    fun `deleteEntries with out of bounds indices handled gracefully`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))

        kb.deleteEntries(setOf(0, 99, -1))

        assertEquals(0, kb.entries.size)
    }

    @Test
    fun `deleteEntries empty set does nothing`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))

        kb.deleteEntries(emptySet())

        assertEquals(2, kb.entries.size)
    }

    @Test
    fun `deleteEntries rebuilds feature cache when entries removed`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))
        kb.buildFeatureCache()

        kb.deleteEntries(setOf(0))

        assertEquals(1, kb.entries.size)
        // 验证缓存已重建（不抛异常即可）
        val results = kb.search("Q2")
        assertTrue(results.isNotEmpty())
    }

    // ── KnowledgeBase getImportFiles ──────────────────────────────

    @Test
    fun `getImportFiles returns records sorted by time descending`() {
        val kb = KnowledgeBase(name = "test")
        kb.importRecords.add(
            ImportRecord(fileName = "old.xls", hash = "111", importedAt = 1000L, entryCount = 5)
        )
        kb.importRecords.add(
            ImportRecord(fileName = "new.xls", hash = "222", importedAt = 2000L, entryCount = 10)
        )

        val files = kb.getImportFiles()

        assertEquals(2, files.size)
        assertEquals("new.xls", files[0].fileName) // 最新的在前
        assertEquals("old.xls", files[1].fileName)
    }
}
