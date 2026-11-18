package app.inkwell.reader.reader

/**
 * Pure layout maths for inline images — no Android/Compose types, so it is unit-testable on
 * the JVM. Scales an image to fit the content width and the page height while preserving the
 * aspect ratio. Returns (width, height) in pixels, or (0, 0) for degenerate input.
 */
fun fitImageSize(imgW: Int, imgH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
    if (imgW <= 0 || imgH <= 0 || maxW <= 0 || maxH <= 0) return 0 to 0
    val scale = minOf(maxW.toFloat() / imgW, maxH.toFloat() / imgH)
    val w = (imgW * scale).toInt().coerceIn(1, maxW)
    val h = (imgH * scale).toInt().coerceIn(1, maxH)
    return w to h
}
