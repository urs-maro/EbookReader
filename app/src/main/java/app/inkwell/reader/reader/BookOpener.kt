package app.inkwell.reader.reader

import android.content.Context
import app.inkwell.reader.core.BookContent
import app.inkwell.reader.core.BookFormat
import app.inkwell.reader.parser.CbzParser
import app.inkwell.reader.parser.EpubParser
import app.inkwell.reader.parser.ReadiumEpubParser
import app.inkwell.reader.parser.Fb2Parser
import app.inkwell.reader.parser.TxtParser
import app.inkwell.reader.pdf.PdfSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UnsupportedFormatException(val format: BookFormat) :
    Exception("Formatul ${format.label} nu este încă suportat pentru citire")

/** Maps a stored file + format to fully-parsed [BookContent], off the main thread. */
object BookOpener {
    suspend fun open(context: Context, file: File, format: BookFormat): BookContent =
        withContext(Dispatchers.IO) {
            when (format) {
                // Prefer the Readium toolkit; fall back to the built-in parser on any failure.
                BookFormat.EPUB -> ReadiumEpubParser.parse(context, file) ?: EpubParser.parse(file)
                BookFormat.TXT -> TxtParser.parse(file)
                BookFormat.FB2 -> Fb2Parser.parse(file)
                BookFormat.CBZ -> CbzParser.parse(file)
                BookFormat.PDF -> PdfSupport.open(context, file)
                BookFormat.MOBI, BookFormat.AZW3 -> throw UnsupportedFormatException(format)
                BookFormat.UNKNOWN -> TxtParser.parse(file)
            }
        }
}
