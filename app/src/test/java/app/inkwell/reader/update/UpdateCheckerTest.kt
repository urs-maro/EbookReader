package app.inkwell.reader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure version-comparison and asset-selection logic. */
class UpdateCheckerTest {

    @Test
    fun newerVersionsAreDetected() {
        assertTrue(UpdateChecker.isNewer("0.1.0", "v0.2.0"))
        assertTrue(UpdateChecker.isNewer("0.1.0", "0.1.1"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "1.0.1"))
        assertTrue(UpdateChecker.isNewer("0.9.0", "1.0.0"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "v0.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun leadingVAndSuffixesAreIgnored() {
        assertEquals(listOf(1, 2, 3), UpdateChecker.parseVersion("v1.2.3"))
        assertEquals(listOf(0, 1, 0), UpdateChecker.parseVersion("0.1.0-beta"))
        assertTrue(UpdateChecker.isNewer("1.2.3", "v1.2.4"))
    }

    @Test
    fun picksReleaseApkOverOthers() {
        val assets = listOf(
            UpdateChecker.Asset("app-debug.apk", "https://x/app-debug.apk"),
            UpdateChecker.Asset("app-release.apk", "https://x/app-release.apk"),
            UpdateChecker.Asset("notes.txt", "https://x/notes.txt"),
        )
        assertEquals("app-release.apk", UpdateChecker.pickApk(assets)?.name)
    }

    @Test
    fun picksFirstApkWhenNoReleaseNamed() {
        val assets = listOf(
            UpdateChecker.Asset("readme.md", "https://x/readme.md"),
            UpdateChecker.Asset("crio.apk", "https://x/crio.apk"),
        )
        assertEquals("crio.apk", UpdateChecker.pickApk(assets)?.name)
    }

    @Test
    fun noApkYieldsNull() {
        val assets = listOf(UpdateChecker.Asset("notes.txt", "https://x/notes.txt"))
        assertNull(UpdateChecker.pickApk(assets))
    }
}
