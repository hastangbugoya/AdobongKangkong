package com.example.adobongkangkong.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.dashboard.DashboardScreen
import com.example.adobongkangkong.ui.daylog.DayLogScreen
import com.example.adobongkangkong.ui.food.FoodsListScreen
import com.example.adobongkangkong.ui.food.editor.FoodEditorRoute
import com.example.adobongkangkong.ui.heatmap.HeatmapScreen
import com.example.adobongkangkong.ui.recipe.RecipeBuilderScreen
import com.example.adobongkangkong.ui.startup.StartupScreen
import com.example.adobongkangkong.ui.startup.StartupViewModel
import java.time.LocalDate

@Composable
fun AppNavHost(
    navController: NavHostController,
    bannerCaptureController: BannerCaptureController,
    modifier: Modifier = Modifier,
    startDestination: String = "startup"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {


        composable("startup") {
            StartupScreen(
                onDone = {
                    navController.navigate("dashboard") {
                        popUpTo("startup") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ------------------------------------------------------------
        // Dashboard (always today)
        // ------------------------------------------------------------

        composable(route = NavRoutes.Dashboard.route) {
            DashboardScreen(
                onEditFood = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                },
                onCreateRecipe = {
                    // For now your recipes entry is the builder/list route.
                    navController.navigate(NavRoutes.Recipes.route)
                },
                onCreateFood = { prefillName ->
                    navController.navigate(NavRoutes.Foods.new(prefillName))
                },
                onOpenFoods = {
                    navController.navigate(NavRoutes.Foods.list)
                },
                onOpenHeatmap = {
                    navController.navigate(NavRoutes.Heatmap.route)
                },
                onOpenDayLog = { date: LocalDate ->
                    navController.navigate(NavRoutes.DayLog.dayLog(date))
                }
            )
        }

        // ------------------------------------------------------------
        // Heatmap
        // ------------------------------------------------------------

        composable(NavRoutes.Heatmap.route) {
            HeatmapScreen(
                onNavigateToDayLog = { date ->
                    navController.navigate(NavRoutes.DayLog.dayLog(date))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ------------------------------------------------------------
        // Day Log (shared)
        // ------------------------------------------------------------

        composable(
            route = NavRoutes.DayLog.route,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType }
            )
        ) { entry ->
            val dateIso = entry.arguments!!.getString("date")!!
            val date = LocalDate.parse(dateIso)

            DayLogScreen(
                date = date,
                onBack = { navController.popBackStack() }
            )
        }

        // ------------------------------------------------------------
        // Foods
        // ------------------------------------------------------------

        composable(route = NavRoutes.Foods.list) {
            FoodsListScreen(
                onBack = {
                    navController.popBackStack()
                },

                // Row tap → edit food
                onEditFood = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                },

                // Row tap → edit recipe (or route to recipes entry for now)
                onEditRecipe = { recipeId ->
                    navController.navigate(NavRoutes.Recipes.builder(editFoodId = recipeId))
                    // or NavRoutes.Recipes.route / list if that’s your current setup
                },

                // Add button when filter = FOODS_ONLY or ALL
                onCreateFood = {
                    navController.navigate(
                        NavRoutes.Foods.new(prefillName = null)
                    )
                },

                // Add button when filter = RECIPES_ONLY
                onCreateRecipe = {
                    navController.navigate(
                        NavRoutes.Recipes.route
                    )
                }
            )
        }

        composable(
            route = NavRoutes.Foods.edit,
            arguments = listOf(navArgument("foodId") { type = NavType.LongType })
        ) { entry ->
            val foodId = entry.arguments!!.getLong("foodId")

            FoodEditorRoute(
                foodId = foodId,
                initialName = null,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                bannerCaptureController = bannerCaptureController
            )
        }

        composable(
            route = NavRoutes.Foods.edit,
            arguments = listOf(
                navArgument("foodId") { type = NavType.LongType }
            )
        ) { entry ->
            val foodId = entry.arguments!!.getLong("foodId")

            FoodEditorRoute(
                foodId = foodId,
                initialName = null,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                bannerCaptureController = bannerCaptureController
            )
        }

        composable(
            route = NavRoutes.Foods.new,
            arguments = listOf(
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { entry ->
            val initialName = entry.arguments?.getString("name").orEmpty().ifBlank { null }

            FoodEditorRoute(
                foodId = null,
                initialName = initialName,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                bannerCaptureController = bannerCaptureController
            )
        }


        // ------------------------------------------------------------
        // Recipes
        // ------------------------------------------------------------
        composable(route = NavRoutes.Recipes.route) {
            RecipeBuilderScreen(
                editFoodId = null,
                onBack = { navController.popBackStack() },
                onEditFood = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                }
            )
        }

        composable(
            route = NavRoutes.Recipes.builder,
            arguments = listOf(
                navArgument("recipeId") { nullable = true; defaultValue = "" },
                navArgument("editFoodId") { nullable = true; defaultValue = "" }
            )
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId").orEmpty().toLongOrNull()
            val editFoodId = backStackEntry.arguments?.getString("editFoodId").orEmpty().toLongOrNull()

            RecipeBuilderScreen(
                editFoodId = editFoodId,
                recipeId = recipeId,
                onBack = { navController.popBackStack() },
                onEditFood = { foodId -> navController.navigate(NavRoutes.Foods.edit(foodId)) }
            )
        }

        Log.d("NavDbg", "Recipes.route=${NavRoutes.Recipes.route}")
        Log.d("NavDbg", "Recipes.builderPattern=${NavRoutes.Recipes.builder}")
        Log.d("NavDbg", "Recipes.builderSample=${NavRoutes.Recipes.builder(recipeId = 123)}")


    }
}
