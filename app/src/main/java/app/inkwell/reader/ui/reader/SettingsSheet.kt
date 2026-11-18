package app.inkwell.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import app.inkwell.reader.data.ReaderFont
import app.inkwell.reader.data.ReaderTheme
import app.inkwell.reader.data.ReadingSettings
import app.inkwell.reader.ui.theme.toFontFamily

@Composable
fun SettingsSheetContent(
    settings: ReadingSettings,
    onChange: ((ReadingSettings) -> ReadingSettings) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Afișare", style = MaterialTheme.typography.titleMedium)

        // Theme swatches
        SectionLabel("Fundal")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReaderTheme.entries.forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = settings.theme == theme,
                    onClick = { onChange { it.copy(theme = theme) } },
                )
            }
        }

        // Font family
        SectionLabel("Font")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderFont.entries.forEach { font ->
                Chip(
                    label = font.label,
                    selected = settings.font == font,
                    onClick = { onChange { it.copy(font = font) } },
                    bold = settings.font == font,
                )
            }
        }

        // Font size
        SectionLabel("Mărime text (${settings.fontSizeSp.toInt()})")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(icon = { Icon(Icons.Filled.Remove, null) }) {
                onChange { it.copy(fontSizeSp = (it.fontSizeSp - 1f).coerceAtLeast(ReadingSettings.MIN_FONT)) }
            }
            Slider(
                value = settings.fontSizeSp,
                onValueChange = { v -> onChange { it.copy(fontSizeSp = v) } },
                valueRange = ReadingSettings.MIN_FONT..ReadingSettings.MAX_FONT,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Stepper(icon = { Icon(Icons.Filled.Add, null) }) {
                onChange { it.copy(fontSizeSp = (it.fontSizeSp + 1f).coerceAtMost(ReadingSettings.MAX_FONT)) }
            }
        }

        // Line spacing
        SectionLabel("Spațiere rânduri")
        Slider(
            value = settings.lineHeight,
            onValueChange = { v -> onChange { it.copy(lineHeight = v) } },
            valueRange = 1.1f..2.2f,
        )

        // Margins
        SectionLabel("Margini")
        Slider(
            value = settings.marginScale,
            onValueChange = { v -> onChange { it.copy(marginScale = v) } },
            valueRange = 0.4f..1.8f,
        )

        // Brightness
        SectionLabel("Luminozitate")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Brightness6, null, Modifier.size(20.dp))
            Slider(
                value = if (settings.brightness < 0f) 0.5f else settings.brightness,
                onValueChange = { v -> onChange { it.copy(brightness = v) } },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Chip(
                label = "Auto",
                selected = settings.brightness < 0f,
                onClick = { onChange { it.copy(brightness = -1f) } },
            )
        }

        // Curl toggle
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Efect de întoarcere a paginii", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = settings.curlEnabled,
                onCheckedChange = { c -> onChange { it.copy(curlEnabled = c) } },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

@Composable
private fun ThemeSwatch(theme: ReaderTheme, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(theme.bg)
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) ring else theme.faint), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Aa", color = theme.fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, bold: Boolean = false) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .wrapContentWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Stepper(icon: @Composable () -> Unit, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { icon() }
}
