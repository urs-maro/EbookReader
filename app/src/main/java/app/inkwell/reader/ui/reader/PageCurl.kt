package app.inkwell.reader.ui.reader

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.inkwell.reader.reader.PaginatedBook
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Page viewer with an iBooks-style page-fold turn. Dragging left turns to the next page — the
 * current page lifts from the right edge and folds over itself, showing its softly-lit blank
 * back while the next page is revealed beneath; dragging right mirrors this to go back. Works
 * on page bitmaps, so it behaves identically for EPUB/TXT/FB2 text pages and scanned-PDF /
 * comic image pages.
 */
@Composable
fun PageCurl(
    book: PaginatedBook,
    index: Int,
    onIndexChange: (Int) -> Unit,
    background: Color,
    curlEnabled: Boolean,
    onCenterTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bitmaps = remember(book) { mutableStateMapOf<Int, ImageBitmap>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // drag offset in px: negative = turning to next page, positive = previous page.
    val drag = remember(book) { Animatable(0f) }
    // vertical fraction (0..1) where the page was grabbed → decides the fold corner.
    var anchorY by remember { mutableFloatStateOf(0.5f) }

    LaunchedEffect(book, index, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        for (i in (index - 1)..(index + 1)) {
            if (i in 0 until book.pageCount && bitmaps[i] == null) {
                book.bitmap(i)?.let { bitmaps[i] = it }
            }
        }
    }

    fun settle(targetIndex: Int, toOffset: Float) {
        scope.launch {
            drag.animateTo(toOffset, tween(340))
            if (targetIndex != index) onIndexChange(targetIndex)
            drag.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(book, index, size, curlEnabled) {
                detectTapGestures { pos ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    anchorY = (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                    when {
                        pos.x > w * 0.66f && index < book.pageCount - 1 -> settle(index + 1, -w)
                        pos.x < w * 0.34f && index > 0 -> settle(index - 1, w)
                        else -> onCenterTap()
                    }
                }
            }
            .pointerInput(book, index, size, curlEnabled) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        anchorY = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val d = drag.value
                        when {
                            d < -w * 0.28f && index < book.pageCount - 1 -> settle(index + 1, -w)
                            d > w * 0.28f && index > 0 -> settle(index - 1, w)
                            else -> settle(index, 0f)
                        }
                    },
                    onDragCancel = { settle(index, 0f) },
                ) { change, dragAmount ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val next = (drag.value + dragAmount)
                    val lo = if (index < book.pageCount - 1) -w else 0f
                    val hi = if (index > 0) w else 0f
                    scope.launch { drag.snapTo(next.coerceIn(lo, hi)) }
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            size = IntSize(this.size.width.toInt(), this.size.height.toInt())
            val w = this.size.width
            val current = bitmaps[index]
            if (current == null) {
                drawRect(background)
                return@Canvas
            }
            val d = drag.value
            when {
                d < 0f && index < book.pageCount - 1 -> {
                    val next = bitmaps[index + 1]
                    val p = (abs(d) / w).coerceIn(0f, 1f)
                    if (!curlEnabled || next == null) drawSlide(current, next, d, background)
                    else drawFold(current, next, p, background, fromRight = true)
                }
                d > 0f && index > 0 -> {
                    val prev = bitmaps[index - 1]
                    val p = (d / w).coerceIn(0f, 1f)
                    if (!curlEnabled || prev == null) drawSlide(prev, current, d - w, background)
                    else drawFold(current, prev, p, background, fromRight = false)
                }
                else -> drawImageFit(current)
            }
        }
    }
}

private fun DrawScope.drawImageFit(img: ImageBitmap) {
    drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero)
}

private fun DrawScope.drawSlide(front: ImageBitmap?, back: ImageBitmap?, dx: Float, bg: Color) {
    drawRect(bg)
    back?.let { translate(left = size.width + dx) { drawImageFit(it) } }
    front?.let { translate(left = dx) { drawImageFit(it) } }
}

/**
 * iBooks-style page fold. The [folded] page's edge lifts and folds over itself — showing its
 * blank, softly-lit back — while the [under] page is revealed beneath. [p] is turn progress
 * (0..1). When [fromRight] the fold runs in from the right edge (turning to the next page);
 * otherwise it mirrors and runs from the left (turning back to the previous page).
 *
 * The flap is drawn as blank paper with a horizontal brightness gradient (a darker lifted lip,
 * a broad soft sheen and a rounded, highlighted roll at the crease), plus a valley shadow in
 * the fold and a drop shadow the lifted flap casts on the page below — no bitmap mesh needed.
 */
private fun DrawScope.drawFold(
    folded: ImageBitmap,
    under: ImageBitmap,
    p: Float,
    bg: Color,
    fromRight: Boolean,
) {
    val w = size.width
    val h = size.height
    if (p <= 0.004f) { drawImageFit(folded); return }

    val foldedBmp = folded.asAndroidBitmap()
    val underBmp = under.asAndroidBitmap()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    val flapW = p * w
    // Fold line and lifted outer edge, in "fromRight" space; mirrored below when !fromRight.
    val foldX = if (fromRight) w - flapW else flapW
    val outerX = if (fromRight) foldX - flapW else foldX + flapW

    // Warm paper colour for the blank page back = the reading background.
    val pr = (bg.red * 255f).toInt().coerceIn(0, 255)
    val pg = (bg.green * 255f).toInt().coerceIn(0, 255)
    val pb = (bg.blue * 255f).toInt().coerceIn(0, 255)

    fun ratioX(x: Float) = (x / w).coerceIn(0f, 1f) * foldedBmp.width

    drawRect(bg)
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas

        // 1. the two flat pages: folded page on its side of the fold, under page on the other.
        if (fromRight) {
            if (foldX > 0f) nc.drawBitmap(
                foldedBmp,
                android.graphics.Rect(0, 0, ratioX(foldX).toInt(), foldedBmp.height),
                android.graphics.RectF(0f, 0f, foldX, h), paint,
            )
            nc.drawBitmap(
                underBmp,
                android.graphics.Rect(ratioX(foldX).toInt(), 0, underBmp.width, underBmp.height),
                android.graphics.RectF(foldX, 0f, w, h), paint,
            )
        } else {
            if (foldX < w) nc.drawBitmap(
                foldedBmp,
                android.graphics.Rect(ratioX(foldX).toInt(), 0, foldedBmp.width, foldedBmp.height),
                android.graphics.RectF(foldX, 0f, w, h), paint,
            )
            nc.drawBitmap(
                underBmp,
                android.graphics.Rect(0, 0, ratioX(foldX).toInt(), underBmp.height),
                android.graphics.RectF(0f, 0f, foldX, h), paint,
            )
        }

        // 2. valley shadow on the revealed page, right at the fold.
        val vW = minOf(70f, flapW)
        val vAlpha = (0.30f * minOf(1f, p * 3f) * 255f).toInt().coerceIn(0, 255)
        val valleyFar = if (fromRight) foldX + vW else foldX - vW
        Paint().apply {
            shader = LinearGradient(
                foldX, 0f, valleyFar, 0f,
                android.graphics.Color.argb(vAlpha, 0, 0, 0),
                android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
        }.also { nc.drawRect(minOf(foldX, valleyFar), 0f, maxOf(foldX, valleyFar), h, it) }

        // 3. drop shadow the lifted flap casts on the page beneath, just past its outer edge.
        val onScreenOuter = if (fromRight) outerX > 0f else outerX < w
        if (onScreenOuter) {
            val sw = (flapW * 0.55f).coerceIn(12f, 50f)
            val cAlpha = (0.42f * minOf(1f, p * 3f) * 255f).toInt().coerceIn(0, 255)
            val far = if (fromRight) outerX - sw else outerX + sw
            Paint().apply {
                shader = LinearGradient(
                    outerX, 0f, far, 0f,
                    android.graphics.Color.argb(cAlpha, 0, 0, 0),
                    android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
            }.also { nc.drawRect(minOf(outerX, far), 0f, maxOf(outerX, far), h, it) }
        }

        // 4. the folded flap: blank paper shaded as a lifted, slightly rolled page back.
        val stops = 16
        val positions = FloatArray(stops + 1)
        val flapColors = IntArray(stops + 1)
        for (i in 0..stops) {
            val pos = i.toFloat() / stops            // 0 at outer edge → 1 at the fold
            val dFold = 1f - pos                     // 0 at fold → 1 at outer edge
            var shade = 0.94f
            shade -= 0.14f * Math.pow(dFold.toDouble(), 6.0).toFloat()        // darker lip
            shade += 0.05f * Math.exp(-((dFold - 0.42) * (dFold - 0.42) / (0.32 * 0.32))).toFloat() // sheen
            val rd = (0.30f - dFold) / 0.30f         // 1 at fold → 0 at roll edge
            if (rd > 0f) {
                shade += 0.11f * sin(rd * Math.PI.toFloat())                  // roll highlight
                shade -= 0.18f * maxOf(0f, rd - 0.6f)                         // crease hollow
            }
            val s = shade.coerceIn(0.55f, 1.08f)
            flapColors[i] = if (s <= 1f) {
                (0xFF shl 24) or ((pr * s).toInt().coerceIn(0, 255) shl 16) or
                    ((pg * s).toInt().coerceIn(0, 255) shl 8) or (pb * s).toInt().coerceIn(0, 255)
            } else {
                // blend paper toward white for the bright sheen crest
                val t = ((s - 1f) * 6f).coerceIn(0f, 1f)
                fun up(c: Int) = (c + (255 - c) * t).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (up(pr) shl 16) or (up(pg) shl 8) or up(pb)
            }
            // gradient runs outer → fold in screen space
            positions[i] = pos
        }
        val gStart = outerX
        val gEnd = foldX
        val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(gStart, 0f, gEnd, 0f, flapColors, positions, Shader.TileMode.CLAMP)
        }
        val flapLeft = maxOf(0f, minOf(outerX, foldX))
        val flapRight = minOf(w, maxOf(outerX, foldX))
        nc.drawRect(flapLeft, 0f, flapRight, h, flapPaint)

        // 5. crisp crease highlight exactly on the fold.
        val crease = Paint().apply { color = android.graphics.Color.argb(72, 255, 255, 255) }
        nc.drawRect(foldX - 1f, 0f, foldX + 0.6f, h, crease)
    }
}

private fun DrawScope.drawImage(image: ImageBitmap, dstSize: IntSize, dstOffset: IntOffset) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = dstOffset,
        dstSize = dstSize,
    )
}
