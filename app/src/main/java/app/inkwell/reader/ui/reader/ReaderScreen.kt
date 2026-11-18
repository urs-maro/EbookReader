package app.inkwell.reader.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.inkwell.reader.data.ReadingSettings
import app.inkwell.reader.reader.BookPaginationBuilder
import app.inkwell.reader.reader.PaginatedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel()
    LaunchedEffect(bookId) { vm.load(bookId) }
    val state by vm.state.collectAsState()
    val settings by vm.settings.collectAsState(initial = ReadingSettings())

    // Apply in-app brightness to the window.
    val context = LocalContext.current
    LaunchedEffect(settings.brightness) {
        (context as? Activity)?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = if (settings.brightness < 0f)
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.brightness
            win.attributes = lp
        }
    }

    Box(Modifier.fillMaxSize().background(settings.theme.bg)) {
        when (val s = state) {
            is ReaderState.Loading -> CenteredSpinner(settings.theme.fg)
            is ReaderState.Error -> CenteredMessage(s.message, settings.theme.fg, onBack)
            is ReaderState.Loaded -> ReaderContent(vm, s, settings, onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    vm: ReaderViewModel,
    s: ReaderState.Loaded,
    settings: ReadingSettings,
    onBack: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var chromeVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Never close the book by an accidental edge-swipe: a back gesture first reveals the
    // toolbar (with its back arrow); only the arrow — or a second back — actually closes.
    BackHandler(enabled = !chromeVisible) { chromeVisible = true }

    val ctx = LocalContext.current
    val pageTexture = remember(settings.theme) {
        if (settings.theme.textured)
            app.inkwell.reader.reader.PapyrusTexture.get(ctx).asImageBitmap() else null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = IntSize(constraints.maxWidth, constraints.maxHeight)
        val paged by produceState<PaginatedBook?>(null, s.content, settings, size, pageTexture) {
            if (size.width <= 0 || size.height <= 0) return@produceState
            value = null
            value = withContext(Dispatchers.Default) {
                BookPaginationBuilder.build(s.content, settings, size, measurer, density, pageTexture)
            }
        }

        var index by remember(paged) { mutableIntStateOf(-1) }
        LaunchedEffect(paged) {
            val book = paged ?: return@LaunchedEffect
            if (index < 0) index = book.pageForPosition(s.book.position)
        }
        LaunchedEffect(index, paged) {
            val book = paged ?: return@LaunchedEffect
            if (index in 0 until book.pageCount) vm.savePosition(book.positionForPage(index))
        }

        val book = paged
        if (book == null || index < 0) {
            CenteredSpinner(settings.theme.fg)
        } else {
            PageCurl(
                book = book,
                index = index.coerceIn(0, (book.pageCount - 1).coerceAtLeast(0)),
                onIndexChange = { index = it },
                background = settings.theme.bg,
                curlEnabled = settings.curlEnabled,
                onCenterTap = { chromeVisible = !chromeVisible },
                modifier = Modifier.systemGestureExclusion(),
            )

            // Top bar
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            s.content.meta.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Înapoi")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Tune, "Setări")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = settings.theme.bg.copy(alpha = 0.96f),
                        titleContentColor = settings.theme.fg,
                        navigationIconContentColor = settings.theme.fg,
                        actionIconContentColor = settings.theme.fg,
                    ),
                )
            }

            // Bottom scrubber
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(settings.theme.bg.copy(alpha = 0.96f))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        book.label(index.coerceIn(0, book.pageCount - 1)),
                        color = settings.theme.faint,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (book.pageCount > 1) {
                        Slider(
                            value = index.coerceIn(0, book.pageCount - 1).toFloat(),
                            onValueChange = { index = it.toInt() },
                            valueRange = 0f..(book.pageCount - 1).toFloat(),
                        )
                    }
                    Text(
                        "${index + 1} / ${book.pageCount}",
                        color = settings.theme.faint,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = sheetState,
            ) {
                SettingsSheetContent(
                    settings = settings,
                    onChange = { transform -> vm.updateSettings(transform) },
                )
            }
        }
    }
}

@Composable
private fun CenteredSpinner(tint: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = tint)
    }
}

@Composable
private fun CenteredMessage(message: String, tint: Color, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = tint)
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Înapoi", tint = tint)
            }
        }
    }
}
