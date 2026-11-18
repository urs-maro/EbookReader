package app.inkwell.reader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.inkwell.reader.InkwellApp
import app.inkwell.reader.data.LibraryBook
import app.inkwell.reader.data.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as InkwellApp
    val library: LibraryRepository get() = appRef.library
    val state = appRef.library.state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    init {
        // Backfill covers for books imported before cover extraction was available/working.
        viewModelScope.launch {
            appRef.library.state.value.books
                .filter { it.coverFileName == null }
                .forEach { runCatching { appRef.importer.ensureCover(it) } }
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            var ok = 0
            uris.forEach { uri ->
                runCatching { appRef.importer.import(uri) }
                    .onSuccess { ok++ }
                    .onFailure { _message.value = "Import eșuat: ${it.message}" }
            }
            _importing.value = false
            if (ok > 0) _message.value = "Am importat $ok ${if (ok == 1) "carte" else "cărți"}"
        }
    }

    fun delete(book: LibraryBook) = viewModelScope.launch { appRef.library.delete(book) }

    fun coverFile(book: LibraryBook): File? = appRef.library.coverFor(book)

    fun clearMessage() { _message.value = null }
}
