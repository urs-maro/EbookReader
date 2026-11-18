package app.inkwell.reader.parser

import app.inkwell.reader.core.BookFormat
import java.io.File

/** Detects the ebook format from extension first, then file magic bytes as a fallback. */
object FormatDetector {

    fun fromName(name: String): BookFormat = when (name.substringAfterLast('.', "").lowercase()) {
        "epub" -> BookFormat.EPUB
        "pdf" -> BookFormat.PDF
        "txt", "text" -> BookFormat.TXT
        "fb2" -> BookFormat.FB2
        "cbz" -> BookFormat.CBZ
        "mobi", "prc" -> BookFormat.MOBI
        "azw", "azw3" -> BookFormat.AZW3
        else -> BookFormat.UNKNOWN
    }

    fun detect(file: File): BookFormat {
        val byName = fromName(file.name)
        if (byName != BookFormat.UNKNOWN) return byName
        return fromMagic(file)
    }

    private fun fromMagic(file: File): BookFormat {
        val head = runCatching { file.inputStream().use { it.readNBytes(8) } }.getOrNull()
            ?: return BookFormat.UNKNOWN
        return when {
            head.size >= 4 && head[0] == 0x25.toByte() && head[1] == 0x50.toByte() &&
                head[2] == 0x44.toByte() && head[3] == 0x46.toByte() -> BookFormat.PDF // %PDF
            head.size >= 2 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() ->
                BookFormat.EPUB // zip; assume EPUB (could be CBZ) — caller may refine
            head.size >= 5 && String(head.copyOf(5)) == "<?xml" -> BookFormat.FB2
            else -> BookFormat.TXT
        }
    }
}
