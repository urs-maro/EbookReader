package app.inkwell.reader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure version logic (no network). */
class UpdateCheckerTest {

    @Test
    fun newerVersionsAreDetected() {
        assertTrue(UpdateChecker.isNewer("0.1.0", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("0.1.0", "0.1.1"))
        assertTrue(UpdateChecker.isNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("0.3.0", "0.3.0"))
        assertFalse(UpdateChecker.isNewer("0.3.0", "0.2.9"))
    }

    @Test
    fun parsesVersionIgnoringPrefixAndSuffix() {
        assertEquals(listOf(1, 2, 3), UpdateChecker.parseVersion("v1.2.3"))
        assertEquals(listOf(0, 3, 0), UpdateChecker.parseVersion("0.3.0-beta"))
    }

    @Test
    fun readsVersionFromReleaseName() {
        val json = """{"tag_name":"latest","name":"CRIO-Books-Reader v0.4.0","body":"x"}"""
        assertEquals("0.4.0", UpdateChecker.versionFromRelease(json))
    }

    @Test
    fun readsTwoPartVersionFromReleaseName() {
        val json = """{"name":"CRIO-Books-Reader v1.10","assets":[]}"""
        assertEquals("1.10", UpdateChecker.versionFromRelease(json))
    }

    @Test
    fun missingVersionInNameYieldsNull() {
        assertNull(UpdateChecker.versionFromRelease("""{"name":"CRIO-Books-Reader"}"""))
    }
}
