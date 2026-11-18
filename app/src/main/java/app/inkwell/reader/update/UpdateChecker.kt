package app.inkwell.reader.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the project's public GitHub Releases for a newer build. The version-comparison and
 * asset-selection logic is pure (no Android types) so it is unit-testable on the JVM; only
 * [fetchLatest] touches the network.
 */
object UpdateChecker {

    private const val LATEST_API =
        "https://api.github.com/repos/urs-maro/EbookReader/releases/latest"

    @Serializable
    data class Asset(val name: String = "", val browser_download_url: String = "")

    @Serializable
    data class Release(
        val tag_name: String = "",
        val name: String? = null,
        val body: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    /** A newer release with a downloadable APK, ready to present to the user. */
    data class Available(val version: String, val apkUrl: String, val notes: String?)

    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches the latest release, or null on any network/parse error. */
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CRIO-Books-Reader")
            }
            conn.use {
                if (it.responseCode !in 200..299) return@runCatching null
                val text = it.inputStream.use { s -> s.readBytes().toString(Charsets.UTF_8) }
                json.decodeFromString<Release>(text)
            }
        }.getOrNull()
    }

    /** Resolves whether [current] should be updated to the latest release, with its APK asset. */
    suspend fun check(current: String): Available? {
        val release = fetchLatest() ?: return null
        if (!isNewer(current, release.tag_name)) return null
        val apk = pickApk(release.assets) ?: return null
        return Available(
            version = release.tag_name.trim().removePrefix("v").removePrefix("V"),
            apkUrl = apk.browser_download_url,
            notes = release.body?.takeIf { it.isNotBlank() },
        )
    }

    /** True when [latest] is a strictly higher version than [current] (e.g. "v0.2.0" > "0.1.0"). */
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

    /** Picks the APK asset to install, preferring one whose name mentions "release". */
    fun pickApk(assets: List<Asset>): Asset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        return apks.firstOrNull { it.name.contains("release", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
