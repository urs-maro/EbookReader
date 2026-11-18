package app.inkwell.reader.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import app.inkwell.reader.core.BlockKind
import app.inkwell.reader.core.Chapter
import app.inkwell.reader.core.RichBlock

/** A single text run placed on a page (may be a whole paragraph or a fragment of one). */
data class PageItem(
    val text: AnnotatedString,
    val style: TextStyle,
    val spacingBeforePx: Float,
)

/** One laid-out page: the items to stack, plus the block index it starts at (for position save). */
data class Page(
    val items: List<PageItem>,
    val startBlockIndex: Int,
)

/**
 * Lays reflowable chapter content out into fixed-size pages by measuring with [measurer] and
 * splitting paragraphs at line boundaries so text flows exactly like a paper book.
 */
class Paginator(
    private val measurer: TextMeasurer,
    private val widthPx: Int,
    private val heightPx: Int,
    private val styleFor: (BlockKind) -> TextStyle,
    private val paragraphSpacingPx: Float,
    private val headingSpacingPx: Float,
) {
    fun paginate(chapter: Chapter): List<Page> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()
        val pages = ArrayList<Page>()
        var current = ArrayList<PageItem>()
        var used = 0f
        var pageStartBlock = 0

        fun flush() {
            if (current.isNotEmpty()) {
                pages += Page(current, pageStartBlock)
                current = ArrayList()
                used = 0f
            }
        }

        chapter.blocks.forEachIndexed { blockIndex, block ->
            if (block.kind == BlockKind.IMAGE) return@forEachIndexed // images skipped in v1 flow
            if (current.isEmpty()) pageStartBlock = blockIndex

            if (block.kind == BlockKind.SEPARATOR) {
                val h = paragraphSpacingPx * 1.5f
                if (used + h > heightPx && current.isNotEmpty()) flush()
                used += h
                return@forEachIndexed
            }

            val style = styleFor(block.kind)
            val spacingBefore = when (block.kind) {
                BlockKind.TITLE, BlockKind.HEADING, BlockKind.SUBHEADING -> headingSpacingPx
                else -> paragraphSpacingPx
            }
            var remaining: AnnotatedString = block.text
            var firstFragment = true

            while (remaining.isNotEmpty()) {
                val spacing = if (firstFragment) spacingBefore else 0f
                val avail = heightPx - used - spacing
                val oneLine = measurer.measure(
                    AnnotatedString("Mg"), style,
                    constraints = Constraints(maxWidth = widthPx),
                ).size.height.toFloat()

                if (avail < oneLine) {
                    if (current.isNotEmpty()) { flush(); continue }
                }

                val layout = measurer.measure(
                    remaining, style,
                    constraints = Constraints(maxWidth = widthPx),
                )
                val availNow = heightPx - used - spacing
                var linesFit = 0
                for (line in 0 until layout.lineCount) {
                    if (layout.getLineBottom(line) <= availNow) linesFit = line + 1 else break
                }

                if (linesFit >= layout.lineCount) {
                    current.add(PageItem(remaining, style, spacing))
                    used += spacing + layout.size.height
                    remaining = AnnotatedString("")
                } else if (linesFit == 0) {
                    if (current.isNotEmpty()) flush() else {
                        // Page too short for even one line — emit it anyway to avoid an infinite loop.
                        current.add(PageItem(remaining, style, spacing))
                        flush()
                        remaining = AnnotatedString("")
                    }
                } else {
                    val cut = layout.getLineEnd(linesFit - 1, visibleEnd = true)
                    val head = remaining.subSequence(0, cut)
                    current.add(PageItem(head, style, spacing))
                    pages += Page(current, pageStartBlock)
                    current = ArrayList()
                    used = 0f
                    pageStartBlock = blockIndex
                    var rest = remaining.subSequence(cut, remaining.length)
                    // trim a single leading space left by the line break
                    if (rest.isNotEmpty() && rest[0] == ' ') rest = rest.subSequence(1, rest.length)
                    remaining = rest
                }
                firstFragment = false
            }
        }
        flush()
        if (pages.isEmpty()) pages += Page(emptyList(), 0)
        return pages
    }
}
