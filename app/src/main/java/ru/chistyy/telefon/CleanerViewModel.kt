package ru.chistyy.telefon

import android.app.Application
import android.net.Uri
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanerViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = StorageScanner(app)

    var loading by mutableStateOf(false)
        private set
    var candidates by mutableStateOf<List<Candidate>>(emptyList())
        private set
    var selectedCategories by mutableStateOf(Category.values().toSet())
        private set
    var freeFraction by mutableStateOf(1f)
        private set
    var message by mutableStateOf<String?>(null)

    val lowSpace: Boolean get() = freeFraction < LOW_SPACE_LIMIT

    val selectedBytes: Long
        get() = candidates.filter { it.category in selectedCategories }.sumOf { it.sizeBytes }

    fun sizeOf(category: Category): Long =
        candidates.filter { it.category == category }.sumOf { it.sizeBytes }

    fun toggle(category: Category) {
        selectedCategories = if (category in selectedCategories) {
            selectedCategories - category
        } else {
            selectedCategories + category
        }
    }

    fun urisToDelete(): List<Uri> =
        candidates.filter { it.category in selectedCategories }.map { it.uri }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            candidates = withContext(Dispatchers.IO) { scanner.scanAll() }
            freeFraction = withContext(Dispatchers.IO) {
                val stat = StatFs(getApplication<Application>().filesDir.path)
                stat.availableBytes.toFloat() / stat.totalBytes.toFloat()
            }
            loading = false
        }
    }

    fun onDeleteFinished(deleted: Boolean) {
        message = if (deleted) "Файлы удалены" else "Удаление отменено"
        if (deleted) refresh()
    }

    companion object {
        const val LOW_SPACE_LIMIT = 0.15f
    }
}
