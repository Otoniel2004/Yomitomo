package io.github.otoniel2004.mangareaderjp

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
import io.github.otoniel2004.mangareaderjp.ui.detail.ItemDetailScreen
import io.github.otoniel2004.mangareaderjp.ui.home.HomeScreen
import io.github.otoniel2004.mangareaderjp.ui.options.ReaderOptionsScreen
import io.github.otoniel2004.mangareaderjp.ui.review.ReviewScreen
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
}

/**
 * App screen map:
 *
 *   home ──► item/{id}   (card detail + study)
 *     ├────► review      (review every due card)
 *     └────► options     (reader options)
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
            )
        }
        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ItemDetailScreen(id = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable("review") { ReviewScreen(onBack = { nav.popBackStack() }) }
        composable("options") { ReaderOptionsScreen(onBack = { nav.popBackStack() }) }
    }
}
