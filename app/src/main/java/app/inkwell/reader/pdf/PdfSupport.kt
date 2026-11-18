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
                    paragraphStart = " "
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
     * Turns raw extracted PDF text into styled paragraphs ready for the reflowable paginator.
     * The heavy lifting lives in [reflowParagraphs], which is kept free of Compose types so it
     * can be unit-tested on the JVM; this is a thin adapter that wraps the result in text spans.
     */
    fun reflowText(raw: String): List<RichBlock> =
        reflowParagraphs(raw).map { RichBlock(it.kind, AnnotatedString(it.text)) }

    /** A reflowed paragraph before styling — plain data so it is testable without Compose. */
    data class ReflowBlock(val kind: BlockKind, val text: String)

    /**
     * Rejoins PDF text — which comes line-wrapped at the original layout width — into
     * paragraphs: it undoes end-of-line hyphenation, drops page-number / folio noise, and
     * classifies chapter headings (Romanian + Greek markers, or all-caps lines).
     */
    fun reflowParagraphs(raw: String): List<ReflowBlock> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw.replace("\r\n", "\n").replace(' ', '\n')
        val paragraphs = normalized.split(Regex("\n[ \t]*\n+"))
        val out = ArrayList<ReflowBlock>()
        for (p in paragraphs) {
            val merged = mergeWrappedLines(p) ?: continue
            val kind = classify(merged) ?: continue // null → dropped as page noise
            out += ReflowBlock(kind, merged)
        }
        return out
    }

    /** Rejoins the wrapped lines of a single paragraph, undoing end-of-line hyphenation. */
    private fun mergeWrappedLines(paragraph: String): String? {
        val lines = paragraph.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val sb = StringBuilder()
        for ((i, line) in lines.withIndex()) {
            if (i > 0) {
                val prev = sb.toString()
                if (prev.endsWith("-") && prev.length > 1 && prev[prev.length - 2].isLetter()) {
                    sb.deleteCharAt(sb.length - 1) // de-hyphenate across the line break
                } else if (prev.isNotEmpty() && !prev.endsWith(' ')) {
                    sb.append(' ')
                }
            }
            sb.append(line)
        }
        return sb.toString().trim().ifEmpty { null }
    }

    /** Only digits, roman numerals and punctuation — a running page number / folio. */
    private val pageNoise = Regex("^[\\p{Nd}ivxlcdmIVXLCDM.,:;·•\\-–—()\\[\\]\\s]+$")
    private val chapterMarker = Regex(
        "^(CAP(ITOLUL|\\.)?|PARTEA|CARTEA|VOLUMUL|SEC[ȚT]IUNEA|PROLOG|EPILOG|" +
            "PREFA[ȚT][AĂ]|INTRODUCERE|ΚΕΦΑΛΑΙΟ|ΜΕΡΟΣ|ΠΡΟΛΟΓΟΣ|ΕΠΙΛΟΓΟΣ)\\b.*",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Classifies a merged paragraph, or returns null when it is page-number / folio noise that
     * should be dropped from the flow. Conservative on purpose: only clear chapter markers and
     * fully upper-case short lines become headings, so body text is never misclassified.
     */
    fun classify(text: String): BlockKind? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.length <= 12 && (t.none { it.isLetter() } || pageNoise.matches(t))) return null
        val isShort = t.length < 60
        val letters = t.filter { it.isLetter() }
        val allCaps = letters.isNotEmpty() && letters.all { it.isUpperCase() }
        return when {
            isShort && chapterMarker.matches(t) -> BlockKind.HEADING
            isShort && allCaps -> BlockKind.HEADING
            else -> BlockKind.BODY
        }
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
