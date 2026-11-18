package app.inkwell.reader.core

import androidx.compose.ui.text.AnnotatedString

/** Supported ebook container formats. */
enum class BookFormat(val label: String) {
    EPUB("EPUB"),
    PDF("PDF"),
    TXT("Text"),
    FB2("FB2"),
    CBZ("Comic"),
    MOBI("MOBI"),
    AZW3("AZW3"),
    UNKNOWN("?");

    val isReflowable: Boolean
        get() = this == EPUB || this == TXT || this == FB2 || this == MOBI || this == AZW3
}

/** Kind of a paragraph, drives typographic styling during layout. */
enum class BlockKind { TITLE, HEADING, SUBHEADING, BODY, QUOTE, SEPARATOR, IMAGE }

/**
 * A single flow element of a chapter. For text blocks [text] holds inline styling
 * (bold / italic). For [BlockKind.IMAGE] blocks [imageRef] points to a resource.
 */
data class RichBlock(
    val kind: BlockKind,
    val text: AnnotatedString = AnnotatedString(""),
    val imageRef: String? = null,
)

/** A chapter / spine item of a reflowable book. */
data class Chapter(
    val id: String,
    val title: String?,
    val blocks: List<RichBlock>,
)

/** An entry in the table of contents. */
data class TocEntry(
    val title: String,
    val chapterIndex: Int,
    val depth: Int = 0,
)

/** Metadata extracted from the source file. */
data class BookMeta(
    val title: String,
    val author: String?,
    val language: String? = null,
)

/**
 * The fully-parsed content of a book, ready to be paginated or displayed.
 * Reflowable books expose [chapters]; fixed/image books expose [imageProvider].
 */
sealed interface BookContent {
    val meta: BookMeta
    val toc: List<TocEntry>

    data class Reflowable(
        override val meta: BookMeta,
        override val toc: List<TocEntry>,
        val chapters: List<Chapter>,
        /** Resolves an [RichBlock.imageRef] to raw bytes, if the format carries images. */
        val imageResolver: (suspend (String) -> ByteArray?)? = null,
    ) : BookContent

    data class Paged(
        override val meta: BookMeta,
        val pageCount: Int,
        /** Renders page [index] (0-based) to a bitmap sized to fit [maxWidth] x [maxHeight]. */
        val imageProvider: ImagePageProvider,
        override val toc: List<TocEntry> = emptyList(),
    ) : BookContent
}

/** Lazily renders a page to a bitmap; implementations may be backed by PdfRenderer, a zip, etc. */
interface ImagePageProvider {
    val count: Int
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): android.graphics.Bitmap?
    fun close() {}
}
