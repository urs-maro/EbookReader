package app.inkwell.reader.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.inkwell.reader.core.ImagePageProvider
import app.inkwell.reader.data.ReadingPosition

/** A book flattened into a fixed sequence of equally-sized pages, each renderable to a bitmap. */
interface PaginatedBook {
    val pageCount: Int
    suspend fun bitmap(index: Int): ImageBitmap?
    fun positionForPage(index: Int): ReadingPosition
    fun pageForPosition(position: ReadingPosition): Int
    fun label(index: Int): String
    fun close() {}
}

/** Simple LRU-ish cache for rendered page bitmaps. */
private class BitmapCache(private val capacity: Int = 6) {
    private val map = LinkedHashMap<Int, ImageBitmap>()
    @Synchronized fun get(i: Int): ImageBitmap? = map[i]
    @Synchronized fun put(i: Int, b: ImageBitmap) {
        map[i] = b
        while (map.size > capacity) {
            val oldest = map.keys.firstOrNull() ?: break
            map.remove(oldest)
        }
    }
    @Synchronized fun clear() = map.clear()
}

/** Reflowable book: pages precomputed by the [Paginator], rendered to bitmaps on demand. */
class ReflowBook(
    private val pages: List<Pair<Int, Page>>, // chapterIndex to page
    private val chapterTitles: List<String?>,
    private val widthPx: Int,
    private val heightPx: Int,
    private val marginXPx: Float,
    private val marginYPx: Float,
    private val background: androidx.compose.ui.graphics.Color,
    private val measurer: androidx.compose.ui.text.TextMeasurer,
    private val density: androidx.compose.ui.unit.Density,
    private val backgroundImage: ImageBitmap? = null,
) : PaginatedBook {

    private val cache = BitmapCache()
    override val pageCount: Int get() = pages.size

    override suspend fun bitmap(index: Int): ImageBitmap? {
        if (index !in pages.indices) return null
        cache.get(index)?.let { return it }
        val (_, page) = pages[index]
        val bmp = PageRenderer.render(
            page, widthPx, heightPx, marginXPx, marginYPx, background, measurer, density,
            backgroundImage,
        )
        cache.put(index, bmp)
        return bmp
    }

    override fun positionForPage(index: Int): ReadingPosition {
        val safe = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val (chapter, page) = pages.getOrElse(safe) { 0 to Page(emptyList(), 0) }
        return ReadingPosition(
            chapterIndex = chapter,
            blockIndex = page.startBlockIndex,
            pageIndex = safe,
            progress = if (pageCount > 1) safe.toFloat() / (pageCount - 1) else 1f,
        )
    }

    override fun pageForPosition(position: ReadingPosition): Int {
        var best = 0
        pages.forEachIndexed { i, (chapter, page) ->
            if (chapter < position.chapterIndex ||
                (chapter == position.chapterIndex && page.startBlockIndex <= position.blockIndex)
            ) best = i
        }
        return best
    }

    override fun label(index: Int): String {
        val chapter = pages.getOrNull(index)?.first ?: return ""
        return chapterTitles.getOrNull(chapter)?.takeIf { it.isNotBlank() }
            ?: "Capitolul ${chapter + 1}"
    }
}

/** Fixed-layout book (scanned PDF, comics): each page is an image. */
class PagedImageBook(
    private val provider: ImagePageProvider,
    private val widthPx: Int,
    private val heightPx: Int,
) : PaginatedBook {
    private val cache = BitmapCache()
    override val pageCount: Int get() = provider.count

    override suspend fun bitmap(index: Int): ImageBitmap? {
        if (index !in 0 until provider.count) return null
        cache.get(index)?.let { return it }
        val bmp = provider.render(index, widthPx, heightPx)?.asImageBitmap() ?: return null
        cache.put(index, bmp)
        return bmp
    }

    override fun positionForPage(index: Int): ReadingPosition =
        ReadingPosition(
            pageIndex = index,
            progress = if (pageCount > 1) index.toFloat() / (pageCount - 1) else 1f,
        )

    override fun pageForPosition(position: ReadingPosition): Int =
        position.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    override fun label(index: Int): String = "Pagina ${index + 1} / $pageCount"

    override fun close() = provider.close()
}
