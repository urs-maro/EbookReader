package app.inkwell.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import app.inkwell.reader.core.BookFormat
import app.inkwell.reader.parser.CbzParser
import app.inkwell.reader.parser.EpubParser
import app.inkwell.reader.parser.Fb2Parser
import app.inkwell.reader.parser.FormatDetector
import app.inkwell.reader.pdf.PdfSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Imports a file (picked by the user) into the library: copies it, extracts metadata + cover. */
class BookImporter(
    private val context: Context,
    private val library: LibraryRepository,
) {
    suspend fun import(uri: Uri): LibraryBook = withContext(Dispatchers.IO) {
        val displayName = queryName(uri) ?: "book_${System.currentTimeMillis()}"
        val id = UUID.randomUUID().toString()
        val ext = displayName.substringAfterLast('.', "").lowercase().ifBlank { "dat" }
        val target = File(library.booksDir, "$id.$ext")

        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("Nu pot citi fișierul selectat")

        var format = FormatDetector.fromName(displayName)
        if (format == BookFormat.UNKNOWN) format = FormatDetector.detect(target)
        // refine zip-based ambiguity
        if (format == BookFormat.EPUB && ext == "cbz") format = BookFormat.CBZ

        val (title, author) = extractMeta(target, format, displayName)
        val coverName = extractCover(target, format, id, title, author)

        val book = LibraryBook(
            id = id,
            title = title,
            author = author,
            format = format.name,
            fileName = target.name,
            coverFileName = coverName,
            addedAt = System.currentTimeMillis(),
            lastOpenedAt = 0L,
        )
        library.upsert(book)
        book
    }

    /** Tries to extract a cover for a book that was imported without one. */
    suspend fun ensureCover(book: app.inkwell.reader.data.LibraryBook) = withContext(Dispatchers.IO) {
        if (book.coverFileName != null) return@withContext
        val file = File(library.booksDir, book.fileName)
        if (!file.exists()) return@withContext
        val format = runCatching { BookFormat.valueOf(book.format) }.getOrDefault(BookFormat.UNKNOWN)
        val name = extractCover(file, format, book.id, book.title, book.author) ?: return@withContext
        library.upsert(book.copy(coverFileName = name))
    }

    private fun extractMeta(file: File, format: BookFormat, fallback: String): Pair<String, String?> {
        val fallbackTitle = fallback.substringBeforeLast('.').ifBlank { fallback }
        return runCatching {
            when (format) {
                BookFormat.EPUB -> EpubParser.parse(file).meta.let { it.title to it.author }
                BookFormat.FB2 -> Fb2Parser.parse(file).meta.let { it.title to it.author }
                else -> fallbackTitle to null
            }
        }.getOrDefault(fallbackTitle to null)
    }

    private fun extractCover(
        file: File,
        format: BookFormat,
        id: String,
        title: String,
        author: String?,
    ): String? {
        val bytes = when (format) {
            BookFormat.EPUB -> EpubParser.extractCover(file)?.bytes
            BookFormat.CBZ -> CbzParser.firstImageBytes(file)
            BookFormat.PDF -> renderPdfThumb(file)
            else -> null
        }
        val bmp = bytes?.let { CbzParser.decodeScaled(it, 512, 800) }
            ?: return generateCover(title, author, id) // no embedded cover → make a nice one
        return saveCover(bmp, id)
    }

    private fun saveCover(bmp: Bitmap, id: String): String? {
        val name = "$id.jpg"
        return runCatching {
            File(library.coversDir, name).outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            name
        }.getOrNull()
    }

    /** Draws a tasteful title/author cover for books that carry no embedded cover image. */
    private fun generateCover(title: String, author: String?, id: String): String? {
        val w = 512; val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)

        val palette = intArrayOf(
            0xFF2E4057.toInt(), 0xFF3D405B.toInt(), 0xFF5B3A29.toInt(), 0xFF2A4747.toInt(),
            0xFF4A2C40.toInt(), 0xFF1F3A5F.toInt(), 0xFF473826.toInt(), 0xFF334139.toInt(),
        )
        val base = palette[Math.floorMod(title.hashCode(), palette.size)]
        fun darken(c: Int, f: Float): Int {
            val r = (android.graphics.Color.red(c) * f).toInt().coerceIn(0, 255)
            val g = (android.graphics.Color.green(c) * f).toInt().coerceIn(0, 255)
            val b = (android.graphics.Color.blue(c) * f).toInt().coerceIn(0, 255)
            return android.graphics.Color.rgb(r, g, b)
        }
        canvas.drawRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, h.toFloat(), base, darken(base, 0.6f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            },
        )
        val accent = 0xFFC9A24B.toInt()
        canvas.drawRect(44f, 150f, 120f, 158f, android.graphics.Paint().apply { color = accent })

        val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF3ECDC.toInt()
            textSize = 46f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
        val titleLayout = android.text.StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, w - 88)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(6).setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save(); canvas.translate(44f, 200f); titleLayout.draw(canvas); canvas.restore()

        if (!author.isNullOrBlank()) {
            val authorPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFC9A24B.toInt()
                textSize = 30f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            }
            val authorLayout = android.text.StaticLayout.Builder
                .obtain(author, 0, author.length, authorPaint, w - 88)
                .setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            canvas.save(); canvas.translate(44f, (h - 150).toFloat()); authorLayout.draw(canvas); canvas.restore()
        }
        return saveCover(bmp, id)
    }

    private fun renderPdfThumb(file: File): ByteArray? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = 512f / page.width
                    val w = 512
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    baos.toByteArray()
                }
            }
        }
    }.getOrNull()

    private fun queryName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }
}
