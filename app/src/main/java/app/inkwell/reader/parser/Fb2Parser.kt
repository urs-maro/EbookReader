package app.inkwell.reader.parser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.inkwell.reader.core.BlockKind
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookMeta
import app.inkwell.reader.core.Chapter
import app.inkwell.reader.core.RichBlock
import app.inkwell.reader.core.TocEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File

/** FictionBook 2 (.fb2) parser. Each top-level <section> becomes a chapter. */
object Fb2Parser {

    fun parse(file: File): BookContent.Reflowable {
        val doc = Jsoup.parse(file.readText(), "", Parser.xmlParser())

        val title = doc.selectFirst("description book-title")?.text()?.trim()
            ?: file.nameWithoutExtension
        val author = doc.selectFirst("description author")?.let { a ->
            listOf(a.selectFirst("first-name")?.text(), a.selectFirst("last-name")?.text())
                .filterNotNull().joinToString(" ").ifBlank { null }
        }

        val body = doc.selectFirst("body")
        val chapters = ArrayList<Chapter>()
        body?.children()?.filter { it.normalName() == "section" }?.forEachIndexed { i, section ->
            chapters += parseSection(section, i)
        }
        if (chapters.isEmpty() && body != null) {
            chapters += Chapter("fb2-0", title, collectBlocks(body))
        }

        val toc = chapters.mapIndexedNotNull { i, c -> c.title?.let { TocEntry(it, i) } }
        return BookContent.Reflowable(BookMeta(title, author), toc, chapters)
    }

    private fun parseSection(section: Element, index: Int): Chapter {
        val titleEl = section.selectFirst("title")
        val title = titleEl?.text()?.trim()?.take(80)
        val blocks = collectBlocks(section)
        return Chapter("fb2-$index", title, blocks)
    }

    private fun collectBlocks(root: Element): List<RichBlock> {
        val out = ArrayList<RichBlock>()
        for (el in root.children()) {
            when (el.normalName()) {
                "title" -> el.getElementsByTag("p").forEach {
                    out += RichBlock(BlockKind.HEADING, inline(it))
                }
                "subtitle" -> out += RichBlock(BlockKind.SUBHEADING, inline(el))
                "p" -> out += RichBlock(BlockKind.BODY, inline(el))
                "empty-line" -> out += RichBlock(BlockKind.SEPARATOR)
                "cite", "epigraph" -> el.getElementsByTag("p").forEach {
                    out += RichBlock(BlockKind.QUOTE, inline(it))
                }
                "section" -> out += collectBlocks(el)
            }
        }
        return out.filter { it.kind == BlockKind.SEPARATOR || it.text.isNotBlank() }
    }

    private fun inline(el: Element): AnnotatedString = buildAnnotatedString {
        fun walk(node: Node, bold: Boolean, italic: Boolean) {
            when (node) {
                is TextNode -> append(node.text())
                is Element -> {
                    val b = bold || node.normalName() == "strong"
                    val i = italic || node.normalName() == "emphasis"
                    pushStyle(
                        SpanStyle(
                            fontWeight = if (b) FontWeight.Bold else null,
                            fontStyle = if (i) FontStyle.Italic else null,
                        )
                    )
                    node.childNodes().forEach { walk(it, b, i) }
                    pop()
                }
            }
        }
        el.childNodes().forEach { walk(it, false, false) }
    }
}
