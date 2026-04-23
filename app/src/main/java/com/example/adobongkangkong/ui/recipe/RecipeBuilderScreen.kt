package com.example.adobongkangkong.ui.recipe

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.transfer.RecipeBundleDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleFoodDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleFoodNutrientDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleIngredientDto
import com.example.adobongkangkong.domain.transfer.RecipeBundleRecipeDto
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import com.example.adobongkangkong.ui.common.QuantityDisplayFormatter
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingBottomSheet
import com.example.adobongkangkong.ui.common.food.FoodBannerCardBackground
import com.example.adobongkangkong.ui.common.food.GoalFlagsSection
import com.example.adobongkangkong.ui.food.SelectedFoodPanel
import com.example.adobongkangkong.ui.food.editor.NutrientRowUi
import com.example.adobongkangkong.ui.theme.AppIconSize
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBuilderScreen(
    recipeId: Long? = null,
    editFoodId: Long?,
    onBack: () -> Unit,
    onEditFood: (Long) -> Unit,
    bannerRefreshTick: Int,
    bannerCaptureController: BannerCaptureController,
    onDeleteRecipe: (() -> Unit)? = null
) {
    val vm: RecipeBuilderViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val shareBundle by vm.shareRecipeBundleFlow.collectAsState()

    val ingredientTotalGrams by vm.ingredientTotalGrams.collectAsState()

    val context = LocalContext.current

    val showExitDialog = rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showMockImportPreviewDialog by rememberSaveable { mutableStateOf(false) }
    var showActionsMenu by rememberSaveable { mutableStateOf(false) }

    fun requestExit() {
        if (state.hasUnsavedChanges && !state.isSaving) {
            showExitDialog.value = true
        } else {
            onBack()
        }
    }

    BackHandler { requestExit() }

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val listState = rememberLazyListState()
    val bringAddIntoView = remember { BringIntoViewRequester() }
    rememberCoroutineScope()

    LaunchedEffect(state.pickedFood?.id) {
        if (state.pickedFood != null) {
            delay(16)
            var idx = 0
            idx += 1
            idx += 1
            if (state.errorMessage != null) idx += 1
            idx += 1
            idx += 1
            idx += 1
            if (state.results.isNotEmpty()) idx += 1

            delay(16)
            listState.animateScrollBy(with(density) { 220.dp.toPx() })

            if (imeBottomPx > 0) {
                delay(16)
                listState.animateScrollBy(with(density) { 180.dp.toPx() })
            }
            delay(32)
            bringAddIntoView.bringIntoView()
        }
    }

    LaunchedEffect(editFoodId) {
        vm.loadForEdit(editFoodId)
    }

    LaunchedEffect(state.navigateToEditFoodId) {
        val id = state.navigateToEditFoodId ?: return@LaunchedEffect
        onEditFood(id)
        vm.onEditFoodNavigationHandled()
    }

    LaunchedEffect(shareBundle) {
        val bundle = shareBundle ?: return@LaunchedEffect
        try {
            val exportFile = writeRecipeBundleToCache(
                cacheDir = context.cacheDir,
                bundle = bundle
            )

            val exportUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "Recipe: ${bundle.recipe.name}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is an AdobongKangkong recipe bundle.\n\nOpen the attachment in AdobongKangkong on another device to import it."
                )
                putExtra(Intent.EXTRA_STREAM, exportUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("recipe_bundle", exportUri)
            }

            val chooser = Intent.createChooser(emailIntent, "Email recipe")
            context.startActivity(chooser)
        } catch (t: Throwable) {
            val message = when (t) {
                is ActivityNotFoundException -> "No email/share app available."
                is IOException -> t.message ?: "Failed to write recipe attachment."
                else -> t.message ?: "Failed to prepare recipe email."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } finally {
            vm.onShareRecipeHandled()
        }
    }

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
    val canDeleteRecipe = editFoodId != null && onDeleteRecipe != null

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
                },
                actions = {
                    Box {
                        IconButton(onClick = { showActionsMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.settings),
                                contentDescription = "More actions"
                            )
                        }

                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Email recipe") },
                                onClick = {
                                    showActionsMenu = false
                                    vm.onEmailRecipeClicked()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open imported") },
                                onClick = {
                                    showActionsMenu = false
                                    showMockImportPreviewDialog = true
                                }
                            )

                            if (canDeleteRecipe) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete recipe") },
                                    onClick = {
                                        showActionsMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
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
                val bannerOwnerId = editFoodId
                val canCaptureBanner = bannerOwnerId != null

                val bannerFile = remember(bannerOwnerId) {
                    if (editFoodId == null) null else FoodImageStorage(context).bannerJpegFile(editFoodId)
                }

                val bannerBitmapState = if (canCaptureBanner) {
                    val file = bannerFile!!
                    produceState<android.graphics.Bitmap?>(
                        initialValue = null,
                        key1 = bannerOwnerId,
                        key2 = bannerRefreshTick
                    ) {
                        value = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                    }
                } else {
                    null
                }

                val bmp = bannerBitmapState?.value

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 1f)
                        .clipToBounds()
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            alignment = Alignment.Center,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.FillWidth
                        )

                        IconButton(
                            onClick = { bannerCaptureController.open(bannerOwnerId!!) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_camera),
                                contentDescription = "${editFoodId?.let { "Change" } ?: "Assign"} banner"
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(R.drawable.recipe_banner),
                            contentDescription = null,
                            alignment = Alignment.Center,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
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
                                text = "${editFoodId?.let { "Change" } ?: "Assign"} banner",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (!canCaptureBanner) {
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

            fun Double.cleanRecipeYield(): String =
                if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

            item {
                var servingsYieldText by rememberSaveable(editFoodId) {
                    mutableStateOf(state.servingsYield.cleanRecipeYield())
                }
                var servingsYieldFocused by remember { mutableStateOf(false) }

                LaunchedEffect(state.servingsYield, servingsYieldFocused) {
                    if (!servingsYieldFocused) {
                        servingsYieldText = state.servingsYield.cleanRecipeYield()
                    }
                }

                OutlinedTextField(
                    value = servingsYieldText,
                    onValueChange = { raw ->
                        servingsYieldText = raw
                        raw.toDoubleOrNull()?.let(vm::onYieldChange)
                    },
                    label = { Text("Servings yield") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            val nowFocused = focusState.isFocused
                            if (servingsYieldFocused && !nowFocused) {
                                val parsed = servingsYieldText.toDoubleOrNull()
                                servingsYieldText = if (parsed != null) {
                                    parsed.cleanRecipeYield()
                                } else {
                                    state.servingsYield.cleanRecipeYield()
                                }
                            }
                            servingsYieldFocused = nowFocused
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (state.categories.isEmpty()) {
                        Text(
                            text = "No categories yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.categories.forEach { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.selectedCategoryIds.contains(category.id),
                                    onCheckedChange = { checked ->
                                        vm.onCategoryCheckedChange(category.id, checked)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(category.name)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.newCategoryName,
                            onValueChange = vm::onNewCategoryNameChange,
                            label = { Text("New category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = vm::createCategory) {
                            Text("Add")
                        }
                    }
                }
            }

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
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Ingredients", style = MaterialTheme.typography.titleMedium) }

            item {
                if (state.ingredients.isEmpty()) {
                    Text("No ingredients yet.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.ingredients.forEachIndexed { index, ing ->
                            Column {
                                FoodBannerCardBackground(
                                    foodId = ing.foodId,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = ing.foodName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            val resolvedServingUnit =
                                                ing.servingUnitLabel.toServingUnitOrNull()
                                            val totalGrams = ing.grams

                                            val amountText = if (totalGrams != null && resolvedServingUnit != null) {
                                                val baseText = QuantityDisplayFormatter.format(
                                                    servings = ing.servings ?: 0.0,
                                                    unit = resolvedServingUnit,
                                                    totalGrams = totalGrams
                                                )

                                                when {
                                                    ing.isApproximateWeight && resolvedServingUnit.isMassUnit() ->
                                                        "≈ %,.0f g".format(totalGrams)

                                                    ing.isApproximateWeight ->
                                                        baseText.replace(" • ", " • ≈ ")

                                                    else -> baseText
                                                }
                                            } else {
                                                val unitLabel = ing.servingUnitLabel?.trim().orEmpty()
                                                val enteredAmountText = if (unitLabel.isNotBlank()) {
                                                    "${"%,.2f".format(ing.servings)} $unitLabel"
                                                } else {
                                                    "${"%,.2f".format(ing.servings)} servings"
                                                }
                                                val gramsText = totalGrams?.let { g ->
                                                    val prefix = if (ing.isApproximateWeight) " • ≈ " else " • "
                                                    prefix + "%,.0f".format(g) + " g"
                                                }.orEmpty()
                                                enteredAmountText + gramsText
                                            }

                                            Text(
                                                text = amountText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )

                                            val enteredUnit = ing.enteredUnitLabel?.trim().orEmpty()
                                            val resolvedEnteredUnit = ing.enteredUnitLabel.toServingUnitOrNull()

                                            if (
                                                ing.enteredAmount != null &&
                                                enteredUnit.isNotBlank() &&
                                                (resolvedEnteredUnit == null || !resolvedEnteredUnit.isMassUnit())
                                            ) {
                                                Text(
                                                    text = "Entered as ${"%,.2f".format(ing.enteredAmount)} $enteredUnit",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }

                                            ing.estimatedLineCostDisplay?.let { display ->
                                                Text(
                                                    text = display,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { vm.removeIngredientAt(index) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.trash),
                                                contentDescription = "Remove ingredient",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(AppIconSize.CardAction)
                                            )
                                        }
                                    }
                                }

                                Divider(
                                    modifier = Modifier.padding(start = 0.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.results.take(8).forEach { food ->
                            Row(
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
                    val gramsPerServingUnit = pickedFood.gramsPerServingUnitResolved()
                    var inputUnit by rememberSaveable(pickedFood.id) { mutableStateOf(ServingUnit.G) }
                    var inputAmount by rememberSaveable(pickedFood.id) { mutableStateOf<Double?>(null) }

                    var servingUnitAmount by rememberSaveable(pickedFood.id) {
                        mutableStateOf(state.pickedServings * pickedFood.servingSize)
                    }

                    var gramsAmount by rememberSaveable(pickedFood.id) {
                        mutableStateOf(gramsPerServingUnit?.let { g -> state.pickedServings * g })
                    }

                    LaunchedEffect(state.pickedServings, pickedFood.id, gramsPerServingUnit) {
                        servingUnitAmount = state.pickedServings * pickedFood.servingSize
                        gramsAmount = gramsPerServingUnit?.let { g -> state.pickedServings * g }
                    }

                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
                            .padding(12.dp)
                    ) {
                        SelectedFoodPanel(
                            food = pickedFood,
                            servings = state.pickedServings,
                            servingUnitAmount = servingUnitAmount,
                            gramsAmount = gramsAmount,
                            inputUnit = inputUnit,
                            inputAmount = inputAmount,
                            errorMessage = state.errorMessage,
                            onBack = vm::clearSelection,
                            onServingsChanged = { s -> vm.onServingsChanged(s) },
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
                            onPackage = { mult -> vm.onPackageClicked(mult) },
                            onEditFoodInEditor = { onEditFood(pickedFood.id) },
                            primaryButtonLabel = "Add ingredient",
                            onPrimaryAction = vm::addPickedIngredient,
                            normalizedPriceDisplay = state.pickedNormalizedPriceDisplay,
                            servingPriceDisplay = state.pickedServingPriceDisplay,
                            ingredientCostDisplay = state.pickedIngredientLineCostDisplay
                        )
                        Spacer(
                            modifier = Modifier
                                .height(1.dp)
                                .bringIntoViewRequester(bringAddIntoView)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Preview", style = MaterialTheme.typography.titleMedium) }

            item {
                val servings = state.servingsYield
                val noDivZero = servings > 0

                fun fmt0(v: Double?) = if (v == null) "—" else "%,.0f".format(v)
                fun fmt1(v: Double?) = if (v == null) "—" else "%,.1f".format(v)
                fun fmtCurrency(v: Double?) = if (v == null) "—" else "~ $%,.2f".format(v)
                fun perServing(total: Double): Double? =
                    if (noDivZero) total / servings else null

                val numberColWidth = 88.dp

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("", modifier = Modifier.weight(1f))
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
                            Text(label, modifier = Modifier.weight(1f))
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
                        "Approx. ingredient weight (g)",
                        fmt0(ingredientTotalGrams),
                        fmt0(perServing(ingredientTotalGrams))
                    )

                    state.estimatedRecipeTotalCostDisplay?.let {
                        RowItem(
                            "Estimated cost",
                            it,
                            fmtCurrency(perServing(state.estimatedRecipeTotalCost ?: 0.0))
                        )
                    }

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

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Recipe transfer",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Use the top-right action menu to email a recipe bundle attachment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "The attachment is generated from the saved recipe and handed off to the OS share/email app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                Text("Nutrient tally", style = MaterialTheme.typography.titleMedium)

                val errorMessage = state.nutrientTallyErrorMessage

                if (state.nutrientTallyLoading) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator()
                } else if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (state.nutrientTallyRows.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Add ingredients to see nutrient totals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    NutrientTallyList(rows = state.nutrientTallyRows)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showMockImportPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showMockImportPreviewDialog = false },
            title = { Text("Mock import preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recipe import preview UI is still mock-only.")
                    Text("Email export is now wired to generate a real attachment and hand it to the OS share flow.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showMockImportPreviewDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
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
                Row {
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

    if (showDeleteDialog && canDeleteRecipe) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete recipe?") },
            text = {
                Text(
                    "This will hide the recipe from lists, but any past usage (planned meals, logs, etc.) will be preserved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.deleteRecipe {
                            onDeleteRecipe?.invoke()
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NutrientTallyList(rows: List<NutrientRowUi>) {
    val grouped = rows.groupBy { it.category }
        .toSortedMap(compareBy { it.sortOrder })

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (cat, list) ->
            Text(cat.displayName, style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { row ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = MaterialTheme.colorScheme.secondaryContainer)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = row.unit.symbol,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = row.amount,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Divider(modifier = Modifier.padding(2.dp), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

private fun String?.toServingUnitOrNull(): ServingUnit? {
    val label = this?.trim().orEmpty()
    if (label.isBlank()) return null
    return ServingUnit.entries.firstOrNull { unit ->
        unit.display.equals(label, ignoreCase = true)
    }
}

private fun writeRecipeBundleToCache(
    cacheDir: File,
    bundle: RecipeBundleDto
): File {
    val exportDir = File(cacheDir, "recipe_exports").apply { mkdirs() }
    val fileName = buildRecipeBundleFileName(bundle.recipe.name)
    val file = File(exportDir, fileName)
    file.writeText(bundle.toJsonString(), Charsets.UTF_8)
    return file
}

private fun buildRecipeBundleFileName(recipeName: String): String {
    val safeStem = recipeName
        .trim()
        .replace(Regex("[^A-Za-z0-9 _-]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Recipe" }

    return "$safeStem.akrecipe"
}

private fun RecipeBundleDto.toJsonString(): String =
    toJsonObject().toString(2)

private fun RecipeBundleDto.toJsonObject(): JSONObject =
    JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("exportedAtEpochMs", exportedAtEpochMs)
        put("recipe", recipe.toJsonObject())
        put("ingredients", JSONArray().apply {
            ingredients.forEach { put(it.toJsonObject()) }
        })
        put("foods", JSONArray().apply {
            foods.forEach { put(it.toJsonObject()) }
        })
    }

private fun RecipeBundleRecipeDto.toJsonObject(): JSONObject =
    JSONObject().apply {
        put("stableId", stableId)
        put("name", name)
        put("servingsYield", servingsYield)
        put("totalYieldGrams", totalYieldGrams)
    }

private fun RecipeBundleIngredientDto.toJsonObject(): JSONObject =
    JSONObject().apply {
        put("foodStableId", foodStableId)
        put("ingredientServings", ingredientServings)
    }

private fun RecipeBundleFoodDto.toJsonObject(): JSONObject =
    JSONObject().apply {
        put("stableId", stableId)
        put("name", name)
        put("brand", brand)
        put("servingSize", servingSize)
        put("servingUnit", servingUnit)
        put("gramsPerServingUnit", gramsPerServingUnit)
        put("mlPerServingUnit", mlPerServingUnit)
        put("servingsPerPackage", servingsPerPackage)
        put("isRecipe", isRecipe)
        put("isLowSodium", isLowSodium)
        put("usdaFdcId", usdaFdcId)
        put("usdaGtinUpc", usdaGtinUpc)
        put("usdaPublishedDate", usdaPublishedDate)
        put("usdaModifiedDate", usdaModifiedDate)
        put("usdaServingSize", usdaServingSize)
        put("usdaServingUnit", usdaServingUnit)
        put("householdServingText", householdServingText)
        put("canonicalNutrientBasis", canonicalNutrientBasis?.name)
        put("nutrients", JSONArray().apply {
            nutrients.forEach { put(it.toJsonObject()) }
        })
    }

private fun RecipeBundleFoodNutrientDto.toJsonObject(): JSONObject =
    JSONObject().apply {
        put("code", code)
        put("amount", amount)
    }