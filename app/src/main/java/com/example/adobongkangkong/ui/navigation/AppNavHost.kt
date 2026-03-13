package com.example.adobongkangkong.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.adobongkangkong.R
import com.example.adobongkangkong.core.util.restartApp
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.feature.camera.BannerOwnerRef
import com.example.adobongkangkong.feature.camera.BannerOwnerType
import com.example.adobongkangkong.ui.backup.BackupScreen
import com.example.adobongkangkong.ui.calendar.CalendarScreen
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.dashboard.DashboardScreen
import com.example.adobongkangkong.ui.daylog.DayLogScreen
import com.example.adobongkangkong.ui.debug.MeowLogScreen
import com.example.adobongkangkong.ui.food.FoodsListScreen
import com.example.adobongkangkong.ui.food.editor.FoodEditorRoute
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import com.example.adobongkangkong.ui.planner.PlannerDayRoute
import com.example.adobongkangkong.ui.recipe.RecipeBuilderScreen
import com.example.adobongkangkong.ui.shopping.ShoppingScreen
import com.example.adobongkangkong.ui.startup.StartupScreen
import com.example.adobongkangkong.ui.templates.MealTemplateEditorActions
import java.time.LocalDate

private const val KEY_FOOD_PICK_FOOD_ID = "food_pick_food_id"
private const val KEY_FOOD_PICK_MODE = "food_pick_mode"
private const val FOOD_PICK_MODE_MERGE = "merge"
private const val KEY_MERGE_PICK_CANONICAL_FOOD_ID = "merge_pick_canonical_food_id"

private const val KEY_TEMPLATE_PICK_TEMPLATE_ID = "template_pick_template_id"
private const val KEY_TEMPLATE_PICK_OVERRIDE_SLOT = "template_pick_override_slot"

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
            val popped = navController.popBackStack(NavRoutes.Foods.list, inclusive = false)

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

            val startDate = runCatching {
                startIso.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: LocalDate.now()
            }.getOrElse { LocalDate.now() }

            ShoppingScreen(
                startDate = startDate,
                onBack = { navController.popBackStack() }
            )
        }

        composable("startup") {
            StartupScreen(
                onDone = {
                    navController.navigate(NavRoutes.Dashboard.dashboard()) {
                        popUpTo("startup") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = NavRoutes.Dashboard.route,
            arguments = listOf(
                navArgument("date") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { entry ->
            val dateIso = entry.arguments?.getString("date").orEmpty()
            val initialDate = dateIso.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }

            DashboardScreen(
                initialDate = initialDate,
                onEditFood = { foodId -> navController.navigate(NavRoutes.Foods.edit(foodId)) },
                onCreateRecipe = { navController.navigate(NavRoutes.Recipes.route) },
                onCreateFood = { prefillName -> navController.navigate(NavRoutes.Foods.new(prefillName)) },
                onOpenFoods = { navController.navigate(NavRoutes.Foods.list) },
                onOpenCalendar = { navController.navigate(NavRoutes.Calendar.route) },
                onOpenDayLog = { date -> navController.navigate(NavRoutes.DayLog.dayLog(date)) },
                onOpenMeowLogs = { navController.navigate(NavRoutes.Debug.meowLogs) },
                onOpenPlanner = { navController.navigate(NavRoutes.Planner.plannerDay(LocalDate.now().toString())) },
                onOpenBackup = { navController.navigate("backup") },
                onCreateFoodWithBarcode = { barcode ->
                    navController.navigate(NavRoutes.Foods.new(prefillName = null, prefillBarcode = barcode))
                },
            )
        }

        composable(NavRoutes.Calendar.route) {
            CalendarScreen(
                onNavigateToDashboard = { date ->
                    navController.navigate(NavRoutes.Dashboard.dashboard(date)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToPlannerDay = { date ->
                    navController.navigate(NavRoutes.Planner.plannerDay(date.toString()))
                },
                onNavigateToDayLog = { date ->
                    navController.navigate(NavRoutes.DayLog.dayLog(date))
                },
                onNavigateToShopping = { startDate ->
                    navController.navigate(NavRoutes.Shopping.shopping(startDate = startDate, days = 7))
                },
                onNavigateToTemplates = { _ ->
                    navController.navigate(NavRoutes.Planner.templates)
                },
                onBack = { navController.popBackStack() }
            )
        }

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
                onBack = { navController.popBackStack() },
                onCreateFood = { prefillName ->
                    navController.navigate(NavRoutes.Foods.new(prefillName = prefillName))
                },
                onCreateFoodWithBarcode = { barcode ->
                    navController.navigate(NavRoutes.Foods.new(prefillName = null, prefillBarcode = barcode))
                },
                onOpenFoodEditor = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                }
            )
        }

        composable(
            route = NavRoutes.QuickAdd.route,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType }
            )
        ) { entry ->
            val dateIso = entry.arguments!!.getString("date")!!
            val date = runCatching { LocalDate.parse(dateIso) }.getOrElse { LocalDate.now() }

            QuickAddBottomSheet(
                onDismiss = { navController.popBackStack() },
                onCreateFood = { prefillName ->
                    navController.navigate(NavRoutes.Foods.new(prefillName = prefillName))
                },
                onCreateFoodWithBarcode = { barcode ->
                    navController.navigate(NavRoutes.Foods.new(prefillName = null, prefillBarcode = barcode))
                },
                onOpenFoodEditor = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                },
                logDate = date
            )
        }

        composable(route = NavRoutes.Foods.list) {
            FoodsListScreen(
                onBack = { navController.popBackStack() },
                onEditFood = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                },
                onEditRecipe = { recipeId ->
                    navController.navigate(NavRoutes.Recipes.builder(editFoodId = recipeId))
                },
                onCreateFood = {
                    navController.navigate(NavRoutes.Foods.new(prefillName = null))
                },
                onCreateRecipe = {
                    navController.navigate(NavRoutes.Recipes.route)
                }
            )
        }

        composable(route = NavRoutes.Foods.pickFood) {
            FoodsListScreen(
                onBack = { navController.popBackStack() },
                onEditFood = { foodId -> navController.navigate(NavRoutes.Foods.edit(foodId)) },
                onEditRecipe = { recipeId -> navController.navigate(NavRoutes.Recipes.builder(editFoodId = recipeId)) },
                onCreateFood = { navController.navigate(NavRoutes.Foods.new(prefillName = null)) },
                onCreateRecipe = { navController.navigate(NavRoutes.Recipes.route) },
                onPickFood = { foodId ->
                    val previousEntry = navController.previousBackStackEntry
                    val pickMode = previousEntry?.savedStateHandle?.get<String>(KEY_FOOD_PICK_MODE)

                    if (pickMode == FOOD_PICK_MODE_MERGE) {
                        previousEntry.savedStateHandle[KEY_MERGE_PICK_CANONICAL_FOOD_ID] = foodId
                        previousEntry.savedStateHandle[KEY_FOOD_PICK_MODE] = null
                    } else {
                        previousEntry?.savedStateHandle?.set(KEY_FOOD_PICK_FOOD_ID, foodId)
                    }

                    navController.popBackStack()
                }
            )
        }

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
                    navController.navigate(NavRoutes.Foods.edit(pickedFoodId, barcode)) {
                        popUpTo(NavRoutes.Foods.pickBarcode) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEditRecipe = { },
                onCreateFood = { },
                onCreateRecipe = { }
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
            Log.d(
                "Meow",
                "NAV -> Foods.edit destination. foodId=$foodId initialBarcode=$initialBarcode route=${entry.destination.route}"
            )

            val pickedMergeFoodId by entry.savedStateHandle
                .getStateFlow<Long?>(KEY_MERGE_PICK_CANONICAL_FOOD_ID, null)
                .collectAsState()

            FoodEditorRoute(
                foodId = foodId,
                initialName = null,
                initialBarcode = initialBarcode,
                mergePickedFoodId = pickedMergeFoodId,
                onBack = { navController.popBackStack() },
                onDone = { closeFoodEditor() },
                onAssignBarcodeToExisting = { barcode ->
                    navController.navigate(NavRoutes.Foods.pickBarcode(barcode))
                },
                onOpenMergeFoodPicker = {
                    entry.savedStateHandle[KEY_FOOD_PICK_MODE] = FOOD_PICK_MODE_MERGE
                    navController.navigate(NavRoutes.Foods.pickFood)
                },
                onMergePickedConsumed = {
                    entry.savedStateHandle[KEY_MERGE_PICK_CANONICAL_FOOD_ID] = null
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
                onOpenFoodEditor = { targetFoodId ->
                    navController.navigate(NavRoutes.Foods.edit(targetFoodId))
                },
            )
        }

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
        ) { backStackEntry ->
            val handle = backStackEntry.savedStateHandle

            val pickedTemplateIdFlow =
                handle.getStateFlow(KEY_TEMPLATE_PICK_TEMPLATE_ID, 0L)

            val pickedOverrideSlotNameFlow =
                handle.getStateFlow(KEY_TEMPLATE_PICK_OVERRIDE_SLOT, "")

            var pendingTemplatePick by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<Pair<Long, MealSlot?>?>(null)
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                pickedTemplateIdFlow.collect { templateId ->
                    if (templateId > 0L) {
                        handle[KEY_TEMPLATE_PICK_TEMPLATE_ID] = 0L

                        val slotName = pickedOverrideSlotNameFlow.value
                        val overrideSlot = runCatching {
                            slotName.takeIf { it.isNotBlank() }?.let {
                                MealSlot.valueOf(it)
                            }
                        }.getOrNull()

                        pendingTemplatePick = templateId to overrideSlot
                    }
                }
            }

            val dateIso = backStackEntry.arguments?.getString("dateIso")
                ?.takeIf { it.isNotBlank() }
                ?: LocalDate.now().toString()

            val date = runCatching { LocalDate.parse(dateIso) }
                .getOrElse { LocalDate.now() }

            PlannerDayRoute(
                date = date,
                onBack = { navController.popBackStack() },
                onPickDate = { picked ->
                    navController.navigate(NavRoutes.Planner.plannerDay(picked.toString()))
                },
                onOpenPlannedMealEditor = { mealId ->
                    navController.navigate(NavRoutes.Planner.plannedMealEditor(mealId))
                },
                onOpenNewPlannedMealEditor = { pickedDateIso, slot, templateId ->
                    navController.navigate(
                        NavRoutes.Planner.plannedMealEditorNew(
                            dateIso = pickedDateIso,
                            slot = slot.name,
                            templateId = templateId
                        )
                    )
                },
                onOpenTemplatePicker = { slot ->
                    navController.navigate(
                        NavRoutes.Planner.templatePicker(
                            dateIso = date.toString(),
                            slot = slot?.name.orEmpty()
                        )
                    )
                },
                onCreateFood = { prefillName ->
                    navController.navigate(NavRoutes.Foods.new(prefillName = prefillName))
                },
                onCreateFoodWithBarcode = { barcode ->
                    navController.navigate(NavRoutes.Foods.new(prefillName = null, prefillBarcode = barcode))
                },
                onOpenFoodEditor = { foodId ->
                    navController.navigate(NavRoutes.Foods.edit(foodId))
                },
                templatePick = pendingTemplatePick,
                onTemplatePickConsumed = { pendingTemplatePick = null }
            )
        }

        composable(
            route = NavRoutes.Planner.templatePicker,
            arguments = listOf(
                navArgument("dateIso") { type = NavType.StringType },
                navArgument("slot") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val dateIso = backStackEntry.arguments?.getString("dateIso").orEmpty()
                .ifBlank { LocalDate.now().toString() }

            val slotName = backStackEntry.arguments?.getString("slot").orEmpty()
            val overrideSlot = runCatching {
                slotName.takeIf { it.isNotBlank() }?.let {
                    MealSlot.valueOf(it)
                }
            }.getOrNull()

            com.example.adobongkangkong.ui.planner.templatepicker.MealTemplatePickerRoute(
                dateIso = dateIso,
                initialSlotContext = overrideSlot,
                onBack = { navController.popBackStack() },
                onPicked = { templateId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_TEMPLATE_PICK_TEMPLATE_ID, templateId)

                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_TEMPLATE_PICK_OVERRIDE_SLOT, overrideSlot?.name.orEmpty())

                    navController.popBackStack()
                }
            )
        }

        composable("backup") {
            val context = androidx.compose.ui.platform.LocalContext.current
            BackupScreen(
                onBack = { navController.popBackStack() },
                onRequestRestartApp = {
                    restartApp(context)
                }
            )
        }

        composable(NavRoutes.Planner.templates) {
            com.example.adobongkangkong.ui.templates.MealTemplateListRoute(
                onBack = { navController.popBackStack() },
                onOpenTemplate = { templateId ->
                    navController.navigate(NavRoutes.Planner.templateEditor(templateId))
                },
                onCreateTemplate = {
                    navController.navigate(NavRoutes.Planner.templateEditorNew)
                }
            )
        }

        composable(
            route = NavRoutes.Planner.templateEditor,
            arguments = listOf(navArgument("templateId") { type = NavType.LongType })
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getLong("templateId") ?: 0L
            val context = androidx.compose.ui.platform.LocalContext.current
            val bannerStorage = com.example.adobongkangkong.feature.camera.FoodImageStorage(context)

            val vm: com.example.adobongkangkong.ui.templates.MealTemplateEditorViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()

            androidx.compose.runtime.LaunchedEffect(templateId) {
                if (templateId > 0L) vm.setTemplateId(templateId)
            }

            androidx.compose.runtime.LaunchedEffect(vm) {
                vm.effects.collect { effect ->
                    when (effect) {
                        is com.example.adobongkangkong.ui.templates.MealTemplateEditorViewModel.Effect.OpenTemplate -> {
                            navController.popBackStack()
                            navController.navigate(NavRoutes.Planner.templateEditor(effect.templateId))
                        }
                        is com.example.adobongkangkong.ui.templates.MealTemplateEditorViewModel.Effect.Deleted -> {
                            bannerStorage.deleteBanner(BannerOwnerRef(BannerOwnerType.TEMPLATE, effect.templateId))
                            navController.popBackStack()
                        }
                    }
                }
            }

            val pickedFoodId = backStackEntry.savedStateHandle.getStateFlow<Long?>(KEY_FOOD_PICK_FOOD_ID, null)
            androidx.compose.runtime.LaunchedEffect(pickedFoodId) {
                pickedFoodId.collect { id ->
                    if (id != null && id > 0L) {
                        vm.addFood(id)
                        backStackEntry.savedStateHandle[KEY_FOOD_PICK_FOOD_ID] = null
                    }
                }
            }

            val templateEditorState = vm.state.collectAsState().value

            com.example.adobongkangkong.ui.meal.editor.MealEditorScreen(
                contract = vm,
                onBack = { navController.popBackStack() },
                onRequestAddFood = {
                    navController.navigate(NavRoutes.Foods.pickFood)
                },
                bannerCaptureController = bannerCaptureController,
                bannerOwner = BannerOwnerRef(BannerOwnerType.TEMPLATE, templateId),
                bannerRefreshTick = bannerRefreshTick,
                bannerPlaceholderResId = R.drawable.recipe_banner,
                bannerChangeLabel = "Change banner",
                extraActions = {
                    MealTemplateEditorActions(
                        enabled = !templateEditorState.isSaving,
                        onDuplicate = vm::duplicateTemplate,
                        onDeleteConfirmed = vm::deleteTemplate
                    )
                }
            )
        }

        composable(route = NavRoutes.Planner.templateEditorNew) { backStackEntry ->
            val vm: com.example.adobongkangkong.ui.templates.MealTemplateEditorViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()

            val pickedFoodId = backStackEntry.savedStateHandle.getStateFlow<Long?>(KEY_FOOD_PICK_FOOD_ID, null)
            androidx.compose.runtime.LaunchedEffect(pickedFoodId) {
                pickedFoodId.collect { id ->
                    if (id != null && id > 0L) {
                        vm.addFood(id)
                        backStackEntry.savedStateHandle[KEY_FOOD_PICK_FOOD_ID] = null
                    }
                }
            }

            com.example.adobongkangkong.ui.meal.editor.MealEditorScreen(
                contract = vm,
                onBack = { navController.popBackStack() },
                onRequestAddFood = {
                    navController.navigate(NavRoutes.Foods.pickFood)
                },
                bannerCaptureController = bannerCaptureController,
                bannerOwner = vm.state.collectAsState().value.mealId?.let {
                    BannerOwnerRef(BannerOwnerType.TEMPLATE, it)
                },
                bannerRefreshTick = bannerRefreshTick,
                bannerPlaceholderResId = R.drawable.recipe_banner,
                bannerChangeLabel = "Change banner"
            )
        }

        composable(
            route = NavRoutes.Planner.plannedMealEditor,
            arguments = listOf(navArgument("mealId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mealId = backStackEntry.arguments?.getLong("mealId") ?: 0L

            val vm: com.example.adobongkangkong.ui.planner.PlannedMealEditorViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()

            androidx.compose.runtime.LaunchedEffect(mealId) {
                if (mealId > 0L) vm.setMealId(mealId)
            }

            androidx.compose.runtime.LaunchedEffect(vm) {
                vm.effects.collect { effect ->
                    when (effect) {
                        is com.example.adobongkangkong.ui.planner.PlannedMealEditorViewModel.Effect.Saved -> {
                            navController.popBackStack()
                        }
                    }
                }
            }

            val pickedFoodId = backStackEntry.savedStateHandle.getStateFlow<Long?>(KEY_FOOD_PICK_FOOD_ID, null)
            androidx.compose.runtime.LaunchedEffect(pickedFoodId) {
                pickedFoodId.collect { id ->
                    if (id != null && id > 0L) {
                        vm.addFood(id)
                        backStackEntry.savedStateHandle[KEY_FOOD_PICK_FOOD_ID] = null
                    }
                }
            }

            com.example.adobongkangkong.ui.meal.editor.MealEditorScreen(
                contract = vm,
                onBack = { navController.popBackStack() },
                onRequestAddFood = {
                    navController.navigate(NavRoutes.Foods.pickFood)
                }
            )
        }

        composable(
            route = NavRoutes.Planner.plannedMealEditorNew,
            arguments = listOf(
                navArgument("dateIso") { type = NavType.StringType },
                navArgument("slot") { type = NavType.StringType },
                navArgument("templateId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val dateIso = backStackEntry.arguments?.getString("dateIso").orEmpty()
            val slotName = backStackEntry.arguments?.getString("slot").orEmpty()
            val templateIdArg = backStackEntry.arguments?.getLong("templateId") ?: -1L
            val templateId = templateIdArg.takeIf { it > 0L }

            val vm: com.example.adobongkangkong.ui.planner.PlannedMealEditorViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()

            androidx.compose.runtime.LaunchedEffect(vm) {
                vm.effects.collect { effect ->
                    when (effect) {
                        is com.example.adobongkangkong.ui.planner.PlannedMealEditorViewModel.Effect.Saved -> {
                            navController.popBackStack()
                        }
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(dateIso, slotName, templateId) {
                if (dateIso.isNotBlank() && slotName.isNotBlank()) {
                    runCatching {
                        MealSlot.valueOf(slotName)
                    }.getOrNull()?.let { slot ->
                        vm.startNewPlannedMeal(
                            dateIso = dateIso,
                            slot = slot,
                            templateId = templateId
                        )
                    }
                }
            }

            val pickedFoodId = backStackEntry.savedStateHandle.getStateFlow<Long?>(KEY_FOOD_PICK_FOOD_ID, null)
            androidx.compose.runtime.LaunchedEffect(pickedFoodId) {
                pickedFoodId.collect { id ->
                    if (id != null && id > 0L) {
                        vm.addFood(id)
                        backStackEntry.savedStateHandle[KEY_FOOD_PICK_FOOD_ID] = null
                    }
                }
            }

            com.example.adobongkangkong.ui.meal.editor.MealEditorScreen(
                contract = vm,
                onBack = { navController.popBackStack() },
                onRequestAddFood = {
                    navController.navigate(NavRoutes.Foods.pickFood)
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