// RecipeBuilderScreen.kt
package com.example.adobongkangkong.ui.recipe

import android.R.attr.maxWidth
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.ui.food.SelectedFoodPanel
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingBottomSheet
import com.example.adobongkangkong.ui.common.food.GoalFlagsSection
import kotlinx.coroutines.delay

/**
 * Recipe builder / editor screen.
 *
 * ## Important architecture points
 *
 * ### Point-of-use enforcement (data integrity)
 * The importer may allow foods with missing grams-per-serving (nullable). That’s OK.
 * We enforce correctness **right when the user tries to use the food by servings**
 * (adding an ingredient in the recipe builder).
 *
 * The ViewModel signals this condition by setting [RecipeBuilderState.blockingSheet].
 * The UI displays a modal blocking sheet that offers a primary action to edit the food.
 *
 * ### Navigation request pattern (Compose-safe)
 * The ViewModel does NOT navigate directly. Instead it sets a one-shot
 * [RecipeBuilderState.navigateToEditFoodId]. The UI consumes it via [LaunchedEffect],
 * calls [onEditFood], then tells the VM the navigation has been handled so it won’t re-trigger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBuilderScreen(
    recipeId: Long? = null,
    editFoodId: Long?,
    onBack: () -> Unit,
    onEditFood: (Long) -> Unit,
    bannerRefreshTick: Int,
    bannerCaptureController: BannerCaptureController
) {
    val vm: RecipeBuilderViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    val ingredientTotalGrams by vm.ingredientTotalGrams.collectAsState()

    val listState = rememberLazyListState()

    val showExitDialog = rememberSaveable { mutableStateOf(false) }

    fun requestExit() {
        if (state.hasUnsavedChanges && !state.isSaving) {
            showExitDialog.value = true
        } else {
            onBack()
        }
    }

    BackHandler { requestExit() }
    // --------------------------------------------------------------------------
    // Deterministic scroll: when a food is picked, bring the "Picked" section into view.
    LaunchedEffect(state.pickedFood?.id) {
        if (state.pickedFood != null) {
            // Wait for LazyColumn to insert the conditional "Picked" item.
            delay(16)

            // Compute the index of the "Picked" item based on current list structure.
            var idx = 0
            idx += 1 // name
            idx += 1 // servings yield
            if (state.errorMessage != null) idx += 1
            idx += 1 // spacer
            idx += 1 // "Add ingredient" title
            idx += 1 // search field
            if (state.results.isNotEmpty()) idx += 1 // results list

            listState.animateScrollToItem(idx)
        }
    }

    // --- Edit-mode load -------------------------------------------------------
    LaunchedEffect(editFoodId) {
        vm.loadForEdit(editFoodId)
    }

    // --- One-shot navigation request -----------------------------------------
    LaunchedEffect(state.navigateToEditFoodId) {
        val id = state.navigateToEditFoodId ?: return@LaunchedEffect
        onEditFood(id)
        vm.onEditFoodNavigationHandled()
    }

    // --- Blocking sheet UI ----------------------------------------------------
    val blockingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    state.blockingSheet?.let { sheetModel ->
        ModalBottomSheet(
            onDismissRequest = { vm.dismissBlockingSheet() },
            sheetState = blockingSheetState
        ) {
            BlockingBottomSheet(
                model = sheetModel,
                onDismiss = { vm.dismissBlockingSheet() }
            )
        }
    }

    val canSave = !state.isSaving

    // --- Screen UI ------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editFoodId == null) "New Recipe" else "Edit Recipe") },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Sticky save so it's always reachable when font is large / screen is short.
            Button(
                onClick = { vm.save(onDone = onBack) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val context = LocalContext.current
                val bannerOwnerId =editFoodId
                val canCaptureBanner = bannerOwnerId != null

                val bannerFile = remember(bannerOwnerId) {
                        if (editFoodId == null) null else FoodImageStorage(context).bannerJpegFile(editFoodId)
                    }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            enabled = canCaptureBanner,
                            onClick = { bannerCaptureController.open(bannerOwnerId!!) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Change banner",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (canCaptureBanner) {
                        Text(
                            text = "Save first to enable banner image.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onNameChange,
                    label = { Text("Recipe name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = state.servingsYield.toString(),
                    onValueChange = { raw -> raw.toDoubleOrNull()?.let(vm::onYieldChange) },
                    label = { Text("Servings yield") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                val configuration = LocalConfiguration.current
                val isTablet = configuration.screenWidthDp >= 600
                GoalFlagsSection(
                    favorite = state.favorite,
                    eatMore = state.eatMore,
                    limit = state.limit,
                    onToggleFavorite = vm::onFavoriteChange,
                    onToggleEatMore = vm::onEatMoreChange,
                    onToggleLimit = vm::onLimitChange,
                    isTablet = isTablet
                )
            }
            state.errorMessage?.let { err ->
                item {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = vm::clearError) { Text("Dismiss") }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item { Text("Add ingredient", style = MaterialTheme.typography.titleMedium) }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = { Text("Search foods") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (state.results.isNotEmpty()) {
                item {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.results.take(8).forEach { food ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    food.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                TextButton(onClick = { vm.pickFood(food) }) { Text("Pick") }
                            }
                        }
                    }
                }
            }

            state.pickedFood?.let { pickedFood ->
                item {
                    // ------------------------------------------------------------------
                    // IDENTICAL steps to QuickAddBottomSheet (SelectedFoodPanel)
                    // ------------------------------------------------------------------
                    val gramsPerServingUnit = pickedFood.gramsPerServingUnitResolved()
                    var inputUnit by rememberSaveable(pickedFood.id) { mutableStateOf(ServingUnit.G) }
                    var inputAmount by rememberSaveable(pickedFood.id) { mutableStateOf<Double?>(null) }

                    var servingUnitAmount by rememberSaveable(pickedFood.id) {
                        mutableStateOf(state.pickedServings * pickedFood.servingSize)
                    }

                    var gramsAmount by rememberSaveable(pickedFood.id) {
                        mutableStateOf(gramsPerServingUnit?.let { g -> state.pickedServings * g })
                    }

                    // Keep derived fields synced whenever canonical servings changes (same behavior as QuickAdd VM).
                    LaunchedEffect(state.pickedServings, pickedFood.id, gramsPerServingUnit) {
                        servingUnitAmount = state.pickedServings * pickedFood.servingSize
                        gramsAmount = gramsPerServingUnit?.let { g -> state.pickedServings * g }
                    }

                    SelectedFoodPanel(
                        food = pickedFood,
                        servings = state.pickedServings,
                        servingUnitAmount = servingUnitAmount,
                        gramsAmount = gramsAmount,
                        inputUnit = inputUnit,
                        inputAmount = inputAmount,
                        errorMessage = state.errorMessage,
                        onBack = vm::clearSelection,
                        onServingsChanged = { s ->
                            vm.onServingsChanged(s)
                        },
                        onServingUnitAmountChanged = { amt ->
                            servingUnitAmount = amt
                            vm.onServingUnitAmountChanged(amt)
                        },
                        onGramsChanged = { g ->
                            gramsAmount = g
                            vm.onGramsChanged(g)
                        },
                        onInputUnitChanged = { u ->
                            inputUnit = u
                            vm.onInputUnitChanged(u)
                        },
                        onInputAmountChanged = { amt ->
                            inputAmount = amt
                            vm.onInputAmountChanged(amt)
                        },
                        onPackage = { mult ->
                            vm.onPackageClicked(mult)
                        },
                        onEditFoodInEditor = { onEditFood(pickedFood.id) },
                        primaryButtonLabel = "Add ingredient",
                        onPrimaryAction = vm::addPickedIngredient
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Ingredients", style = MaterialTheme.typography.titleMedium) }

            item {
                if (state.ingredients.isEmpty()) {
                    Text("No ingredients yet.")
                } else {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.ingredients.forEachIndexed { index, ing ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val unitLabel = ing.servingUnitLabel?.trim().orEmpty()
                                val amountText = if (unitLabel.isNotBlank()) {
                                    "${"%,.2f".format(ing.servings)} $unitLabel"
                                } else {
                                    "${"%,.2f".format(ing.servings)} servings"
                                }
                                val gramsText = ing.grams?.let { g -> " (≈ ${"%,.1f".format(g)} g)" }.orEmpty()

                                // NEW: compute entered-as string (only if present)
                                val enteredUnit = ing.enteredUnitLabel?.trim().orEmpty()
                                val enteredAs = if (ing.enteredAmount != null && enteredUnit.isNotBlank()) {
                                    "Entered as ${"%,.2f".format(ing.enteredAmount)} $enteredUnit"
                                } else null

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "${ing.foodName} • $amountText$gramsText",
                                        maxLines = 2
                                    )

                                    if (enteredAs != null) {
                                        Text(
                                            enteredAs,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }

                                IconButton(onClick = { vm.removeIngredientAt(index) }) {
                                    Icon(
                                        painterResource(id = R.drawable.trash),
                                        contentDescription = "Remove"
                                    )
                                }
                            }
                        }

                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Preview", style = MaterialTheme.typography.titleMedium) }
            val noDivZero = state.servingsYield > 0
            item {
                val servings = state.servingsYield
                val noDivZero = servings > 0

                fun fmt0(v: Double?) = if (v == null) "—" else "%,.0f".format(v)
                fun fmt1(v: Double?) = if (v == null) "—" else "%,.1f".format(v)
                fun perServing(total: Double): Double? =
                    if (noDivZero) total / servings else null

                val numberColWidth = 88.dp

                Column {

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "",
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "batch",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(numberColWidth)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "serving",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(numberColWidth)
                        )
                    }

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    @Composable
                    fun RowItem(label: String, batch: String, serving: String) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                batch,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(numberColWidth)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                serving,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(numberColWidth)
                            )
                        }
                    }

                    RowItem(
                        "Ingredient weight (g)",
                        fmt0(ingredientTotalGrams),
                        fmt0(perServing(ingredientTotalGrams))
                    )

                    RowItem(
                        "Calories (kcal)",
                        fmt0(state.preview.totalCalories),
                        fmt0(perServing(state.preview.totalCalories))
                    )

                    RowItem(
                        "Protein (g)",
                        fmt1(state.preview.totalProteinG),
                        fmt1(perServing(state.preview.totalProteinG))
                    )

                    RowItem(
                        "Carbs (g)",
                        fmt1(state.preview.totalCarbsG),
                        fmt1(perServing(state.preview.totalCarbsG))
                    )

                    RowItem(
                        "Fat (g)",
                        fmt1(state.preview.totalFatG),
                        fmt1(perServing(state.preview.totalFatG))
                    )
                }
            }



            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showExitDialog.value) {
        AlertDialog(
            onDismissRequest = { showExitDialog.value = false },
            title = { Text("Unsaved changes") },
            text = { Text("What would you like to do with your changes?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog.value = false
                        vm.save(onDone = onBack)
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(
                        onClick = {
                            showExitDialog.value = false
                            onBack()
                        }
                    ) { Text("Discard") }

                    TextButton(
                        onClick = { showExitDialog.value = false }
                    ) { Text("Cancel") }
                }
            }
        )
    }

}

