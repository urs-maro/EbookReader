package app.inkwell.reader.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.inkwell.reader.update.ApkUpdater
import app.inkwell.reader.update.UpdateChecker
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/** Where new builds are published; the About screen links here for updates. */
private const val UPDATE_URL = "https://github.com/urs-maro/EbookReader/releases/latest"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Despre") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Înapoi")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // CRIO Software logo — place the file at app/src/main/assets/crio_logo.png
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/crio_logo.png")
                    .build(),
                contentDescription = "CRIO Software",
                modifier = Modifier.size(160.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "CRIO-Books-Reader",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Cititor de ebook-uri",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Versiunea $version",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(20.dp))
            UpdateSection(currentVersion = version)
            Spacer(Modifier.height(32.dp))
            Text(
                "© CRIO Software",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "EPUB · PDF · FB2 · TXT · CBZ\nMotor EPUB: Readium Kotlin Toolkit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** In-app update: check GitHub Releases, then download and launch the installer. */
@Composable
private fun UpdateSection(currentVersion: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = idle; other values drive the UI below.
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<UpdateChecker.Available?>(null) }

    val busy = checking || downloading
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    when {
        downloading -> StatusRow("Se descarcă actualizarea…")
        checking -> StatusRow("Se verifică actualizările…")
        available != null -> {
            val info = available!!
            Text(
                "Versiune nouă disponibilă: v${info.version}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        downloading = true
                        message = null
                        val file = ApkUpdater.download(context, info.apkUrl)
                        downloading = false
                        if (file != null) {
                            runCatching { ApkUpdater.install(context, file) }
                                .onFailure { message = "Nu pot porni instalarea." }
                        } else {
                            message = "Descărcarea a eșuat."
                        }
                    }
                },
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Descarcă și instalează")
            }
        }
        else -> {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        checking = true
                        message = null
                        val result = runCatching { UpdateChecker.check(currentVersion) }.getOrNull()
                        checking = false
                        if (result != null) {
                            available = result
                        } else {
                            message = "Ești la zi."
                        }
                    }
                },
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Caută actualizări")
            }
        }
    }

    message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = faint, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        UPDATE_URL,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StatusRow(text: String) {
    CircularProgressIndicator(Modifier.size(28.dp))
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
