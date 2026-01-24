package com.example.adobongkangkong.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.adobongkangkong.ui.dashboard.DashboardScreen
import com.example.adobongkangkong.ui.recipe.RecipeBuilderScreen
import com.example.adobongkangkong.ui.food.FoodEditorScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.DASHBOARD,
        modifier = modifier
    ) {

        composable(NavRoutes.DASHBOARD) {
            DashboardScreen(
                onCreateRecipe = { navController.navigate(NavRoutes.RECIPE_NEW) },
                onCreateFood = { navController.navigate(NavRoutes.FOOD_NEW) }
            )
        }

        composable(NavRoutes.RECIPE_NEW) {
            RecipeBuilderScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.FOOD_NEW) {
            FoodEditorScreen(
                foodId = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            NavRoutes.FOOD_EDIT,
            arguments = listOf(navArgument("foodId") { type = NavType.LongType })
        ) {
            val foodId = it.arguments!!.getLong("foodId")
            FoodEditorScreen(
                foodId = foodId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

