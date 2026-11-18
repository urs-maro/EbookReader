package app.inkwell.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.inkwell.reader.ui.about.AboutScreen
import app.inkwell.reader.ui.library.LibraryScreen
import app.inkwell.reader.ui.reader.ReaderScreen
import app.inkwell.reader.ui.theme.InkwellTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InkwellTheme {
                Surface(Modifier.fillMaxSize()) { InkwellNav() }
            }
        }
    }
}

@Composable
private fun InkwellNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onOpenBook = { id -> nav.navigate("reader/$id") },
                onOpenAbout = { nav.navigate("about") },
            )
        }
        composable("about") {
            AboutScreen(onBack = { nav.popBackStack() })
        }
        composable("reader/{bookId}") { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            ReaderScreen(bookId = bookId, onBack = { nav.popBackStack() })
        }
    }
}
