package app.inkwell.reader.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.inkwell.reader.R
import app.inkwell.reader.data.ReaderFont

// Full variable fonts (Latin + Greek + Cyrillic coverage), so Greek/Cyrillic text renders.
val LiterataFamily = FontFamily(
    Font(
        R.font.literata_var, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.literata_var, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.literata_var_italic, FontWeight.Normal, FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

val InterFamily = FontFamily(
    Font(
        R.font.inter_var, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.inter_var, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

// Lora covers Latin + Cyrillic (no Greek); kept for readers who prefer it.
val LoraFamily = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_bold, FontWeight.Bold),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
)

fun ReaderFont.toFontFamily(): FontFamily = when (this) {
    ReaderFont.LITERATA -> LiterataFamily
    ReaderFont.LORA -> LoraFamily
    ReaderFont.INTER -> InterFamily
    ReaderFont.SYSTEM_SERIF -> FontFamily.Serif
    ReaderFont.SYSTEM_SANS -> FontFamily.SansSerif
}
