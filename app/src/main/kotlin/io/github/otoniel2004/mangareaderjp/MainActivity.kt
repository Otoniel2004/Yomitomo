package io.github.otoniel2004.mangareaderjp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.otoniel2004.mangareaderjp.reader.ReaderStatus
import io.github.otoniel2004.mangareaderjp.ui.add.AddItemScreen
import io.github.otoniel2004.mangareaderjp.ui.detail.ItemDetailScreen
import io.github.otoniel2004.mangareaderjp.ui.home.HomeScreen
import io.github.otoniel2004.mangareaderjp.ui.options.ReaderOptionsScreen
import io.github.otoniel2004.mangareaderjp.ui.review.ReviewScreen
import io.github.otoniel2004.mangareaderjp.ui.scan.ScanScreen
import io.github.otoniel2004.mangareaderjp.ui.stats.StatsScreen
import io.github.otoniel2004.mangareaderjp.ui.theme.MangaReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MangaReaderTheme {
                AppNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ReaderStatus.appInForeground = true
    }

    override fun onPause() {
        ReaderStatus.appInForeground = false
        super.onPause()
    }
}

/**
 * App screen map:
 *
 *   home ──► item/{id}         (card detail + study)
 *     ├────► review            (review every due card)
 *     ├────► options           (reader options)
 *     ├────► add               (new card: type romaji or Japanese)
 *     └────► scan/{camera}     (photo → OCR → pick text) ──► add?text=…&image=…
 */
@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenItem = { id -> nav.navigate("item/$id") },
                onReview = { nav.navigate("review") },
                onOpenOptions = { nav.navigate("options") },
                onOpenStats = { nav.navigate("stats") },
                onAddTyped = { nav.navigate("add") },
                onScan = { fromCamera -> nav.navigate("scan/$fromCamera") },
            )
        }
        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ItemDetailScreen(id = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable("review") { ReviewScreen(onBack = { nav.popBackStack() }) }
        composable("options") { ReaderOptionsScreen(onBack = { nav.popBackStack() }) }
        composable("stats") { StatsScreen(onBack = { nav.popBackStack() }) }
        composable(
            "add?text={text}&image={image}&source={source}",
            arguments = listOf(
                navArgument("text") { type = NavType.StringType; defaultValue = "" },
                navArgument("image") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("source") { type = NavType.StringType; defaultValue = "Manual" },
            ),
        ) { entry ->
            val args = entry.arguments
            AddItemScreen(
                initialText = args?.getString("text").orEmpty(),
                imagePath = args?.getString("image"),
                source = args?.getString("source") ?: "Manual",
                onDone = { nav.popBackStack("home", inclusive = false) },
            )
        }
        composable("scan/{camera}", arguments = listOf(navArgument("camera") { type = NavType.BoolType })) { entry ->
            val fromCamera = entry.arguments?.getBoolean("camera") ?: true
            ScanScreen(
                fromCamera = fromCamera,
                onBack = { nav.popBackStack() },
                onCreateCard = { text, image ->
                    val query = buildString {
                        append("add?text=").append(Uri.encode(text))
                        if (image != null) append("&image=").append(Uri.encode(image))
                        append("&source=").append(if (fromCamera) "Camera" else "Image")
                    }
                    nav.navigate(query)
                },
            )
        }
    }
}
