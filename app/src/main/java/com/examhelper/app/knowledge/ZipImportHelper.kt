package com.examhelper.app.knowledge

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

object ZipImportHelper {

    data class ZipImportResult(
        val success: Boolean,
        val totalFiles: Int = 0,
        val excelFiles: Int = 0,
        val importedEntries: Int = 0,
        val skippedFiles: Int = 0,
        val failedFiles: Int = 0,
        val error: String? = null
    )

    fun isSpreadsheetFile(fileName: String): Boolean {
        val name = fileName.lowercase()
        return name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".et")
    }

    fun extractExcelFiles(context: Context, zipUri: Uri): List<File>? {
        return try {
            val tempDir = File(context.cacheDir, "zip_import_${System.nanoTime()}")
            tempDir.mkdirs()
            android.util.Log.d("ZipImport", "Extracting ZIP to: ${tempDir.absolutePath}")

            // Copy ZIP to a temp file for ZipFile access
            val zipFile = File(tempDir, "temp.zip")
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                FileOutputStream(zipFile).use { fos ->
                    inputStream.copyTo(fos)
                }
            }

            val extractedFiles = mutableListOf<File>()
            val zipCharset = Charset.forName("GBK")

            ZipFile(zipFile, zipCharset).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory) {
                        val entryName = entry.name
                        if (isSpreadsheetFile(entryName)) {
                            val outFile = File(tempDir, entryName)
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { entryStream ->
                                FileOutputStream(outFile).use { fos ->
                                    entryStream.copyTo(fos)
                                }
                            }
                            android.util.Log.d("ZipImport", "Extracted: $entryName, size=${outFile.length()}")
                            extractedFiles.add(outFile)
                        }
                    }
                }
            }

            // Clean up the temp zip file
            zipFile.delete()

            android.util.Log.d("ZipImport", "Extracted ${extractedFiles.size} Excel files")

            if (extractedFiles.isEmpty()) {
                tempDir.deleteRecursively()
                null
            } else {
                extractedFiles.sortedBy { it.name }
            }
        } catch (e: java.util.zip.ZipException) {
            android.util.Log.e("ZipImport", "ZIP format error", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("ZipImport", "Extraction error", e)
            null
        }
    }

    fun cleanupTempFiles(context: Context) {
        val tempDirs = context.cacheDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("zip_import_") }
        tempDirs?.forEach { it.deleteRecursively() }
    }
}
