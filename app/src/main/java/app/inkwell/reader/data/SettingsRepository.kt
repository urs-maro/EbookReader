package app.inkwell.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("reader_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val font = stringPreferencesKey("font")
        val fontSize = floatPreferencesKey("font_size")
        val lineHeight = floatPreferencesKey("line_height")
        val margin = floatPreferencesKey("margin")
        val brightness = floatPreferencesKey("brightness")
        val curl = booleanPreferencesKey("curl")
    }

    val settings: Flow<ReadingSettings> = store.data.map { p ->
        ReadingSettings(
            theme = p[Keys.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                ?: ReaderTheme.LIGHT,
            font = p[Keys.font]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() }
                ?: ReaderFont.LITERATA,
            fontSizeSp = p[Keys.fontSize] ?: 19f,
            lineHeight = p[Keys.lineHeight] ?: 1.5f,
            marginScale = p[Keys.margin] ?: 1f,
            brightness = p[Keys.brightness] ?: -1f,
            curlEnabled = p[Keys.curl] ?: true,
        )
    }

    suspend fun update(transform: (ReadingSettings) -> ReadingSettings) {
        store.edit { p ->
            val current = ReadingSettings(
                theme = p[Keys.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                    ?: ReaderTheme.LIGHT,
                font = p[Keys.font]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() }
                    ?: ReaderFont.LITERATA,
                fontSizeSp = p[Keys.fontSize] ?: 19f,
                lineHeight = p[Keys.lineHeight] ?: 1.5f,
                marginScale = p[Keys.margin] ?: 1f,
                brightness = p[Keys.brightness] ?: -1f,
                curlEnabled = p[Keys.curl] ?: true,
            )
            val next = transform(current)
            p[Keys.theme] = next.theme.name
            p[Keys.font] = next.font.name
            p[Keys.fontSize] = next.fontSizeSp
            p[Keys.lineHeight] = next.lineHeight
            p[Keys.margin] = next.marginScale
            p[Keys.brightness] = next.brightness
            p[Keys.curl] = next.curlEnabled
        }
    }
}
