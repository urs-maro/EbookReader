package app.inkwell.reader.data

import kotlinx.serialization.Serializable

/** Persisted reading position within a book. */
@Serializable
data class ReadingPosition(
    val chapterIndex: Int = 0,
    val blockIndex: Int = 0,
    val pageIndex: Int = 0, // for fixed/image books
    val progress: Float = 0f, // 0..1 overall, for the library progress ring
)

/** A book as stored in the user's library. The actual file lives under filesDir/library. */
@Serializable
data class LibraryBook(
    val id: String,
    val title: String,
    val author: String? = null,
    val format: String,
    val fileName: String,
    val coverFileName: String? = null,
    val addedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
    val position: ReadingPosition = ReadingPosition(),
)

@Serializable
data class LibraryState(
    val books: List<LibraryBook> = emptyList(),
)
