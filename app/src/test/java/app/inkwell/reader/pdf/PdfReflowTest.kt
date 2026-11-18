package app.inkwell.reader.pdf

import app.inkwell.reader.core.BlockKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the PDF reflow logic. These cover [PdfSupport.reflowParagraphs] and
 * [PdfSupport.classify], which are deliberately free of Android/Compose types so they can
 * run on a plain JVM (`./gradlew testDebugUnitTest`).
 *
 * The raw inputs mimic PDFBox output with `paragraphStart = " "`: words are separated by
 * spaces, wrapped lines by `\n`, and the first line of each paragraph carries a leading space.
 */
class PdfReflowTest {

    @Test
    fun dehyphenatesAcrossLineBreaks() {
        val raw = " Acesta este un para-\ngraf de test."
        val blocks = PdfSupport.reflowParagraphs(raw)
        assertEquals(1, blocks.size)
        assertEquals(BlockKind.BODY, blocks[0].kind)
        assertEquals("Acesta este un paragraf de test.", blocks[0].text)
    }

    @Test
    fun joinsWrappedLinesWithSpaces() {
        val raw = " Prima linie\na doua linie\na treia linie."
        val blocks = PdfSupport.reflowParagraphs(raw)
        assertEquals(1, blocks.size)
        assertEquals("Prima linie a doua linie a treia linie.", blocks[0].text)
    }

    @Test
    fun dropsStandalonePageNumbers() {
        val raw = " Un paragraf oarecare de text.\n 42\n Alt paragraf care continuă."
        val blocks = PdfSupport.reflowParagraphs(raw)
        assertEquals(2, blocks.size)
        assertTrue(blocks.none { it.text.trim() == "42" })
        assertEquals("Un paragraf oarecare de text.", blocks[0].text)
        assertEquals("Alt paragraf care continuă.", blocks[1].text)
    }

    @Test
    fun detectsRomanianChapterHeading() {
        val raw = " CAPITOLUL I\n Despre credință și rațiune în text."
        val blocks = PdfSupport.reflowParagraphs(raw)
        assertEquals(2, blocks.size)
        assertEquals(BlockKind.HEADING, blocks[0].kind)
        assertEquals("CAPITOLUL I", blocks[0].text)
        assertEquals(BlockKind.BODY, blocks[1].kind)
    }

    @Test
    fun classifiesAllCapsShortLineAsHeading() {
        assertEquals(BlockKind.HEADING, PdfSupport.classify("DOGMATICA ORTODOXĂ"))
    }

    @Test
    fun classifiesGreekChapterMarkerAsHeading() {
        assertEquals(BlockKind.HEADING, PdfSupport.classify("ΚΕΦΑΛΑΙΟ Α"))
    }

    @Test
    fun classifiesNormalSentenceAsBody() {
        assertEquals(
            BlockKind.BODY,
            PdfSupport.classify("Aceasta este o propoziție normală, de lungime obișnuită, în corpul textului."),
        )
    }

    @Test
    fun dropsPageNoiseAndFolios() {
        assertNull(PdfSupport.classify("42"))
        assertNull(PdfSupport.classify("— 12 —"))
        assertNull(PdfSupport.classify("IX"))
    }

    @Test
    fun blankInputYieldsNoBlocks() {
        assertTrue(PdfSupport.reflowParagraphs("   \n  \n").isEmpty())
    }
}
