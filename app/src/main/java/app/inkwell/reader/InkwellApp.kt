package app.inkwell.reader

import android.app.Application
import app.inkwell.reader.data.BookImporter
import app.inkwell.reader.data.LibraryRepository
import app.inkwell.reader.data.SettingsRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/** App-wide singletons. Lightweight manual DI — no framework needed for this scope. */
class InkwellApp : Application() {
    lateinit var library: LibraryRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var importer: BookImporter
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        library = LibraryRepository(this)
        settings = SettingsRepository(this)
        importer = BookImporter(this, library)
    }
}
