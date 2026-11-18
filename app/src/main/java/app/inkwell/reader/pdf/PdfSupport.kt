package app.inkwell.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.text.AnnotatedString
import app.inkwell.reader.core.BlockKind
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookMeta
import app.inkwell.reader.core.Chapter
import app.inkwell.reader.core.ImagePageProvider
import app.inkwell.reader.core.RichBlock
import app.inkwell.reader.core.TocEntry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * PDF → ebook pipeline.
 *  - Digital PDFs: text is extracted with PDFBox and reflowed into a reflowable book,
 *    so it honours the same font / size / colour settings as an EPUB.
 *  - Scanned PDFs (no text layer): pages are rendered to bitmaps with the platform
 *    PdfRenderer and shown as a fixed-layout book — the app never blocks on them.
 */
object PdfSupport {

    /** Average extractable characters per page below which we assume a scanned PDF. */
    private const val SCANNED_CHARS_PER_PAGE = 90

    fun open(context: Context, file: File): BookContent {
        val info = analyze(file)
        return if (info.looksScanned) {
            BookContent.Paged(
                meta = info.meta,
                pageCount = info.pageCount,
                imageProvider = PdfPageProvider(context, file),
            )
        } else {
            reflow(file, info)
        }
    }

    /** True if this PDF has essentially no extractable text (needs image mode / OCR). */
    fun isScanned(file: File): Boolean = analyze(file).looksScanned

    private data class Info(
        val meta: BookMeta,
        val pageCount: Int,
        val looksScanned: Boolean,
    )

    private fun analyze(file: File): Info {
        PDDocument.load(file).use { doc ->
            val pages = doc.numberOfPages
            val di = doc.documentInformation
            val title = di?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val author = di?.author?.takeIf { it.isNotBlank() }
            // sample up to the first 5 pages for text density
            val stripper = PDFTextStripper()
            val sample = minOf(pages, 5)
            stripper.startPage = 1
            stripper.endPage = sample
            val text = runCatching { stripper.getText(doc) }.getOrDefault("")
            val perPage = if (sample > 0) text.length / sample else 0
            return Info(BookMeta(title, author), pages, perPage < SCANNED_CHARS_PER_PAGE)
        }
    }

    private fun reflow(file: File, info: Info): BookContent.Reflowable {
        PDDocument.load(file).use { doc ->
            val pageCount = doc.numberOfPages
            val chapters = ArrayList<Chapter>()
            val toc = ArrayList<TocEntry>()
            val pagesPerChapter = 25
            var chapterIndex = 0
            var page = 1
            while (page <= pageCount) {
                val end = minOf(page + pagesPerChapter - 1, pageCount)
                val stripper = PDFTextStripper().apply {
                    startPage = page
                    endPage = end
                    paragraphStart = " "
                }
                val raw = runCatching { stripper.getText(doc) }.getOrDefault("")
                val blocks = reflowText(raw)
                if (blocks.isNotEmpty()) {
                    toc += TocEntry("Pagini $page–$end", chapterIndex)
                    chapters += Chapter("pdf-$chapterIndex", "Pagini $page–$end", blocks)
                    chapterIndex++
                }
                page = end + 1
            }
            if (chapters.isEmpty()) {
                chapters += Chapter("pdf-0", info.meta.title, emptyList())
            }
            return BookContent.Reflowable(info.meta, toc, chapters)
        }
    }

    /**
     * Turns raw extracted PDF text into paragraphs. PDF text comes line-wrapped at the
     * original layout width, so we rejoin lines into paragraphs, undo hyphenation, and
     * split on blank lines / large gaps.
     */
    fun reflowText(raw: String): List<RichBlock> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw.replace("\r\n", "\n").replace(' ', '\n')
        val paragraphs = normalized.split(Regex("\n[ \t]*\n+"))
        val out = ArrayList<RichBlock>()
        for (p in paragraphs) {
            val lines = p.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val sb = StringBuilder()
            for ((i, line) in lines.withIndex()) {
                if (i > 0) {
                    val prev = sb.toString()
                    if (prev.endsWith("-") && prev.length > 1 && prev[prev.length - 2].isLetter()) {
                        sb.deleteCharAt(sb.length - 1) // de-hyphenate
                    } else if (prev.isNotEmpty() && !prev.endsWith(' ')) {
                        sb.append(' ')
                    }
                }
                sb.append(line)
            }
            val merged = sb.toString().trim()
            if (merged.isNotEmpty()) {
                val kind = if (merged.length < 60 && merged == merged.uppercase() &&
                    merged.any { it.isLetter() }
                ) BlockKind.HEADING else BlockKind.BODY
                out += RichBlock(kind, AnnotatedString(merged))
            }
        }
        return out
    }

    /** Renders scanned PDF pages on demand. PdfRenderer is single-threaded → guarded by a mutex. */
    private class PdfPageProvider(context: Context, file: File) : ImagePageProvider {
        private val pfd: ParcelFileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        private val renderer = PdfRenderer(pfd)
        private val mutex = Mutex()

        override val count: Int get() = renderer.pageCount

        override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
            if (index !in 0 until renderer.pageCount) return null
            return mutex.withLock {
                renderer.openPage(index).use { page ->
                    val scale = minOf(
                        maxWidth.toFloat() / page.width,
                        maxHeight.toFloat() / page.height,
                    ).coerceAtMost(3f).coerceAtLeast(0.1f)
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }

        override fun close() {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }
}
