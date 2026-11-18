package app.inkwell.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure inline-image layout maths in [fitImageSize]. */
class ImageLayoutTest {

    @Test
    fun wideImageIsLimitedByWidth() {
        // 2000x1000 into a 1000x1500 page → scale 0.5 → 1000x500.
        assertEquals(1000 to 500, fitImageSize(2000, 1000, 1000, 1500))
    }

    @Test
    fun tallImageIsLimitedByHeight() {
        // 1000x3000 into a 1000x1500 page → scale 0.5 → 500x1500.
        assertEquals(500 to 1500, fitImageSize(1000, 3000, 1000, 1500))
    }

    @Test
    fun smallImageScalesUpToFitWidth() {
        // 100x100 into 1000x1500 → scale 10 → 1000x1000.
        assertEquals(1000 to 1000, fitImageSize(100, 100, 1000, 1500))
    }

    @Test
    fun neverExceedsBounds() {
        val (w, h) = fitImageSize(1234, 5678, 800, 1200)
        assert(w in 1..800) { "width $w out of bounds" }
        assert(h in 1..1200) { "height $h out of bounds" }
    }

    @Test
    fun degenerateInputYieldsZero() {
        assertEquals(0 to 0, fitImageSize(0, 100, 1000, 1500))
        assertEquals(0 to 0, fitImageSize(100, 100, 0, 1500))
    }
}
