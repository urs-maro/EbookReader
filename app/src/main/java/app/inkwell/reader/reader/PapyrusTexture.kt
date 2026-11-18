package app.inkwell.reader.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Supplies a papyrus/parchment page texture for the Papyrus theme. Uses the bundled
 * `assets/papyrus.png` (or `.jpg`) if present; otherwise generates a soft procedural parchment
 * so the theme still works.
 */
object PapyrusTexture {
    @Volatile private var cached: Bitmap? = null

    fun get(context: Context): Bitmap {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val fromAsset = sequenceOf("papyrus.png", "papyrus.jpg", "papyrus.jpeg")
                .mapNotNull { name ->
                    runCatching {
                        context.assets.open(name).use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                .firstOrNull()
            val bmp = fromAsset ?: generate()
            cached = bmp
            return bmp
        }
    }

    private fun generate(): Bitmap {
        val size = 512
        val px = IntArray(size * size)
        val rnd = Random(7)

        // Low-frequency mottling grid, bilinearly upsampled for soft blotches.
        val gN = 10
        val grid = FloatArray((gN + 1) * (gN + 1)) { rnd.nextFloat() * 2f - 1f }
        fun blotch(x: Int, y: Int): Float {
            val gx = x.toFloat() / size * gN
            val gy = y.toFloat() / size * gN
            val x0 = gx.toInt(); val y0 = gy.toInt()
            val fx = gx - x0; val fy = gy - y0
            fun g(ix: Int, iy: Int) = grid[iy.coerceIn(0, gN) * (gN + 1) + ix.coerceIn(0, gN)]
            val top = g(x0, y0) * (1 - fx) + g(x0 + 1, y0) * fx
            val bot = g(x0, y0 + 1) * (1 - fx) + g(x0 + 1, y0 + 1) * fx
            return top * (1 - fy) + bot * fy
        }

        val cx = size / 2f; val cy = size / 2f; val maxD = hypot(cx, cy)
        val baseR = 231; val baseG = 214; val baseB = 176
        for (y in 0 until size) {
            for (x in 0 until size) {
                val mott = blotch(x, y) * 16f           // soft tonal variation
                val fine = (rnd.nextFloat() - 0.5f) * 12f // fibrous grain
                // faint horizontal fibers
                val fiber = kotlin.math.sin(y * 0.7f) * 3f
                val d = hypot(x - cx, y - cy) / maxD
                val vignette = -d * d * 26f              // darker toward the edges
                val delta = mott + fine + fiber + vignette
                val r = (baseR + delta).toInt().coerceIn(0, 255)
                val g = (baseG + delta).toInt().coerceIn(0, 255)
                val b = (baseB + delta * 0.9f).toInt().coerceIn(0, 255)
                px[y * size + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}
