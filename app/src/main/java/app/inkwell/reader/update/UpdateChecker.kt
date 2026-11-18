package app.inkwell.reader.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the project's auto-published GitHub release for a newer build. CI always publishes to
 * the fixed `latest` tag and puts the app version in the release *name* ("CRIO-Books-Reader
 * v0.3.0"), so we read the version from there. Pure helpers are unit-tested on the JVM; only
 * [fetchReleaseJson] touches the network.
 */
object UpdateChecker {

    /** The page the About screen opens so the user can download the new APK. */
    const val RELEASES_PAGE = "https://github.com/urs-maro/EbookReader/releases/latest"
    private const val RELEASE_API =
        "https://api.github.com/repos/urs-maro/EbookReader/releases/tags/latest"

    /** Outcome of a check so the UI can tell "up to date" apart from "couldn't check". */
    sealed interface Result {
        data class NewVersion(val version: String) : Result
        data object UpToDate : Result
        data object Unavailable : Result
    }

    suspend fun check(current: String): Result = withContext(Dispatchers.IO) {
        val json = fetchReleaseJson() ?: return@withContext Result.Unavailable
        val latest = versionFromRelease(json) ?: return@withContext Result.Unavailable
        if (isNewer(current, latest)) Result.NewVersion(latest) else Result.UpToDate
    }

    /** Fetches the `latest` release JSON, or null on any network error. */
    private fun fetchReleaseJson(): String? = runCatching {
        val c = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CRIO-Books-Reader")
        }
        try {
            if (c.responseCode !in 200..299) return@runCatching null
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Reads the version out of the release name, e.g. "CRIO-Books-Reader v0.3.0" -> "0.3.0". */
    fun versionFromRelease(json: String): String? =
        Regex("\"name\"\\s*:\\s*\"[^\"]*?v(\\d+(?:\\.\\d+)*)\"").find(json)?.groupValues?.get(1)

    /** True when [latest] is a strictly higher version than [current] (e.g. "0.10" > "0.9"). */
    fun isNewer(current: String, latest: String): Boolean {
        val c = parseVersion(current)
        val l = parseVersion(latest)
        for (i in 0 until maxOf(c.size, l.size)) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    /** Parses a version string into numeric components, ignoring a leading 'v' and any suffix. */
    fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+', '_')
            .mapNotNull { seg -> seg.takeWhile(Char::isDigit).toIntOrNull() }
            .ifEmpty { listOf(0) }
}
