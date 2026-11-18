package app.inkwell.reader.parser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.inkwell.reader.core.BlockKind
import app.inkwell.reader.core.RichBlock
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts an (X)HTML body element into a flat list of [RichBlock]s suitable for
 * reflowable pagination. Keeps bold / italic inline styling and the structural kind
 * of each block (title, heading, body, quote, image), dropping everything else.
 */
object HtmlToRich {

    fun convert(body: Element): List<RichBlock> {
        val out = ArrayList<RichBlock>()
        walk(body, out, SpanState())
        return out.filter {
            it.kind == BlockKind.IMAGE || it.kind == BlockKind.SEPARATOR || it.text.isNotBlank()
        }
    }

    private data class SpanState(val bold: Boolean = false, val italic: Boolean = false)

    private fun walk(node: Element, out: MutableList<RichBlock>, state: SpanState) {
        for (child in node.childNodes()) {
            if (child !is Element) continue
            when (child.normalName()) {
                "h1" -> out += RichBlock(BlockKind.TITLE, inline(child, state))
                "h2", "h3" -> out += RichBlock(BlockKind.HEADING, inline(child, state))
                "h4", "h5", "h6" -> out += RichBlock(BlockKind.SUBHEADING, inline(child, state))
                "p", "li", "dd", "dt" -> out += RichBlock(BlockKind.BODY, inline(child, state))
                "blockquote" -> out += RichBlock(BlockKind.QUOTE, inline(child, state))
                "hr" -> out += RichBlock(BlockKind.SEPARATOR)
                "img" -> {
                    val src = child.attr("src").ifBlank { child.attr("xlink:href") }
                    if (src.isNotBlank()) out += RichBlock(BlockKind.IMAGE, imageRef = src)
                }
                "image" -> {
                    val src = child.attr("xlink:href").ifBlank { child.attr("href") }
                    if (src.isNotBlank()) out += RichBlock(BlockKind.IMAGE, imageRef = src)
                }
                "br" -> { /* handled inside inline() */ }
                "div", "section", "article", "body", "main", "ul", "ol", "dl",
                "table", "tbody", "tr", "td", "nav", "header", "footer", "figure" ->
                    walk(child, out, state) // descend into containers
                else -> {
                    // Unknown block: if it directly holds text, emit as body, else descend.
                    if (child.children().isEmpty() && child.text().isNotBlank()) {
                        out += RichBlock(BlockKind.BODY, inline(child, state))
                    } else {
                        walk(child, out, state)
                    }
                }
            }
        }
    }

    /** Builds an AnnotatedString for a block element, tracking bold/italic nesting. */
    private fun inline(element: Element, parent: SpanState): AnnotatedString = buildAnnotatedString {
        appendInline(element, parent)
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
        node: Node,
        state: SpanState,
    ) {
        when (node) {
            is TextNode -> append(node.text())
            is Element -> {
                val next = when (node.normalName()) {
                    "b", "strong" -> state.copy(bold = true)
                    "i", "em", "cite" -> state.copy(italic = true)
                    "br" -> { append("\n"); state }
                    else -> state
                }
                val style = SpanStyle(
                    fontWeight = if (next.bold) FontWeight.Bold else null,
                    fontStyle = if (next.italic) FontStyle.Italic else null,
                )
                pushStyle(style)
                for (c in node.childNodes()) appendInline(c, next)
                pop()
            }
        }
    }
}
