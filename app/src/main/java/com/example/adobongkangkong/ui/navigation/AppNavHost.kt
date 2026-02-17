package com.example.adobongkangkong.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.adobongkangkong.ui.calendar.CalendarScreen
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.dashboard.DashboardScreen
import com.example.adobongkangkong.ui.daylog.DayLogScreen
import com.example.adobongkangkong.ui.debug.MeowLogScreen
import com.example.adobongkangkong.ui.food.FoodsListScreen
import com.example.adobongkangkong.ui.food.editor.FoodEditorRoute
import com.example.adobongkangkong.ui.planner.PlannerDayRoute
import com.example.adobongkangkong.ui.recipe.RecipeBuilderScreen
import com.example.adobongkangkong.ui.shopping.ShoppingScreen
import com.example.adobongkangkong.ui.startup.StartupScreen
import java.time.LocalDate

@Composable
fun AppNavHost(
    navController: NavHostController,
    bannerCaptureController: BannerCaptureController,
    bannerRefreshTick: Int,
    modifier: Modifier = Modifier,
    startDestination: String = "startup"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        fun closeFoodEditor() {
            // If Foods.list exists in back stack, go there
            val popped = navController.popBackStack(NavRoutes.Foods.list, inclusive = false)

            // If not (e.g. opened editor from Dashboard), navigate to list as the landing page
            if (!popped) {
                navController.navigate(NavRoutes.Foods.list) {
                    launchSingleTop = true
                }
            }
        }

        composable(
            route = NavRoutes.Shopping.route,
            arguments = listOf(
                navArgument("start") { type = NavType.StringType; nullable = true; defaultValue = "" },
                navArgument("days") { type = NavType.IntType; defaultValue = 7 }
            )
        ) { entry ->
            val startIso = entry.arguments?.getString("start").orEmpty()
            val days = entry.arguments?.getInt("days") ?: 7

            val startDate = runCatching {
                startIso.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: LocalDate.now()
            }.getOrElse { LocalDate.now() }
            ShoppingScreen(
                startDate = startDate,
                days = days,
                onBack = { navController.popBackStack() }
            )
        }

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
                onOpenCalendar = {
                    navController.navigate(NavRoutes.Calendar.route)
                },
                onOpenDayLog = { date: LocalDate ->
                    navController.navigate(NavRoutes.DayLog.dayLog(date))
                },
                onOpenMeowLogs = {
                    navController.navigate(NavRoutes.Debug.meowLogs)
                },
                onOpenPlanner = {
                    navController.navigate(NavRoutes.Planner.plannerDay(LocalDate.now().toString()))
                },
            )
        }

        // ------------------------------------------------------------
        // Heatmap
        // ------------------------------------------------------------

        composable(NavRoutes.Calendar.route) {
            CalendarScreen(
                onNavigateToPlannerDay = { date ->
                    navController.navigate(NavRoutes.Planner.plannerDay(date.toString()))
                },
                onNavigateToDayLog = { date ->
                    navController.navigate(NavRoutes.DayLog.dayLog(date))
                },
                onNavigateToShopping = { startDate ->
                    navController.navigate(NavRoutes.Shopping.shopping(startDate = startDate, days = 7))
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

        // ------------------------------------------------------------
        // Foods — assign barcode to existing food
        // ------------------------------------------------------------

        composable(
            route = NavRoutes.Foods.pickBarcode,
            arguments = listOf(
                navArgument("barcode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { entry ->
            val barcode = entry.arguments?.getString("barcode").orEmpty()

            FoodsListScreen(
                onBack = { navController.popBackStack() },
                onEditFood = { pickedFoodId ->
                    // Go straight to the editor for that food and carry the barcode as a query param.
                    navController.navigate(NavRoutes.Foods.edit(pickedFoodId, barcode)) {
                        // Remove the picker from back stack so back goes where it used to.
                        popUpTo(NavRoutes.Foods.pickBarcode) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEditRecipe = { /* no-op */ },
                onCreateFood = { /* no-op */ },
                onCreateRecipe = { /* no-op */ }
            )
        }
        composable(
            route = NavRoutes.Foods.edit,
            arguments = listOf(
                navArgument("foodId") { type = NavType.LongType },
                navArgument("barcode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { entry ->
            val foodId = entry.arguments!!.getLong("foodId")
            val initialBarcode = entry.arguments?.getString("barcode").orEmpty().ifBlank { null }
            // In Foods.edit composable (right before FoodEditorRoute call)
            Log.d("Meow", "NAV -> Foods.edit destination. foodId=$foodId initialBarcode=$initialBarcode route=${entry.destination.route}")

            FoodEditorRoute(
                foodId = foodId,
                initialName = null,
                initialBarcode = initialBarcode,
                onBack = { navController.popBackStack() },
                onDone = { closeFoodEditor() },
                onAssignBarcodeToExisting = { barcode ->
                    navController.navigate(NavRoutes.Foods.pickBarcode(barcode))
                },
                bannerCaptureController = bannerCaptureController,
                bannerRefreshTick = bannerRefreshTick,
                onOpenFoodEditor = { targetFoodId ->
                    navController.navigate(NavRoutes.Foods.edit(targetFoodId))
                }
            )
        }

        composable(
            route = NavRoutes.Foods.new,
            arguments = listOf(
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("barcode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { entry ->
            val initialName = entry.arguments?.getString("name").orEmpty().ifBlank { null }
            val initialBarcode = entry.arguments?.getString("barcode").orEmpty().ifBlank { null }
            // In Foods.new composable (right before FoodEditorRoute call)
            Log.d("Meow", "NAV -> Foods.new destination. initialName=$initialName initialBarcode=$initialBarcode route=${entry.destination.route}")
            FoodEditorRoute(
                foodId = null,
                initialName = initialName,
                initialBarcode = initialBarcode,
                onBack = { navController.popBackStack() },
                onDone = { closeFoodEditor() },
                onAssignBarcodeToExisting = { barcode ->
                    navController.navigate(NavRoutes.Foods.pickBarcode(barcode))
                },
                bannerCaptureController = bannerCaptureController,
                bannerRefreshTick = bannerRefreshTick,
                // ✅ add this
                onOpenFoodEditor = { targetFoodId ->
                    navController.navigate(NavRoutes.Foods.edit(targetFoodId))
                },
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
                },
                bannerRefreshTick = bannerRefreshTick,
                bannerCaptureController = bannerCaptureController
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
                onEditFood = { foodId -> navController.navigate(NavRoutes.Foods.edit(foodId)) },
                bannerRefreshTick = bannerRefreshTick,
                bannerCaptureController = bannerCaptureController
            )
        }

        composable(route = NavRoutes.Debug.meowLogs) {
            MeowLogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.Planner.plannerDay,
            arguments = listOf(navArgument("dateIso") { type = NavType.StringType })
        ) {

            backStackEntry ->
            val dateIso = backStackEntry.arguments?.getString("dateIso")
                ?.takeIf { it.isNotBlank() }
                ?: LocalDate.now().toString()

            val date = runCatching { LocalDate.parse(dateIso) }
                .getOrElse { LocalDate.now() }

            PlannerDayRoute(
                date = date,
                onBack = { navController.popBackStack() },
                onPickDate = { /* TODO: date picker later */ },
                onNavigateToDate = { newDate ->
                    navController.navigate(NavRoutes.Planner.plannerDay(newDate.toString())) {
                        launchSingleTop = true
                    }
                }
            )
        }

        Log.d("NavDbg", "Recipes.route=${NavRoutes.Recipes.route}")
        Log.d("NavDbg", "Recipes.builderPattern=${NavRoutes.Recipes.builder}")
        Log.d("NavDbg", "Recipes.builderSample=${NavRoutes.Recipes.builder(recipeId = 123)}")
    }
}
/**
 * FOR-FUTURE-ME — AppNavHost and global controller plumbing
 *
 * This NavHost is NOT allowed to create global controllers.
 * It only *forwards* instances created at MainScreen level.
 *
 * BannerCaptureController RULE (critical):
 * - BannerCaptureController must be created exactly once in MainScreen.
 * - AppNavHost must receive it as a parameter and pass it through unchanged.
 * - DO NOT call BannerCaptureController() or remember { BannerCaptureController() } here.
 *
 * Why this matters:
 * - BannerCaptureHost observes the controller instance from MainScreen.
 * - If a destination uses a different instance, controller.open() will silently do nothing.
 * - This failure mode has no crash, no log, no warning — just a dead button.
 *
 * Mental model:
 * - Controllers are identity-sensitive.
 * - If UI doesn’t appear, suspect instance mismatch before suspecting logic.
 */
