package app.inkwell.reader.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkwell.reader.core.BlockKind
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.data.ReadingSettings
import app.inkwell.reader.ui.theme.toFontFamily

/** Builds a [PaginatedBook] from parsed [BookContent] for the current size + reading settings. */
object BookPaginationBuilder {

    suspend fun build(
        content: BookContent,
        settings: ReadingSettings,
        size: IntSize,
        measurer: TextMeasurer,
        density: Density,
        pageTexture: androidx.compose.ui.graphics.ImageBitmap? = null,
    ): PaginatedBook {
        val bg = settings.theme.bg
        return when (content) {
            is BookContent.Paged -> PagedImageBook(content.imageProvider, size.width, size.height)
            is BookContent.Reflowable ->
                buildReflow(content, settings, size, measurer, density, bg, pageTexture)
        }
    }

    private suspend fun buildReflow(
        content: BookContent.Reflowable,
        settings: ReadingSettings,
        size: IntSize,
        measurer: TextMeasurer,
        density: Density,
        bg: androidx.compose.ui.graphics.Color,
        pageTexture: androidx.compose.ui.graphics.ImageBitmap?,
    ): PaginatedBook {
        val marginX = with(density) { (22.dp.toPx()) } * settings.marginScale
        val marginY = with(density) { (30.dp.toPx()) } * settings.marginScale
        val contentW = (size.width - 2 * marginX).toInt().coerceAtLeast(1)
        val contentH = (size.height - 2 * marginY).toInt().coerceAtLeast(1)
        val fontPx = with(density) { settings.fontSizeSp.sp.toPx() }

        val family = settings.font.toFontFamily()
        val fg = settings.theme.fg
        val faint = settings.theme.faint

        fun style(kind: BlockKind): TextStyle {
            val mult = when (kind) {
                BlockKind.TITLE -> 1.6f
                BlockKind.HEADING -> 1.32f
                BlockKind.SUBHEADING -> 1.14f
                else -> 1f
            }
            val sizeSp = (settings.fontSizeSp * mult).sp
            return TextStyle(
                fontFamily = family,
                fontSize = sizeSp,
                color = if (kind == BlockKind.QUOTE) faint else fg,
                fontWeight = when (kind) {
                    BlockKind.TITLE, BlockKind.HEADING, BlockKind.SUBHEADING -> FontWeight.Bold
                    else -> FontWeight.Normal
                },
                fontStyle = if (kind == BlockKind.QUOTE) FontStyle.Italic else FontStyle.Normal,
                lineHeight = sizeSp * settings.lineHeight,
                textAlign = when (kind) {
                    BlockKind.TITLE, BlockKind.HEADING, BlockKind.SUBHEADING -> TextAlign.Start
                    else -> TextAlign.Justify
                },
            )
        }

        val images = decodeImages(content, contentW, contentH)

        val paginator = Paginator(
            measurer = measurer,
            widthPx = contentW,
            heightPx = contentH,
            styleFor = ::style,
            paragraphSpacingPx = fontPx * 0.55f,
            headingSpacingPx = fontPx * 1.1f,
            images = images,
        )

        val flat = ArrayList<Pair<Int, Page>>()
        content.chapters.forEachIndexed { ci, chapter ->
            val pages = paginator.paginate(chapter)
            pages.forEach { flat += ci to it }
        }
        val titles = content.chapters.map { it.title }
        return ReflowBook(
            pages = flat,
            chapterTitles = titles,
            widthPx = size.width,
            heightPx = size.height,
            marginXPx = marginX,
            marginYPx = marginY,
            background = bg,
            measurer = measurer,
            density = density,
            backgroundImage = pageTexture,
        )
    }

    /** Resolves and decodes every distinct inline image referenced by the chapters. */
    private suspend fun decodeImages(
        content: BookContent.Reflowable,
        maxW: Int,
        maxH: Int,
    ): Map<String, androidx.compose.ui.graphics.ImageBitmap> {
        val resolver = content.imageResolver ?: return emptyMap()
        val refs = content.chapters
            .asSequence()
            .flatMap { it.blocks.asSequence() }
            .filter { it.kind == BlockKind.IMAGE }
            .mapNotNull { it.imageRef }
            .distinct()
            .toList()
        if (refs.isEmpty()) return emptyMap()
        val out = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>(refs.size)
        for (ref in refs) {
            val bytes = runCatching { resolver(ref) }.getOrNull() ?: continue
            val img = ImageDecoder.decode(bytes, maxW, maxH) ?: continue
            out[ref] = img
        }
        return out
    }
}
