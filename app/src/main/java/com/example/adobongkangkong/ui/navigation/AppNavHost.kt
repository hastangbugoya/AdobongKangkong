package com.example.adobongkangkong.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.adobongkangkong.ui.dashboard.DashboardScreen
import com.example.adobongkangkong.ui.food.editor.FoodEditorScreen
import com.example.adobongkangkong.ui.food.FoodsListScreen
import com.example.adobongkangkong.ui.recipe.RecipeBuilderScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Dashboard.route,
        modifier = modifier
    ) {

        // -----------------------------
        // Dashboard
        // -----------------------------
        composable(NavRoutes.Dashboard.route) {
            DashboardScreen(
                onCreateRecipe = { navController.navigate(NavRoutes.Recipes.new) },
                onCreateFood = { navController.navigate(NavRoutes.Foods.new()) },
                onOpenFoods = { navController.navigate(NavRoutes.Foods.list) }
            )
        }

        // -----------------------------
        // Foods list
        // -----------------------------
        composable(NavRoutes.Foods.list) {
            FoodsListScreen(
                onBack = { navController.popBackStack() },
                onEditFood = { id -> navController.navigate(NavRoutes.Foods.edit(id)) },
                onEditRecipe = { id -> navController.navigate(NavRoutes.Recipes.edit(id)) },
                onCreateFood = { navController.navigate(NavRoutes.Foods.new()) }
            )
        }

        // -----------------------------
        // Create new food
        // -----------------------------
        composable(
            route = NavRoutes.Foods.new,
            arguments = listOf(navArgument("name") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { entry ->

            val raw = entry.arguments?.getString("name").orEmpty()
            val initialName = URLDecoder
                .decode(raw, StandardCharsets.UTF_8.name())
                .takeIf { it.isNotBlank() }

            FoodEditorScreen(
                foodId = null,
                initialName = initialName,
                onBack = { navController.popBackStack() }
            )
        }

        // -----------------------------
        // Edit existing food
        // -----------------------------
        composable(
            route = NavRoutes.Foods.edit,
            arguments = listOf(navArgument("foodId") {
                type = NavType.LongType
            })
        ) { entry ->
            val foodId = entry.arguments!!.getLong("foodId")

            FoodEditorScreen(
                foodId = foodId,
                initialName = null,
                onBack = { navController.popBackStack() }
            )
        }

        // -----------------------------
        // Create new recipe
        // -----------------------------
        composable(NavRoutes.Recipes.new) {
            RecipeBuilderScreen(
                editFoodId = null,
                onBack = { navController.popBackStack() }
            )
        }

        // -----------------------------
        // Edit existing recipe
        // -----------------------------
        composable(
            route = NavRoutes.Recipes.edit,
            arguments = listOf(navArgument("foodId") {
                type = NavType.LongType
            })
        ) { entry ->
            val foodId = entry.arguments!!.getLong("foodId")

            RecipeBuilderScreen(
                editFoodId = foodId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

