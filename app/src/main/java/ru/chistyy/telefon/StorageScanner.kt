package ru.chistyy.telefon

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.util.concurrent.TimeUnit

enum class Category(val title: String) {
    DOWNLOADS("Старые загрузки"),
    SCREENSHOTS("Скриншоты старше 30 дней")
}

data class Candidate(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val category: Category
)

class StorageScanner(private val context: Context) {

    fun scanAll(): List<Candidate> = scanDownloads() + scanScreenshots()

    private fun cutoffSeconds(): String {
        val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        return (cutoffMs / 1000).toString()
    }

    private fun scanDownloads(): List<Candidate> = query(
        collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        selection = "${MediaStore.MediaColumns.DATE_MODIFIED} < ?",
        args = arrayOf(cutoffSeconds()),
        category = Category.DOWNLOADS
    )

    private fun scanScreenshots(): List<Candidate> = query(
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        selection = "${MediaStore.MediaColumns.DATE_MODIFIED} < ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
        args = arrayOf(cutoffSeconds(), "%Screenshots%"),
        category = Category.SCREENSHOTS
    )

    private fun query(
        collection: Uri,
        selection: String,
        args: Array<String>,
        category: Category
    ): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (c.moveToNext()) {
                result += Candidate(
                    uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                    name = c.getString(nameCol) ?: "",
                    sizeBytes = c.getLong(sizeCol),
                    category = category
                )
            }
        }
        return result
    }
}
