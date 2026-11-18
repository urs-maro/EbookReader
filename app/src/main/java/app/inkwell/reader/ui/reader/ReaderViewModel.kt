package app.inkwell.reader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.inkwell.reader.InkwellApp
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookFormat
import app.inkwell.reader.data.LibraryBook
import app.inkwell.reader.data.ReadingPosition
import app.inkwell.reader.reader.BookOpener
import app.inkwell.reader.reader.UnsupportedFormatException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Error(val message: String) : ReaderState
    data class Loaded(val content: BookContent, val book: LibraryBook) : ReaderState
}

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as InkwellApp
    val settings = appRef.settings.settings

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var bookId: String? = null

    fun load(id: String) {
        if (bookId == id && _state.value is ReaderState.Loaded) return
        bookId = id
        _state.value = ReaderState.Loading
        viewModelScope.launch {
            val book = appRef.library.get(id)
            if (book == null) {
                _state.value = ReaderState.Error("Cartea nu a fost găsită")
                return@launch
            }
            val file = appRef.library.fileFor(book)
            val format = runCatching { BookFormat.valueOf(book.format) }.getOrDefault(BookFormat.UNKNOWN)
            try {
                val content = BookOpener.open(appRef, file, format)
                _state.value = ReaderState.Loaded(content, book)
            } catch (e: UnsupportedFormatException) {
                _state.value = ReaderState.Error(e.message ?: "Format nesuportat")
            } catch (e: Exception) {
                _state.value = ReaderState.Error("Nu pot deschide cartea: ${e.message}")
            }
        }
    }

    fun savePosition(position: ReadingPosition) {
        val id = bookId ?: return
        viewModelScope.launch { appRef.library.updatePosition(id, position) }
    }

    fun updateSettings(transform: (app.inkwell.reader.data.ReadingSettings) -> app.inkwell.reader.data.ReadingSettings) {
        viewModelScope.launch { appRef.settings.update(transform) }
    }
}
