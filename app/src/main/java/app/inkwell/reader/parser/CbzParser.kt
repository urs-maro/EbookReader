package app.inkwell.reader.parser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookMeta
import app.inkwell.reader.core.ImagePageProvider
import java.io.File
import java.util.zip.ZipFile

/** Comic Book Zip (.cbz): a zip of ordered images rendered as fixed pages. */
object CbzParser {

    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun parse(file: File): BookContent.Paged {
        val names = ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in imageExts }
                .map { it.name }
                .sortedWith(naturalOrder)
                .toList()
        }
        val provider = object : ImagePageProvider {
            override val count: Int = names.size
            override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
                if (index !in names.indices) return null
                return ZipFile(file).use { zip ->
                    val entry = zip.getEntry(names[index]) ?: return null
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    decodeScaled(bytes, maxWidth, maxHeight)
                }
            }
        }
        return BookContent.Paged(
            meta = BookMeta(file.nameWithoutExtension, null),
            pageCount = names.size,
            imageProvider = provider,
        )
    }

    fun firstImageBytes(file: File): ByteArray? = runCatching {
        ZipFile(file).use { zip ->
            val first = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in imageExts }
                .sortedWith(compareBy(naturalOrder) { it.name })
                .firstOrNull() ?: return@runCatching null
            zip.getInputStream(first).use { it.readBytes() }
        }
    }.getOrNull()

    private val naturalOrder = Comparator<String> { a, b ->
        val re = Regex("\\d+|\\D+")
        val pa = re.findAll(a).map { it.value }.toList()
        val pb = re.findAll(b).map { it.value }.toList()
        var i = 0
        while (i < pa.size && i < pb.size) {
            val x = pa[i]; val y = pb[i]
            val cmp = if (x.first().isDigit() && y.first().isDigit()) {
                (x.toLongOrNull() ?: 0).compareTo(y.toLongOrNull() ?: 0)
            } else x.compareTo(y, ignoreCase = true)
            if (cmp != 0) return@Comparator cmp
            i++
        }
        pa.size - pb.size
    }

    fun decodeScaled(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (maxWidth <= 0 || maxHeight <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2 || bounds.outHeight / sample > maxHeight * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
