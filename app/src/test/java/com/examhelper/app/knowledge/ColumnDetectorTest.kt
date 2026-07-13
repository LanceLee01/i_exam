package com.examhelper.app.knowledge

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ColumnDetectorTest {

    private val detector = ColumnDetector()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun createCell(text: String): Cell {
        val cell = mockk<Cell>()
        every { cell.toString() } returns text
        return cell
    }

    // ═════════════════════════════════════════════════════════════════════════
    // detectByHeader tests
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `should detect Chinese headers`() {
        val sheet = mockk<Sheet>()
        val row = mockk<Row>()
        every { sheet.getRow(0) } returns row
        every { sheet.getRow(1) } returns null
        every { sheet.getRow(2) } returns null
        every { row.lastCellNum } returns 8 // cols 0-7
        every { row.getCell(any<Int>()) } returns null
        every { row.getCell(5) } returns createCell("题目")
        every { row.getCell(6) } returns createCell("来源")
        every { row.getCell(7) } returns createCell("答案")

        val result = detector.detectByHeader(sheet)

        assertEquals(ColumnMapping(5, 7, 6), result)
    }

    @Test
    fun `should detect English headers`() {
        val sheet = mockk<Sheet>()
        val row = mockk<Row>()
        every { sheet.getRow(0) } returns row
        every { sheet.getRow(1) } returns null
        every { sheet.getRow(2) } returns null
        every { row.lastCellNum } returns 3 // cols 0-2
        every { row.getCell(any<Int>()) } returns null
        every { row.getCell(0) } returns createCell("Question")
        every { row.getCell(2) } returns createCell("Answer")

        val result = detector.detectByHeader(sheet)

        assertEquals(ColumnMapping(0, 2, null), result)
    }

    @Test
    fun `should return null when no headers match`() {
        val sheet = mockk<Sheet>()
        val row = mockk<Row>()
        every { sheet.getRow(0) } returns row
        every { sheet.getRow(1) } returns null
        every { sheet.getRow(2) } returns null
        every { row.lastCellNum } returns 3
        every { row.getCell(any<Int>()) } returns null
        every { row.getCell(0) } returns createCell("Name")
        every { row.getCell(1) } returns createCell("Date")
        every { row.getCell(2) } returns createCell("Notes")

        val result = detector.detectByHeader(sheet)

        assertNull(result)
    }

    @Test
    fun `should scan down to row 1-2 if row0 empty`() {
        val sheet = mockk<Sheet>()
        val row1 = mockk<Row>()
        every { sheet.getRow(0) } returns null
        every { sheet.getRow(1) } returns row1
        every { sheet.getRow(2) } returns null
        every { row1.lastCellNum } returns 6
        every { row1.getCell(any<Int>()) } returns null
        every { row1.getCell(3) } returns createCell("题目")
        every { row1.getCell(5) } returns createCell("答案")

        val result = detector.detectByHeader(sheet)

        assertEquals(ColumnMapping(3, 5, null), result)
    }

    @Test
    fun `should accept null sourceCol`() {
        val sheet = mockk<Sheet>()
        val row = mockk<Row>()
        every { sheet.getRow(0) } returns row
        every { sheet.getRow(1) } returns null
        every { sheet.getRow(2) } returns null
        every { row.lastCellNum } returns 4
        every { row.getCell(any<Int>()) } returns null
        every { row.getCell(1) } returns createCell("题目")
        every { row.getCell(3) } returns createCell("答案")

        val result = detector.detectByHeader(sheet)

        assertEquals(ColumnMapping(1, 3, null), result)
    }

    @Test
    fun `should handle empty sheet`() {
        val sheet = mockk<Sheet>()
        every { sheet.getRow(0) } returns null
        every { sheet.getRow(1) } returns null
        every { sheet.getRow(2) } returns null

        val result = detector.detectByHeader(sheet)

        assertNull(result)
    }
}
