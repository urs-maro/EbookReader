package app.inkwell.reader.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Draws a laid-out [Page] into an [ImageBitmap] so the curl effect can warp it as pixels. */
object PageRenderer {
    fun render(
        page: Page,
        widthPx: Int,
        heightPx: Int,
        marginXPx: Float,
        marginYPx: Float,
        background: Color,
        measurer: TextMeasurer,
        density: Density,
        backgroundImage: ImageBitmap? = null,
    ): ImageBitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bmp = ImageBitmap(w, h)
        val canvas = Canvas(bmp)
        val contentWidth = (w - 2 * marginXPx).toInt().coerceAtLeast(1)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            if (backgroundImage != null) {
                drawRect(background)
                drawImage(
                    backgroundImage,
                    dstSize = androidx.compose.ui.unit.IntSize(w, h),
                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                )
            } else {
                drawRect(background)
            }
            var y = marginYPx
            for (item in page.items) {
                y += item.spacingBeforePx
                val layout = measurer.measure(
                    item.text,
                    item.style,
                    constraints = Constraints(maxWidth = contentWidth),
                )
                drawText(layout, topLeft = Offset(marginXPx, y))
                y += layout.size.height
            }
        }
        return bmp
    }
}
