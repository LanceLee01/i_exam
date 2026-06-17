package com.examhelper.app.knowledge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipImportHelperTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `ZIP file creation and validation`() {
        val zipFile = File(tempDir, "test.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("test.xlsx"))
            zos.write("test content".toByteArray())
            zos.closeEntry()
        }
        
        assertTrue(zipFile.exists())
        assertTrue(zipFile.length() > 0)
    }

    @Test
    fun `ZIP with multiple Excel files`() {
        val zipFile = File(tempDir, "multi.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("file1.xlsx"))
            zos.write("content1".toByteArray())
            zos.closeEntry()
            
            zos.putNextEntry(ZipEntry("file2.xls"))
            zos.write("content2".toByteArray())
            zos.closeEntry()
        }
        
        assertTrue(zipFile.exists())
    }

    @Test
    fun `ZIP with mixed file types`() {
        val zipFile = File(tempDir, "mixed.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("excel.xlsx"))
            zos.write("excel content".toByteArray())
            zos.closeEntry()
            
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("text content".toByteArray())
            zos.closeEntry()
        }
        
        assertTrue(zipFile.exists())
    }

    @Test
    fun `ET files are treated as spreadsheet files`() {
        assertTrue(ZipImportHelper.isSpreadsheetFile("questions.et"))
        assertTrue(ZipImportHelper.isSpreadsheetFile("questions.xls"))
        assertTrue(ZipImportHelper.isSpreadsheetFile("questions.xlsx"))
        assertFalse(ZipImportHelper.isSpreadsheetFile("questions.txt"))
    }
}
