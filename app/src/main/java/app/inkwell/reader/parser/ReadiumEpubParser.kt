package app.inkwell.reader.parser

import android.content.Context
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookMeta
import app.inkwell.reader.core.Chapter
import app.inkwell.reader.core.TocEntry
import org.jsoup.Jsoup
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/**
 * Opens an EPUB with the Readium Kotlin Toolkit and adapts its [Publication] into our own
 * [BookContent.Reflowable] model, so our custom paginator + page-curl keep rendering it.
 * Returns null on any failure so the caller can fall back to the built-in [EpubParser].
 */
object ReadiumEpubParser {

    suspend fun parse(context: Context, file: File): BookContent.Reflowable? {
        val publication = openPublication(context, file) ?: return null
        try {
            val meta = BookMeta(
                title = publication.metadata.title ?: file.nameWithoutExtension,
                author = publication.metadata.authors.firstOrNull()?.name,
                language = publication.metadata.languages.firstOrNull(),
            )

            val readingOrder = publication.readingOrder
            val chapters = ArrayList<Chapter>()
            val hrefToIndex = HashMap<String, Int>()

            readingOrder.forEachIndexed { index, link ->
                hrefToIndex[normHref(link)] = index
                val html = readResource(publication, link) ?: run {
                    chapters += Chapter("r-$index", null, emptyList())
                    return@forEachIndexed
                }
                val doc = Jsoup.parse(html)
                val blocks = HtmlToRich.convert(doc.body())
                val title = doc.body().selectFirst("h1, h2, h3")?.text()?.take(80)
                chapters += Chapter("r-$index", title, blocks)
            }

            val toc = buildToc(publication, hrefToIndex, chapters)
            return BookContent.Reflowable(meta, toc, chapters)
        } finally {
            runCatching { publication.close() }
        }
    }

    private suspend fun openPublication(context: Context, file: File): Publication? = runCatching {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val url = file.toUrl(isDirectory = false)
        val asset = assetRetriever.retrieve(url).getOrNull() ?: return null
        val parser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null)
        val opener = PublicationOpener(parser)
        opener.open(asset, allowUserInteraction = false).getOrNull()
    }.getOrNull()

    private suspend fun readResource(publication: Publication, link: Link): String? = runCatching {
        val resource = publication.get(link.url()) ?: return null
        val bytes = resource.read().getOrNull() ?: return null
        resource.close()
        bytes.toString(Charsets.UTF_8)
    }.getOrNull()

    private suspend fun buildToc(
        publication: Publication,
        hrefToIndex: Map<String, Int>,
        chapters: List<Chapter>,
    ): List<TocEntry> {
        val toc = publication.tableOfContents
        val entries = ArrayList<TocEntry>()
        fun walk(links: List<Link>, depth: Int) {
            for (l in links) {
                val idx = hrefToIndex[normHref(l)]
                if (idx != null) entries += TocEntry(l.title?.trim().orEmpty().ifBlank { "—" }, idx, depth)
                if (l.children.isNotEmpty()) walk(l.children, depth + 1)
            }
        }
        walk(toc, 0)
        if (entries.isNotEmpty()) return entries.distinctBy { it.chapterIndex to it.title }
        return chapters.mapIndexedNotNull { i, c -> c.title?.let { TocEntry(it, i) } }
    }

    private fun normHref(link: Link): String =
        link.url().removeFragment().toString()
}
