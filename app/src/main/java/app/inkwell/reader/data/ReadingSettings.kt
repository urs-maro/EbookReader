package app.inkwell.reader.data

import androidx.compose.ui.graphics.Color

enum class ReaderTheme(val bg: Color, val fg: Color, val faint: Color, val textured: Boolean = false) {
    LIGHT(Color(0xFFFBF8F3), Color(0xFF1C1B19), Color(0xFF8A857C)),
    SEPIA(Color(0xFFF4ECD8), Color(0xFF4A3F2E), Color(0xFF9A8B6E)),
    PAPYRUS(Color(0xFFE7D6AE), Color(0xFF3A2C16), Color(0xFF8A7248), textured = true),
    NIGHT(Color(0xFF14161A), Color(0xFFC9CBD0), Color(0xFF6B7078)),
}

enum class ReaderFont(val label: String) {
    LITERATA("Literata"),
    LORA("Lora"),
    INTER("Inter"),
    SYSTEM_SERIF("Serif"),
    SYSTEM_SANS("Sans"),
}

data class ReadingSettings(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val font: ReaderFont = ReaderFont.LITERATA,
    val fontSizeSp: Float = 19f,
    val lineHeight: Float = 1.5f,
    val marginScale: Float = 1f,
    /** -1f = follow the system brightness; otherwise 0f..1f applied to the window. */
    val brightness: Float = -1f,
    val curlEnabled: Boolean = true,
) {
    companion object {
        const val MIN_FONT = 13f
        const val MAX_FONT = 32f
    }
}
