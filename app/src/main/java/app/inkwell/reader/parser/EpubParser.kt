package app.inkwell.reader.parser

import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookMeta
import app.inkwell.reader.core.Chapter
import app.inkwell.reader.core.TocEntry
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/**
 * Minimal but robust EPUB 2/3 parser built directly on the zip container + OPF package
 * document. Produces a [BookContent.Reflowable] so the same paginator/curl renders EPUB,
 * TXT, FB2 and reflowed PDF uniformly. (Readium can be slotted behind this same interface.)
 */
object EpubParser {

    data class CoverResult(val bytes: ByteArray, val ext: String)

    fun parse(file: File): BookContent.Reflowable {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opfText = zip.readText(opfPath)
            val opf = Jsoup.parse(opfText, "", Parser.xmlParser())

            val title = opf.firstText("dc:title", "title") ?: file.nameWithoutExtension
            val author = opf.firstText("dc:creator", "creator")
            val language = opf.firstText("dc:language", "language")

            // manifest: id -> (href, mediaType, properties)
            val manifest = HashMap<String, Triple<String, String, String>>()
            for (item in opf.getElementsByTag("item")) {
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifest[id] = Triple(
                        resolve(opfDir, href),
                        item.attr("media-type"),
                        item.attr("properties"),
                    )
                }
            }

            // spine order
            val spineHrefs = ArrayList<Pair<String, String>>() // id -> href
            for (ref in opf.getElementsByTag("itemref")) {
                val idref = ref.attr("idref")
                manifest[idref]?.let { spineHrefs += idref to it.first }
            }

            val chapters = ArrayList<Chapter>()
            for ((idref, href) in spineHrefs) {
                val html = runCatching { zip.readText(href) }.getOrNull() ?: continue
                val doc = Jsoup.parse(html)
                val body = doc.body()
                val chapterDir = href.substringBeforeLast('/', "")
                // rebase image refs to absolute zip paths
                val blocks = HtmlToRich.convert(body).map { b ->
                    if (b.imageRef != null) b.copy(imageRef = resolve(chapterDir, b.imageRef)) else b
                }
                val chTitle = body.selectFirst("h1, h2, h3")?.text()?.take(80)
                chapters += Chapter(id = idref, title = chTitle, blocks = blocks)
            }

            val toc = buildToc(zip, opf, manifest, opfDir, chapters)

            val zipPath = file.absolutePath
            val resolver: suspend (String) -> ByteArray? = { ref ->
                runCatching { ZipFile(File(zipPath)).use { it.readBytes(ref) } }.getOrNull()
            }

            return BookContent.Reflowable(
                meta = BookMeta(title.trim(), author?.trim(), language?.trim()),
                toc = toc,
                chapters = chapters,
                imageResolver = resolver,
            )
        }
    }

    /** Extracts cover image bytes if present, for the library thumbnail. */
    fun extractCover(file: File): CoverResult? = runCatching {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = Jsoup.parse(zip.readText(opfPath), "", Parser.xmlParser())

            val items = opf.getElementsByTag("item")
            fun hrefOfId(id: String?) = id?.let { i -> items.firstOrNull { it.attr("id") == i }?.attr("href") }
            fun isImage(el: org.jsoup.nodes.Element) = el.attr("media-type").startsWith("image")

            // Collect candidate hrefs in priority order; each may be an image or an XHTML page.
            val candidates = ArrayList<String>()
            // 1. EPUB3 properties="cover-image"
            items.firstOrNull { it.attr("properties").contains("cover-image") }?.attr("href")?.let { candidates += it }
            // 2. EPUB2 <meta name="cover" content="ID">
            hrefOfId(opf.getElementsByTag("meta").firstOrNull { it.attr("name") == "cover" }?.attr("content"))?.let { candidates += it }
            // 3. <guide><reference type="cover" href="...">
            opf.getElementsByTag("reference").firstOrNull { it.attr("type").contains("cover") }?.attr("href")?.let { candidates += it }
            // 4. item whose id mentions "cover"
            items.firstOrNull { it.attr("id").contains("cover", true) && isImage(it) }?.attr("href")?.let { candidates += it }
            // 5. the first spine item (often the cover page)
            opf.getElementsByTag("itemref").firstOrNull()?.attr("idref")?.let { hrefOfId(it) }?.let { candidates += it }
            // 6. any image whose href mentions "cover"
            items.firstOrNull { isImage(it) && it.attr("href").contains("cover", true) }?.attr("href")?.let { candidates += it }

            for (href in candidates.distinct()) {
                val path = resolve(opfDir, href)
                val ext = path.substringAfterLast('.', "").lowercase()
                if (ext in setOf("jpg", "jpeg", "png", "gif", "webp")) {
                    val bytes = zip.readBytes(path) ?: continue
                    return@runCatching CoverResult(bytes, ext)
                }
                // Otherwise it's an (X)HTML/SVG cover page → pull the first image out of it.
                val text = runCatching { zip.readText(path) }.getOrNull() ?: continue
                val doc = Jsoup.parse(text)
                val imgSrc = doc.selectFirst("img[src]")?.attr("src")
                    ?: doc.selectFirst("image")?.let { it.attr("xlink:href").ifBlank { it.attr("href") } }
                if (!imgSrc.isNullOrBlank()) {
                    val imgPath = resolve(path.substringBeforeLast('/', ""), imgSrc)
                    val bytes = zip.readBytes(imgPath) ?: continue
                    return@runCatching CoverResult(bytes, imgPath.substringAfterLast('.', "jpg"))
                }
            }
            null
        }
    }.getOrNull()

    private fun buildToc(
        zip: ZipFile,
        opf: org.jsoup.nodes.Document,
        manifest: Map<String, Triple<String, String, String>>,
        opfDir: String,
        chapters: List<Chapter>,
    ): List<TocEntry> {
        val hrefToIndex = HashMap<String, Int>()
        // map spine href (file part) -> chapter index
        val spineIds = opf.getElementsByTag("itemref").map { it.attr("idref") }
        spineIds.forEachIndexed { i, id ->
            manifest[id]?.first?.substringBefore('#')?.let { hrefToIndex[it] = i }
        }

        // EPUB3 nav
        val navItem = opf.getElementsByTag("item")
            .firstOrNull { it.attr("properties").contains("nav") }?.attr("href")
        if (navItem != null) {
            val navPath = resolve(opfDir, navItem)
            runCatching { zip.readText(navPath) }.getOrNull()?.let { navHtml ->
                val doc = Jsoup.parse(navHtml)
                val nav = doc.selectFirst("nav[epub|type=toc]") ?: doc.selectFirst("nav")
                val entries = nav?.select("a")?.mapNotNull { a ->
                    val target = resolve(navPath.substringBeforeLast('/', ""), a.attr("href"))
                        .substringBefore('#')
                    val idx = hrefToIndex[target] ?: return@mapNotNull null
                    TocEntry(a.text().trim().ifBlank { "—" }, idx)
                }?.distinctBy { it.chapterIndex to it.title }
                if (!entries.isNullOrEmpty()) return entries
            }
        }

        // EPUB2 ncx
        val ncxHref = opf.getElementsByTag("item")
            .firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" }?.attr("href")
        if (ncxHref != null) {
            val ncxPath = resolve(opfDir, ncxHref)
            runCatching { zip.readText(ncxPath) }.getOrNull()?.let { ncx ->
                val doc = Jsoup.parse(ncx, "", Parser.xmlParser())
                val entries = doc.getElementsByTag("navPoint").mapNotNull { np ->
                    val label = np.selectFirst("navLabel > text")?.text()?.trim() ?: return@mapNotNull null
                    val src = np.selectFirst("content")?.attr("src") ?: return@mapNotNull null
                    val target = resolve(ncxPath.substringBeforeLast('/', ""), src).substringBefore('#')
                    val idx = hrefToIndex[target] ?: return@mapNotNull null
                    TocEntry(label, idx)
                }.distinctBy { it.chapterIndex to it.title }
                if (entries.isNotEmpty()) return entries
            }
        }

        // Fallback: chapter titles
        return chapters.mapIndexedNotNull { i, c ->
            c.title?.let { TocEntry(it, i) }
        }
    }

    private fun findOpfPath(zip: ZipFile): String {
        val container = zip.readText("META-INF/container.xml")
        val doc = Jsoup.parse(container, "", Parser.xmlParser())
        return doc.selectFirst("rootfile")?.attr("full-path")
            ?: error("EPUB without rootfile")
    }

    /** Resolves [href] relative to [baseDir] inside the zip, normalizing ./ and ../ */
    private fun resolve(baseDir: String, href: String): String {
        val clean = href.substringBefore('#')
        if (clean.startsWith('/')) return clean.trimStart('/')
        val parts = ArrayList<String>()
        if (baseDir.isNotEmpty()) parts.addAll(baseDir.split('/'))
        for (seg in clean.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun ZipFile.readText(entry: String): String =
        readBytes(entry)?.toString(Charsets.UTF_8) ?: error("Missing zip entry: $entry")

    private fun ZipFile.readBytes(entry: String): ByteArray? {
        val e = getEntry(entry)
            ?: getEntry(entry.removePrefix("/"))
            ?: getEntry(entry.replace('/', '\\')) // tolerate non-compliant back-slash zips
            ?: return null
        return getInputStream(e).use { it.readBytes() }
    }

    private fun org.jsoup.nodes.Document.firstText(vararg tags: String): String? {
        for (t in tags) {
            val el = getElementsByTag(t).firstOrNull { it.text().isNotBlank() }
            if (el != null) return el.text()
        }
        return null
    }
}
