package com.example.adobongkangkong.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
                onBack = { navController.popBackStack() },

                // if list rows open "details", wire edit or details depending on your UX:
                onEditFood = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                },

                // If you don’t have recipe edit yet, you can temporarily no-op:
                onEditRecipe = { recipeId ->
                    navController.navigate(route = NavRoutes.Recipes.route)

                    // or navController.navigate(NavRoutes.Recipes.list) if that's your builder entry
                },

                onCreateFood = {
                    // no prefill supported by this screen signature
                    navController.navigate(NavRoutes.Foods.new(prefillName = null))
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
                onDone = { navController.popBackStack() }
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
                onDone = { navController.popBackStack() }
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
                onDone = { navController.popBackStack() }
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
    }
}
