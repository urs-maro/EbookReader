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
 * Page viewer with an iBooks-style page-curl turn. The fold follows the corner you grab:
 * drag from the top → the page peels from the top-right corner, from the middle → a straight
 * vertical fold, from the bottom → a bottom-corner peel. Works on page bitmaps, so it behaves
 * identically for EPUB/TXT/FB2 text pages and scanned-PDF / comic image pages.
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
                    val back = bitmaps[index + 1]
                    val p = (abs(d) / w).coerceIn(0f, 1f)
                    if (!curlEnabled || back == null) drawSlide(current, back, d, background)
                    else drawCurl(current, back, p, anchorY, background)
                }
                d > 0f && index > 0 -> {
                    val prev = bitmaps[index - 1]
                    val p = 1f - (d / w).coerceIn(0f, 1f)
                    if (!curlEnabled || prev == null) drawSlide(prev, current, d - w, background)
                    else drawCurl(prev, current, p, anchorY, background)
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
 * Natural cylindrical page curl. [front] wraps around an imaginary cylinder and peels away
 * revealing [back]; [p] is turn progress (0..1). [anchorY] (0..1) tilts the curl axis so the
 * page lifts from the grabbed corner (top / middle / bottom). Uses a warped bitmap mesh so the
 * page genuinely bends, with shading on the curved surface and a soft shadow underneath.
 */
private fun DrawScope.drawCurl(
    front: ImageBitmap,
    back: ImageBitmap,
    p: Float,
    anchorY: Float,
    bg: Color,
) {
    val w = size.width
    val h = size.height
    if (p <= 0.004f) { drawImageFit(front); return }

    val frontBmp = front.asAndroidBitmap()
    val backBmp = back.asAndroidBitmap()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    // How much of the page (from the right edge) is being curled, and the cylinder radius.
    val curlWidth = p * w
    val xFoldBase = w - curlWidth
    val radius = (curlWidth / Math.PI.toFloat()).coerceAtLeast(1f)
    // Diagonal tilt of the curl axis based on where the page was grabbed.
    val tilt = (0.5f - anchorY.coerceIn(0f, 1f)) * 0.5f
    val ay = anchorY.coerceIn(0f, 1f) * h

    val cols = 48
    val rows = 20
    val verts = FloatArray((cols + 1) * (rows + 1) * 2)
    val colors = IntArray((cols + 1) * (rows + 1))        // shading for the front face
    val backColors = IntArray((cols + 1) * (rows + 1))    // opaque paper over the page back
    val halfPi = Math.PI.toFloat() / 2f

    // Page-back ("paper") colour = the reading background, so the underside looks blank.
    val bgR = (bg.red * 255f).toInt().coerceIn(0, 255)
    val bgG = (bg.green * 255f).toInt().coerceIn(0, 255)
    val bgB = (bg.blue * 255f).toInt().coerceIn(0, 255)

    for (r in 0..rows) {
        val oy = h * r / rows
        for (c in 0..cols) {
            val idx = r * (cols + 1) + c
            val ox = w * c / cols
            val xFold = (xFoldBase + tilt * (oy - ay)).coerceIn(0f, w)
            if (ox <= xFold || radius <= 1f) {
                verts[idx * 2] = ox
                verts[idx * 2 + 1] = oy
                colors[idx] = -0x1 // unchanged
                backColors[idx] = 0 // transparent: no paper on the flat part
            } else {
                val arc = ox - xFold
                val theta = (arc / radius).coerceIn(0f, Math.PI.toFloat())
                val wx = xFold + radius * sin(theta)
                val lift = -sin(theta) * radius * 0.06f
                verts[idx * 2] = wx
                verts[idx * 2 + 1] = oy + lift
                val front = theta <= halfPi
                if (front) {
                    val shade = 0.98f - 0.08f * sin(theta)
                    val v = (shade * 255f).toInt().coerceIn(0, 255)
                    colors[idx] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                    backColors[idx] = 0 // front face keeps the real page content
                } else {
                    colors[idx] = -0x1
                    // underside → opaque paper, shaded darker toward the trailing edge
                    val t = (theta - halfPi) / halfPi
                    val shade = 0.92f - 0.30f * t
                    val pr = (bgR * shade).toInt().coerceIn(0, 255)
                    val pg = (bgG * shade).toInt().coerceIn(0, 255)
                    val pb = (bgB * shade).toInt().coerceIn(0, 255)
                    backColors[idx] = (0xFF shl 24) or (pr shl 16) or (pg shl 8) or pb
                }
            }
        }
    }
    val paperBmp = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        .apply { eraseColor((0xFF shl 24) or (bgR shl 16) or (bgG shl 8) or bgB) }

    drawRect(bg)
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        // 1. revealed next page underneath
        nc.drawBitmap(backBmp, null, android.graphics.RectF(0f, 0f, w, h), paint)
        // 2. soft shadow the lifted page casts on the one beneath, just ahead of the fold
        val shW = (radius * 0.9f).coerceIn(10f, 90f)
        val shadow = Paint().apply {
            shader = LinearGradient(
                xFoldBase - shW, 0f, xFoldBase + shW * 0.3f, 0f,
                intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb((70 * p).toInt().coerceIn(0, 70), 0, 0, 0),
                    android.graphics.Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        nc.drawRect(0f, 0f, w, h, shadow)
        // 3. the curled front page, warped around the cylinder
        nc.drawBitmapMesh(frontBmp, cols, rows, verts, 0, colors, 0, paint)
        // 4. opaque paper over the underside, so the page back looks blank (not mirrored text)
        nc.drawBitmapMesh(paperBmp, cols, rows, verts, 0, backColors, 0, paint)
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
