package app.inkwell.reader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores the library as a single JSON file plus a directory of imported book files and
 * cover thumbnails. Simple, dependency-light, and good enough for a personal library.
 */
class LibraryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    val booksDir: File = File(appContext.filesDir, "library").apply { mkdirs() }
    val coversDir: File = File(appContext.filesDir, "covers").apply { mkdirs() }
    private val indexFile = File(appContext.filesDir, "library.json")

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private fun load(): LibraryState = runCatching {
        if (indexFile.exists()) json.decodeFromString<LibraryState>(indexFile.readText())
        else LibraryState()
    }.getOrDefault(LibraryState())

    private suspend fun persist(newState: LibraryState) = withContext(Dispatchers.IO) {
        _state.value = newState
        runCatching { indexFile.writeText(json.encodeToString(LibraryState.serializer(), newState)) }
    }

    fun fileFor(book: LibraryBook): File = File(booksDir, book.fileName)
    fun coverFor(book: LibraryBook): File? = book.coverFileName?.let { File(coversDir, it) }

    suspend fun upsert(book: LibraryBook) = mutex.withLock {
        val list = _state.value.books.filter { it.id != book.id } + book
        persist(LibraryState(list.sortedByDescending { it.lastOpenedAt.coerceAtLeast(it.addedAt) }))
    }

    suspend fun updatePosition(id: String, position: ReadingPosition) = mutex.withLock {
        val list = _state.value.books.map {
            if (it.id == id) it.copy(position = position, lastOpenedAt = System.currentTimeMillis()) else it
        }
        persist(LibraryState(list))
    }

    suspend fun delete(book: LibraryBook) = mutex.withLock {
        runCatching { fileFor(book).delete() }
        coverFor(book)?.let { runCatching { it.delete() } }
        persist(LibraryState(_state.value.books.filter { it.id != book.id }))
    }

    fun get(id: String): LibraryBook? = _state.value.books.firstOrNull { it.id == id }
}
