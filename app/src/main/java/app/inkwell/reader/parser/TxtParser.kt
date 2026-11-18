package app.inkwell.reader.parser

import androidx.compose.ui.text.AnnotatedString
import app.inkwell.reader.core.BlockKind
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookMeta
import app.inkwell.reader.core.Chapter
import app.inkwell.reader.core.RichBlock
import app.inkwell.reader.core.TocEntry
import java.io.File

/** Plain-text reader. Groups paragraphs into pseudo-chapters so navigation stays snappy. */
object TxtParser {
    private const val PARAGRAPHS_PER_CHAPTER = 120

    fun parse(file: File): BookContent.Reflowable {
        val text = file.readText(detectCharset(file))
        val paragraphs = text
            .replace("\r\n", "\n")
            .split(Regex("\n[ \t]*\n+"))
            .map { it.trim().replace(Regex("\\s*\n\\s*"), " ") }
            .filter { it.isNotBlank() }

        val chapters = ArrayList<Chapter>()
        var index = 0
        paragraphs.chunked(PARAGRAPHS_PER_CHAPTER).forEach { chunk ->
            val blocks = chunk.map { RichBlock(BlockKind.BODY, AnnotatedString(it)) }
            chapters += Chapter("txt-$index", if (index == 0) file.nameWithoutExtension else null, blocks)
            index++
        }
        if (chapters.isEmpty()) {
            chapters += Chapter("txt-0", file.nameWithoutExtension, emptyList())
        }
        val toc = chapters.mapIndexed { i, _ -> TocEntry("Secțiunea ${i + 1}", i) }
        return BookContent.Reflowable(
            meta = BookMeta(file.nameWithoutExtension, null),
            toc = toc,
            chapters = chapters,
        )
    }

    private fun detectCharset(file: File): java.nio.charset.Charset {
        val head = file.inputStream().use { it.readNBytes(3) }
        return when {
            head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() -> Charsets.UTF_8
            head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            head.size >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
    }
}
