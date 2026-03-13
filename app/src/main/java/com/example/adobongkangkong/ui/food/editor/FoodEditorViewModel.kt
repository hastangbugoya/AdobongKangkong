package com.example.adobongkangkong.ui.food.editor

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.food.usecase.MergeFoodsUseCase
import com.example.adobongkangkong.domain.model.AliasAddResult
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isAmbiguousForGrounding
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.NutrientBasisScaler
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodCategoryRepository
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.usda.ImportUsdaFoodFromSearchJsonUseCase
import com.example.adobongkangkong.domain.usda.ResolveBarcodeWithUsdaUseCase
import com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByBarcodeUseCase
import com.example.adobongkangkong.domain.usda.model.BarcodeRemapDialogState
import com.example.adobongkangkong.domain.usecase.GetFoodEditorDataUseCase
import com.example.adobongkangkong.domain.usecase.HardDeleteFoodIfUnusedUseCase
import com.example.adobongkangkong.domain.usecase.SaveFoodWithNutrientsUseCase
import com.example.adobongkangkong.domain.usecase.SearchNutrientsUseCase
import com.example.adobongkangkong.domain.usecase.SoftDeleteFoodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_BARCODE_ASSIGN_FOOD_ID = "barcode_assign_foodId"
private const val KEY_BARCODE_ASSIGN_BARCODE = "barcode_assign_barcode"
private const val KEY_MERGE_PICK_CANONICAL_FOOD_ID = "merge_pick_canonical_food_id"

@HiltViewModel
class FoodEditorViewModel @Inject constructor(
    private val getData: GetFoodEditorDataUseCase,
    private val searchNutrients: SearchNutrientsUseCase,
    private val saveFoodWithNutrients: SaveFoodWithNutrientsUseCase,
    private val foodRepo: FoodRepository,
    private val foodCategoryRepo: FoodCategoryRepository,
    private val nutrientAliasRepo: NutrientAliasRepository,
    private val flagsRepo: FoodGoalFlagsRepository,
    private val searchUsdaByBarcode: SearchUsdaFoodsByBarcodeUseCase,
    private val importUsdaFromSearchJson: ImportUsdaFoodFromSearchJsonUseCase,
    private val foodBarcodeRepo: FoodBarcodeRepository,
    private val softDeleteFood: SoftDeleteFoodUseCase,
    private val hardDeleteFoodIfUnused: HardDeleteFoodIfUnusedUseCase,
    private val resolveBarcodeWithUsda: ResolveBarcodeWithUsdaUseCase,
    private val nutrientRepo: NutrientRepository,
    private val mergeFoodsUseCase: MergeFoodsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodEditorState())
    val state: StateFlow<FoodEditorState> = _state

    private val savedState = savedStateHandle

    private val assignBarcodeToExistingFlow = MutableStateFlow<String?>(null)
    val assignBarcodeToExistingBarcode: StateFlow<String?> = assignBarcodeToExistingFlow

    fun consumeAssignBarcodeToExistingRequest() {
        assignBarcodeToExistingFlow.value = null
    }

    @OptIn(FlowPreview::class)
    private val nutrientQueryFlow = MutableStateFlow("")

    private val aliasSheetMessageFlow = MutableStateFlow<String?>(null)
    val aliasSheetMessage: StateFlow<String?> = aliasSheetMessageFlow

    private val didDeleteFlow = MutableStateFlow(false)
    val didDelete: StateFlow<Boolean> = didDeleteFlow

    private val didMergeFlow = MutableStateFlow(false)
    val didMerge: StateFlow<Boolean> = didMergeFlow

    private val aliasSheetNutrientIdFlow = MutableStateFlow<Long?>(null)
    private val aliasSheetNutrientNameFlow = MutableStateFlow<String?>(null)
    val aliasSheetNutrientName: StateFlow<String?> = aliasSheetNutrientNameFlow

    // Pending remap when we require user confirmation
    private var pendingRemapBarcode: String? = null
    private var pendingRemapTargetFoodId: Long? = null

    @OptIn(FlowPreview::class)
    private val nutrientResultsFlow =
        nutrientQueryFlow
            .map { it.trim() }
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList())
                else searchNutrients(q, limit = 80)
            }

    val selectedAliases: StateFlow<List<String>> =
        aliasSheetNutrientIdFlow
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else nutrientAliasRepo.observeAliases(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(foodId: Long?, initialName: String?, force: Boolean = false) {
        Log.d("Meow", "foodId: $foodId initialName: $initialName force: $force")
        val current = _state.value

        // ✅ honor force
        if (!force) {
            // Don't allow "new food" route to overwrite an editor already showing an existing food.
            if (foodId == null && current.foodId != null) return

            // If we’re already showing this same existing food, no-op.
            if (current.foodId == foodId && (foodId != null)) return
        }

        viewModelScope.launch {
            val flags = if (foodId != null) flagsRepo.get(foodId) else null
            val data = getData(foodId)
            val food = data.food
            val rows = data.nutrients
            val allCategories = foodCategoryRepo.getAll()
            val selectedCategoryIds = if (foodId != null) {
                foodCategoryRepo.getForFood(foodId).map { it.id }.toSet()
            } else {
                current.selectedCategoryIds
            }

            val stableId =
                food?.stableId
                    ?: current.stableId
                    ?: UUID.randomUUID().toString()

            val servingSize = food?.servingSize ?: 1.0

            val servingUnit = food?.servingUnit ?: ServingUnit.SERVING
            val gramsPerServingUnit = food?.gramsPerServingUnit
            val mlPerServingUnit = food?.mlPerServingUnit

            val gramsPerServingUnitEffectiveForDisplay: Double? =
                when (servingUnit) {
                    ServingUnit.G -> 1.0
                    else -> gramsPerServingUnit
                }

            val inferredBasisType: BasisType? =
                current.basisType
                    ?: when {
                        rows.any { it.basisType == BasisType.PER_100ML } -> BasisType.PER_100ML
                        rows.any { it.basisType == BasisType.PER_100G } -> BasisType.PER_100G
                        else -> {
                            // No nutrient basis to infer from; fall back to bridges / serving unit.
                            when {
                                (food?.mlPerServingUnit != null) -> BasisType.PER_100ML
                                (food?.gramsPerServingUnit != null) -> BasisType.PER_100G
                                (food?.servingUnit ?: ServingUnit.SERVING).toMilliliters(1.0) != null -> BasisType.PER_100ML
                                else -> BasisType.PER_100G
                            }
                        }
                    }

            val assignedBarcodes = if (foodId != null) {
                foodBarcodeRepo.getAllBarcodesForFood(foodId).map { it.barcode }
            } else emptyList()

            val loadedRows = rows.map { r ->
                val displayAmount =
                    when (r.basisType) {
                        BasisType.PER_100G -> NutrientBasisScaler
                            .canonicalToDisplayPerServing(
                                storedAmount = r.amount,
                                storedBasis = r.basisType,
                                servingSize = servingSize,
                                gramsPerServingUnit = gramsPerServingUnitEffectiveForDisplay
                            ).amount

                        BasisType.PER_100ML -> NutrientBasisScaler
                            .canonicalToDisplayPerServingVolume(
                                storedAmount = r.amount,
                                storedBasis = r.basisType,
                                servingSize = servingSize,
                                mlPerServingUnit = mlPerServingUnitEffective(
                                    servingUnit = food?.servingUnit ?: ServingUnit.SERVING,
                                    mlPerServingUnit = mlPerServingUnit
                                )
                            ).amount

                        else -> r.amount
                    }

                NutrientRowUi(
                    nutrientId = r.nutrient.id,
                    code = r.nutrient.code,
                    name = r.nutrient.displayName,
                    aliases = r.nutrient.aliases,
                    unit = r.nutrient.unit,
                    category = r.nutrient.category,
                    amount = displayAmount.toString()
                )
            }

            val editorRows = buildEditorRowsForAllNutrients(loadedRows)

            val next = current.copy(
                hasLoaded = true,
                foodId = foodId,
                stableId = stableId,
                name = food?.name ?: (initialName ?: ""),
                brand = food?.brand.orEmpty(),
                servingSize = food?.servingSize?.toString() ?: "1.0",
                servingUnit = food?.servingUnit ?: ServingUnit.SERVING,
                basisType = inferredBasisType,
                gramsPerServingUnit = current.gramsPerServingUnit.takeIf { it.isNotBlank() }
                    ?: food?.gramsPerServingUnit?.toString().orEmpty(),
                mlPerServingUnit = current.mlPerServingUnit.takeIf { it.isNotBlank() }
                    ?: food?.mlPerServingUnit?.toString().orEmpty(),
                servingsPerPackage = current.servingsPerPackage.takeIf { it.isNotBlank() }
                    ?: food?.servingsPerPackage?.toString().orEmpty(),
                categories = allCategories
                    .sortedBy { it.name.lowercase() }
                    .map { category ->
                        FoodCategoryUi(
                            id = category.id,
                            name = category.name,
                            isSystem = category.isSystem,
                        )
                    },
                selectedCategoryIds = selectedCategoryIds,
                newCategoryName = "",
                nutrientRows = sortNutrientRows(editorRows),
                favorite = flags?.favorite ?: false,
                eatMore = flags?.eatMore ?: false,
                limit = flags?.limit ?: false,
                isSaving = false,
                errorMessage = null,
                hasUnsavedChanges = false,
                assignedBarcodes = assignedBarcodes,
                barcodeActionMessage = null,
                scannedBarcode = if (foodId == null) current.scannedBarcode else ""
            )

            _state.value = applyNeedsFix(next, current)
        }
    }

    private suspend fun buildEditorRowsForAllNutrients(
        savedRows: List<NutrientRowUi>
    ): List<NutrientRowUi> {
        val allNutrients = nutrientRepo.observeAllNutrients().first()

        val savedByNutrientId = savedRows.associateBy { it.nutrientId }
        val savedByCode = savedRows.associateBy { it.code }

        return allNutrients.map { nutrient ->
            val saved = savedByNutrientId[nutrient.id] ?: savedByCode[nutrient.code]

            NutrientRowUi(
                nutrientId = nutrient.id,
                code = nutrient.code,
                name = nutrient.displayName,
                aliases = nutrient.aliases,
                unit = nutrient.unit,
                category = nutrient.category,
                amount = saved?.amount ?: ""
            )
        }
    }

    /**
     * Merges the currently edited food into another canonical food selected by the user.
     *
     * Merge direction:
     * - current edited food = override/source food
     * - selected target food = canonical/surviving food
     *
     * Rules:
     * - self-merge is ignored
     * - historical logs are intentionally left unchanged
     * - on success, the source food is marked merged + soft-deleted by [MergeFoodsUseCase]
     */
    fun mergeIntoFood(canonicalFoodId: Long, onDone: (() -> Unit)? = null) {
        val overrideFoodId = _state.value.foodId ?: return
        if (overrideFoodId == canonicalFoodId) return

        viewModelScope.launch {
            try {
                update { it.copy(isSaving = true, errorMessage = null) }

                mergeFoodsUseCase(
                    overrideFoodId = overrideFoodId,
                    canonicalFoodId = canonicalFoodId
                )

                didMergeFlow.value = true
                onDone?.invoke()
            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to merge food.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun ensureDefaultNutrientRows(rows: List<NutrientRowUi>): List<NutrientRowUi> {
        val existingCodes = rows.map { it.code }.toSet()
        val missingSpecs = EditorDefaultNutrients.defaults.filterNot { it.code in existingCodes }
        if (missingSpecs.isEmpty()) return rows

        val missingRows = missingSpecs.mapNotNull { spec ->
            val matches = searchNutrients(spec.searchQuery, limit = 20).first()
            val nutrient = matches.firstOrNull { it.code == spec.code }
                ?: matches.firstOrNull { it.displayName.equals(spec.displayName, ignoreCase = true) }
                ?: matches.firstOrNull()
                ?: return@mapNotNull null

            NutrientRowUi(
                nutrientId = nutrient.id,
                code = nutrient.code,
                name = nutrient.displayName,
                aliases = nutrient.aliases,
                unit = nutrient.unit,
                category = nutrient.category,
                amount = ""
            )
        }

        return rows + missingRows
    }

    fun dismissBarcodeCollision() {
        update { it.copy(barcodeCollisionDialog = null) }
    }

    fun dismissNeedsFixBanner() {
        update { it.copy(fixBannerDismissed = true) }
    }

    fun openExistingFromCollision() {
        val dialog = state.value.barcodeCollisionDialog ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            foodBarcodeRepo.touchLastSeen(dialog.barcode, now)

            update {
                it.copy(
                    barcodeCollisionDialog = null,
                    pendingUsdaSearchJson = null,
                    barcodePickItems = emptyList(),
                    errorMessage = null
                )
            }

            load(foodId = dialog.existingFoodId, initialName = null, force = true)
        }
    }

    private fun applyNeedsFix(next: FoodEditorState, prev: FoodEditorState): FoodEditorState {
        val computedMessage = computeFixMessage(next)
        val needsFix = !computedMessage.isNullOrBlank()

        val dismissed =
            if (!needsFix) false
            else if (computedMessage != prev.fixMessage) false
            else next.fixBannerDismissed

        return next.copy(
            needsFix = needsFix,
            fixMessage = computedMessage,
            fixBannerDismissed = dismissed
        )
    }

    private fun computeFixMessage(s: FoodEditorState): String? {
        if (!s.hasLoaded) return null

        if (s.nutrientRows.isEmpty()) {
            return "Food has no nutrients and cannot be logged or used in recipes."
        }

        val hasAnyNumeric = s.nutrientRows.any { row -> row.amount.trim().toDoubleOrNull() != null }
        if (!hasAnyNumeric) {
            return "Food nutrient amounts are blank; enter nutrient amounts to log or use in recipes."
        }

        val grams = s.gramsPerServingUnit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
        val ml = s.mlPerServingUnit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

        val needsBacking = s.servingUnit.requiresGramsPerServing()
        val isVolumeGrounded = s.servingUnit.isVolumeUnit() || (ml != null)

        if (needsBacking && grams == null && !isVolumeGrounded) {
            return "Food needs grams per serving unit to be loggable (and recipe-usable) when using servings."
        }

        return null
    }

    fun remapFromCollisionProceedImport() {
        val dialog = state.value.barcodeCollisionDialog ?: return
        val json = state.value.pendingUsdaSearchJson ?: run {
            update { it.copy(errorMessage = "Missing pending USDA JSON.") }
            return
        }
        val fdcId = dialog.incomingFdcId ?: run {
            update { it.copy(errorMessage = "Missing incoming fdcId.") }
            return
        }

        viewModelScope.launch {
            when (val r = importUsdaFromSearchJson(json, selectedFdcId = fdcId)) {
                is ImportUsdaFoodFromSearchJsonUseCase.Result.Success -> {
                    val now = System.currentTimeMillis()

                    foodBarcodeRepo.upsertAndTouch(
                        entity = FoodBarcodeEntity(
                            barcode = dialog.barcode,
                            foodId = r.foodId,
                            source = BarcodeMappingSource.USDA,
                            usdaFdcId = r.fdcId,
                            usdaPublishedDateIso = r.publishedDateIso ?: dialog.incomingPublishedDateIso,
                            assignedAtEpochMs = now,
                            lastSeenAtEpochMs = now
                        ),
                        nowEpochMs = now
                    )

                    update {
                        it.copy(
                            barcodeCollisionDialog = null,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            errorMessage = null
                        )
                    }

                    load(foodId = r.foodId, initialName = null, force = true)
                }

                is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked -> {
                    update { it.copy(errorMessage = r.reason) }
                }
            }
        }
    }

    fun onResolveBarcodeCollision(action: BarcodeCollisionAction) {
        when (action) {
            BarcodeCollisionAction.Cancel -> dismissBarcodeCollision()
            BarcodeCollisionAction.OpenExisting -> openExistingFromCollision()
            BarcodeCollisionAction.Replace -> remapFromCollisionProceedImport()
        }
    }

    fun onPickBasisType(type: BasisType) {
        update {
            it.copy(
                hasUnsavedChanges = it.basisType != type || it.gramsPerServingUnit.isNotBlank() || it.mlPerServingUnit.isNotBlank(),
                basisType = type,
                gramsPerServingUnit = if (type == BasisType.PER_100ML) "" else it.gramsPerServingUnit,
                mlPerServingUnit = if (type == BasisType.PER_100G) "" else it.mlPerServingUnit,
            )
        }
    }

    fun dismissBarcodeFallback() {
        update {
            it.copy(
                isBarcodeFallbackOpen = false,
                barcodeFallbackMessage = null,
                barcodeFallbackCreateName = "",
                barcodeAlreadyAssignedFoodId = null
            )
        }
    }

    fun onBarcodeFallbackCreateNameChange(v: String) {
        update { it.copy(barcodeFallbackCreateName = v) }
    }

    fun barcodeFallbackAssignExisting() {
        val code = _state.value.scannedBarcode.trim()
        if (code.isBlank()) return

        update { it.copy(isBarcodeFallbackOpen = false) }
        assignBarcodeToExistingFlow.value = code
    }

    fun barcodeFallbackCreateMinimalFood() {
        val barcode = _state.value.scannedBarcode.trim()
        val name = _state.value.barcodeFallbackCreateName.trim()
        if (barcode.isBlank() || name.isBlank()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                update { it.copy(isSaving = true, errorMessage = null) }

                val stableId = UUID.randomUUID().toString()
                val food = Food(
                    id = 0L,
                    name = name,
                    brand = null,
                    servingSize = 1.0,
                    servingUnit = ServingUnit.SERVING,
                    gramsPerServingUnit = null,
                    mlPerServingUnit = null,
                    servingsPerPackage = null,
                    isRecipe = false,
                    stableId = stableId,
                    isLowSodium = null
                )

                val newFoodId = saveFoodWithNutrients(food, emptyList())

                val existing = foodBarcodeRepo.getByBarcode(barcode)
                if (existing != null && existing.foodId != newFoodId) {
                    update {
                        it.copy(
                            isBarcodeFallbackOpen = false,
                            barcodeRemapDialog = BarcodeRemapDialogState(
                                barcode = barcode,
                                fromFoodId = existing.foodId,
                                toFoodId = newFoodId,
                                fromSource = existing.source
                            )
                        )
                    }
                    pendingRemapTargetFoodId = newFoodId
                    pendingRemapBarcode = barcode
                    return@launch
                }

                foodBarcodeRepo.upsertAndTouch(
                    entity = FoodBarcodeEntity(
                        barcode = barcode,
                        foodId = newFoodId,
                        source = BarcodeMappingSource.USER_ASSIGNED,
                        usdaFdcId = null,
                        usdaPublishedDateIso = null,
                        assignedAtEpochMs = now,
                        lastSeenAtEpochMs = now
                    ),
                    nowEpochMs = now
                )

                update {
                    it.copy(
                        isBarcodeFallbackOpen = false,
                        barcodeFallbackMessage = null,
                        barcodeFallbackCreateName = "",
                        pendingUsdaSearchJson = null,
                        barcodePickItems = emptyList(),
                        errorMessage = null,
                        isBarcodeScannerOpen = false
                    )
                }

                load(foodId = newFoodId, initialName = null)
            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to create food.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    fun unassignBarcode(barcode: String) {
        val foodId = _state.value.foodId ?: return
        val code = barcode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            try {
                foodBarcodeRepo.deleteByBarcode(code)

                val refreshed = foodBarcodeRepo
                    .getAllBarcodesForFood(foodId)
                    .map { it.barcode }

                update {
                    it.copy(
                        assignedBarcodes = refreshed,
                        scannedBarcode = if (it.scannedBarcode == code) "" else it.scannedBarcode
                    )
                }
            } catch (t: Throwable) {
                update { it.copy(errorMessage = "Failed to unassign barcode.") }
            }
        }
    }

    fun onConfirmBarcodeRemap(confirm: Boolean) {
        val barcode = pendingRemapBarcode
        val toFoodId = pendingRemapTargetFoodId

        update { it.copy(barcodeRemapDialog = null) }
        pendingRemapBarcode = null
        pendingRemapTargetFoodId = null

        if (!confirm || barcode.isNullOrBlank() || toFoodId == null) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            foodBarcodeRepo.upsertAndTouch(
                entity = FoodBarcodeEntity(
                    barcode = barcode,
                    foodId = toFoodId,
                    source = BarcodeMappingSource.USER_ASSIGNED,
                    usdaFdcId = null,
                    usdaPublishedDateIso = null,
                    assignedAtEpochMs = now,
                    lastSeenAtEpochMs = now
                ),
                nowEpochMs = now
            )

            update {
                it.copy(
                    pendingUsdaSearchJson = null,
                    barcodePickItems = emptyList(),
                    errorMessage = null,
                    isBarcodeScannerOpen = false,
                    isBarcodeFallbackOpen = false,
                    barcodeFallbackMessage = null,
                    barcodeFallbackCreateName = ""
                )
            }

            load(foodId = toFoodId, initialName = null)
        }
    }

    fun closeGroundingDialog() {
        update { it.copy(isGroundingDialogOpen = false) }
    }

    fun onNameChange(v: String) = update { it.copy(name = v, errorMessage = null, hasUnsavedChanges = true) }
    fun onBrandChange(v: String) = update { it.copy(brand = v, hasUnsavedChanges = true) }
    fun onServingSizeChange(v: String) = update { it.copy(servingSize = v, hasUnsavedChanges = true) }
    fun onServingUnitChange(v: ServingUnit) = update { it.copy(servingUnit = v, hasUnsavedChanges = true) }
    fun onGramsPerServingChange(v: String) = update { it.copy(gramsPerServingUnit = v, hasUnsavedChanges = true) }

    fun onMlPerServingChange(v: String) = update { it.copy(mlPerServingUnit = v, hasUnsavedChanges = true) }
    fun onServingsPerPackageChange(v: String) = update { it.copy(servingsPerPackage = v, hasUnsavedChanges = true) }

    fun onGroundingModeChange(v: GroundingMode) {
        update { s ->
            val next = s.copy(groundingMode = v)

            if (v == GroundingMode.LIQUID && next.mlPerServingUnit.isBlank()) {
                val auto = next.servingUnit.toMilliliters(1.0)
                if (auto != null && auto > 0.0) {
                    return@update next.copy(mlPerServingUnit = auto.roundForUi())
                }
            }
            next
        }
    }

    fun onMlPerServingTotalChange(totalMlText: String) = update { s ->
        val totalMl = totalMlText.toDoubleOrNull()
        val servingSize = s.servingSize.toDoubleOrNull()

        val base = s.copy(hasUnsavedChanges = true)

        if (totalMl == null || totalMl <= 0.0 || servingSize == null || servingSize <= 0.0) {
            return@update base
        }

        val perUnit = totalMl / servingSize

        base.copy(
            mlPerServingUnit = perUnit.roundForUi()
        )
    }

    fun dismissGroundingDialog() = update { it.copy(isGroundingDialogOpen = false) }

    fun onCategoryCheckedChange(categoryId: Long, checked: Boolean) {
        update { s ->
            val nextIds = s.selectedCategoryIds.toMutableSet().apply {
                if (checked) add(categoryId) else remove(categoryId)
            }
            s.copy(selectedCategoryIds = nextIds, hasUnsavedChanges = true)
        }
    }

    fun onNewCategoryNameChange(v: String) = update { it.copy(newCategoryName = v) }

    fun createCategory() {
        val rawName = _state.value.newCategoryName.trim()
        if (rawName.isBlank()) {
            update { it.copy(errorMessage = "Category name is required.") }
            return
        }

        viewModelScope.launch {
            try {
                val created = foodCategoryRepo.getOrCreateByName(rawName)
                update { s ->
                    s.copy(
                        categories = (s.categories + FoodCategoryUi(
                            id = created.id,
                            name = created.name,
                            isSystem = created.isSystem,
                        )).distinctBy { it.id }.sortedBy { it.name.lowercase() },
                        selectedCategoryIds = s.selectedCategoryIds + created.id,
                        newCategoryName = "",
                        hasUnsavedChanges = true,
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to create category.") }
            }
        }
    }

    fun onFavoriteChange(v: Boolean) = update { it.copy(favorite = v, hasUnsavedChanges = true) }
    fun onEatMoreChange(v: Boolean) = update { it.copy(eatMore = v, hasUnsavedChanges = true) }
    fun onLimitChange(v: Boolean) = update { it.copy(limit = v, hasUnsavedChanges = true) }

    fun onNutrientAmountChange(nutrientId: Long, amount: String) {
        update { s ->
            s.copy(
                nutrientRows = s.nutrientRows.map {
                    if (it.nutrientId == nutrientId) it.copy(amount = amount) else it
                },
                hasUnsavedChanges = true
            )
        }
    }

    fun removeNutrientRow(nutrientId: Long) {
        update { s ->
            s.copy(
                nutrientRows = s.nutrientRows.filterNot { it.nutrientId == nutrientId },
                hasUnsavedChanges = true
            )
        }
    }

    fun onNutrientSearchQueryChange(v: String) {
        update { it.copy(nutrientSearchQuery = v) }
        nutrientQueryFlow.value = v

        if (v.isBlank()) {
            update { it.copy(nutrientSearchResults = emptyList()) }
        }
    }

    fun addNutrient(n: NutrientSearchResultUi) {
        update { s ->
            if (s.nutrientRows.any { it.nutrientId == n.id }) s
            else s.copy(
                hasUnsavedChanges = true,
                nutrientRows = sortNutrientRows(
                    s.nutrientRows + NutrientRowUi(
                        nutrientId = n.id,
                        code = n.code,
                        name = n.name,
                        aliases = n.aliases,
                        unit = n.unit,
                        category = n.category,
                        amount = ""
                    )
                )
            )
        }
    }

    private fun sortNutrientRows(rows: List<NutrientRowUi>): List<NutrientRowUi> {
        return rows.sortedWith(
            compareBy<NutrientRowUi> { EditorDefaultNutrients.rankFor(it.code) }
                .thenBy { it.category.sortOrder }
                .thenBy { it.name.lowercase() }
        )
    }

    fun save(onDone: (Long) -> Unit) {
        val s = _state.value
        val name = s.name.trim()
        if (name.isBlank()) {
            update { it.copy(errorMessage = "Food name is required.") }
            return
        }

        val unit = s.servingUnit
        val needsBasis =
            (unit.isAmbiguousForGrounding()) &&
                    s.basisType == null

        if (needsBasis) {
            update { it.copy(isGroundingDialogOpen = true) }
            return
        }

        fun parseDoubleOrNull(x: String): Double? =
            x.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        val servingSize = parseDoubleOrNull(s.servingSize) ?: 1.0
        val gramsPerServingUnitInput = parseDoubleOrNull(s.gramsPerServingUnit)
        val mlPerServingUnitInput = parseDoubleOrNull(s.mlPerServingUnit)
        val servingsPerPackage = parseDoubleOrNull(s.servingsPerPackage)

        val gramsPerServingUnitFinal: Double? =
            when {
                gramsPerServingUnitInput != null && gramsPerServingUnitInput > 0.0 -> gramsPerServingUnitInput
                s.servingUnit.isMassUnit() && s.servingUnit != ServingUnit.G -> s.servingUnit.toGrams(1.0)
                else -> null
            }?.takeIf { it > 0.0 }

        val mlPerServingUnitFinal: Double? =
            when {
                mlPerServingUnitInput != null && mlPerServingUnitInput > 0.0 -> mlPerServingUnitInput
                else -> s.servingUnit.toMilliliters(1.0)
            }?.takeIf { it > 0.0 }

        val needsGroundingPrompt =
            s.servingUnit.isAmbiguousForGrounding() &&
                    gramsPerServingUnitFinal == null &&
                    mlPerServingUnitFinal == null

        if (needsGroundingPrompt) {
            update { it.copy(isGroundingDialogOpen = true) }
            return
        }

        val finalGramsBridge =
            if (s.groundingMode == GroundingMode.LIQUID) null else gramsPerServingUnitFinal

        val finalMlBridge =
            if (s.groundingMode == GroundingMode.SOLID) null else mlPerServingUnitFinal

        val stableId = s.stableId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        val defaultBasisType =
            if (isVolumeGrounded(s.servingUnit, finalMlBridge)) {
                BasisType.PER_100ML
            } else if (s.servingUnit == ServingUnit.G || finalGramsBridge != null) {
                BasisType.PER_100G
            } else {
                BasisType.USDA_REPORTED_SERVING
            }

        val gramsPerServingUnitEffective =
            when (s.servingUnit) {
                ServingUnit.G -> 1.0
                else -> finalGramsBridge
            }?.takeIf { it > 0.0 }

        val mlPerServingUnitEffective =
            mlPerServingUnitEffective(
                servingUnit = s.servingUnit,
                mlPerServingUnit = finalMlBridge
            )

        val rows = s.nutrientRows.mapNotNull { ui ->
            val amt = ui.amount.trim()
            val uiPerServingAmount =
                if (amt.isEmpty()) 0.0 else (amt.toDoubleOrNull() ?: return@mapNotNull null)

            val amountToStore =
                when (defaultBasisType) {
                    BasisType.PER_100G -> NutrientBasisScaler.displayPerServingToCanonical(
                        uiPerServingAmount,
                        BasisType.PER_100G,
                        servingSize,
                        gramsPerServingUnitEffective
                    ).amount

                    BasisType.PER_100ML -> NutrientBasisScaler.displayPerServingToCanonicalVolume(
                        uiPerServingAmount,
                        BasisType.PER_100ML,
                        servingSize,
                        mlPerServingUnitEffective
                    ).amount

                    else -> uiPerServingAmount
                }

            FoodNutrientRow(
                nutrient = Nutrient(
                    id = ui.nutrientId,
                    code = ui.code,
                    displayName = ui.name,
                    unit = ui.unit,
                    category = ui.category,
                    aliases = ui.aliases
                ),
                amount = amountToStore,
                basisType = defaultBasisType,
                basisGrams = when (defaultBasisType) {
                    BasisType.PER_100G -> 100.0
                    BasisType.PER_100ML -> 100.0
                    else -> null
                }
            )
        }

        viewModelScope.launch {
            update { it.copy(isSaving = true, errorMessage = null, stableId = stableId) }
            try {
                val existing: Food? = s.foodId?.let { foodRepo.getById(it) }

                val food =
                    if (existing != null) {
                        existing.copy(
                            name = name,
                            brand = s.brand.trim().ifEmpty { null },
                            servingSize = servingSize,
                            servingUnit = s.servingUnit,
                            gramsPerServingUnit = finalGramsBridge,
                            servingsPerPackage = servingsPerPackage,
                            mlPerServingUnit = finalMlBridge,
                            isRecipe = false
                        )
                    } else {
                        Food(
                            id = 0L,
                            name = name,
                            brand = s.brand.trim().ifEmpty { null },
                            servingSize = servingSize,
                            servingUnit = s.servingUnit,
                            gramsPerServingUnit = finalGramsBridge,
                            servingsPerPackage = servingsPerPackage,
                            isRecipe = false,
                            stableId = stableId,
                            mlPerServingUnit = finalMlBridge,
                            isLowSodium = null
                        )
                    }

                val savedId = saveFoodWithNutrients(food, rows)

                flagsRepo.setFlags(
                    foodId = savedId,
                    favorite = s.favorite,
                    eatMore = s.eatMore,
                    limit = s.limit
                )

                foodCategoryRepo.replaceForFood(
                    foodId = savedId,
                    categoryIds = s.selectedCategoryIds,
                )

                update { it.copy(hasUnsavedChanges = false) }
                onDone(savedId)

            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to save food.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    fun openAliasSheet(nutrientId: Long, nutrientName: String) {
        aliasSheetNutrientIdFlow.value = nutrientId
        aliasSheetNutrientNameFlow.value = nutrientName
    }

    fun closeAliasSheet() {
        aliasSheetNutrientIdFlow.value = null
        aliasSheetNutrientNameFlow.value = null
        aliasSheetMessageFlow.value = null
    }

    fun addAlias(alias: String) {
        val id = aliasSheetNutrientIdFlow.value ?: return
        viewModelScope.launch {
            when (nutrientAliasRepo.addAlias(id, alias)) {
                AliasAddResult.Added -> {
                    aliasSheetMessageFlow.value = null
                    update { it.copy(hasUnsavedChanges = true) }
                }
                AliasAddResult.IgnoredEmpty -> aliasSheetMessageFlow.value = "Alias is empty."
                AliasAddResult.IgnoredDuplicate -> aliasSheetMessageFlow.value = "Alias already exists."
            }
        }
    }

    fun deleteAlias(alias: String) {
        val id = aliasSheetNutrientIdFlow.value ?: return
        viewModelScope.launch {
            nutrientAliasRepo.deleteAlias(id, alias)
            update { it.copy(hasUnsavedChanges = true) }
        }
    }

    fun deleteFood() {
        val foodId = _state.value.foodId ?: return

        viewModelScope.launch {
            update { it.copy(isSaving = true, errorMessage = null) }
            try {
                when (softDeleteFood(foodId)) {
                    is SoftDeleteFoodUseCase.Result.Success -> didDeleteFlow.value = true
                    is SoftDeleteFoodUseCase.Result.NotFound -> update { it.copy(errorMessage = "Food not found.") }
                }
            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to delete food.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    fun hardDeleteFoodPermanently() {
        val foodId = _state.value.foodId ?: return

        viewModelScope.launch {
            update { it.copy(isSaving = true, errorMessage = null) }
            try {
                when (val r = hardDeleteFoodIfUnused(foodId)) {
                    is HardDeleteFoodIfUnusedUseCase.Result.Success -> didDeleteFlow.value = true
                    is HardDeleteFoodIfUnusedUseCase.Result.NotFound -> update { it.copy(errorMessage = "Food not found.") }
                    is HardDeleteFoodIfUnusedUseCase.Result.Blocked -> {
                        val msg =
                            if (r.reasons.isNotEmpty()) r.reasons.joinToString("\n")
                            else "Cannot delete permanently: this food is still referenced."
                        update { it.copy(errorMessage = msg) }
                    }
                }
            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to delete food permanently.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    private fun update(block: (FoodEditorState) -> FoodEditorState) {
        val prev = _state.value
        val nextRaw = block(prev)
        _state.value = applyNeedsFix(nextRaw, prev)
    }

    private fun Double.roundForUi(): String {
        return "%,.2f".format(this).replace(",", "")
    }

    private fun isVolumeGrounded(servingUnit: ServingUnit, mlPerServingUnit: Double?): Boolean {
        return mlPerServingUnit != null || servingUnit == ServingUnit.ML || servingUnit == ServingUnit.L
    }

    private fun mlPerServingUnitEffective(servingUnit: ServingUnit, mlPerServingUnit: Double?): Double? {
        return when (servingUnit) {
            ServingUnit.ML -> 1.0
            ServingUnit.L -> 1000.0
            else -> mlPerServingUnit
        }?.takeIf { it > 0.0 }
    }

    fun openBarcodeScanner() = update { it.copy(isBarcodeScannerOpen = true, errorMessage = null) }

    fun closeBarcodeScanner() = update {
        it.copy(
            isBarcodeScannerOpen = false,
            pendingUsdaSearchJson = null,
            barcodePickItems = emptyList()
        )
    }

    fun onBarcodeScanned(barcode: String) {
        val cleaned = barcode.trim()
        if (cleaned.isBlank()) return

        Log.d("Meow", "FoodEditor> onBarcodeScanned raw='$barcode' cleaned='$cleaned'")
        Log.d("Meow", "FoodEditor> barcode lookup START barcode='$cleaned'")

        update { it.copy(isBarcodeScannerOpen = false) }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = foodBarcodeRepo.getByBarcode(cleaned)

            Log.d(
                "Meow",
                "FoodEditor> barcode lookup RESULT barcode='$cleaned' " +
                        "existing=${existing != null} source=${existing?.source} foodId=${existing?.foodId} usdaFdcId=${existing?.usdaFdcId}"
            )

            if (existing != null && existing.source == BarcodeMappingSource.USDA) {
                foodBarcodeRepo.touchLastSeen(cleaned, now)

                update {
                    it.copy(
                        scannedBarcode = cleaned,
                        pendingUsdaSearchJson = null,
                        barcodePickItems = emptyList(),
                        isBarcodeFallbackOpen = false,
                        barcodeFallbackMessage = null,
                        barcodeCollisionDialog = null,
                        errorMessage = null,
                        isBarcodeScannerOpen = false
                    )
                }

                load(foodId = existing.foodId, initialName = null, force = true)
                return@launch
            }

            Log.d("Meow", "FoodEditor> USDA search START barcode='$cleaned'")
            when (val r = searchUsdaByBarcode(cleaned)) {
                is SearchUsdaFoodsByBarcodeUseCase.Result.Success -> {
                    Log.d(
                        "Meow",
                        "FoodEditor> USDA search SUCCESS barcode='${r.scannedBarcode}' candidates=${r.candidates.size}"
                    )
                    update {
                        it.copy(
                            scannedBarcode = r.scannedBarcode,
                            pendingUsdaSearchJson = r.searchJson,
                            barcodePickItems = r.candidates,
                            isBarcodeScannerOpen = false,
                            errorMessage = null,
                            barcodeCollisionDialog = null
                        )
                    }
                }

                is SearchUsdaFoodsByBarcodeUseCase.Result.Blocked -> {
                    Log.d("Meow", "FoodEditor> USDA search BLOCKED barcode='$cleaned' reason='${r.reason}'")
                    val msg = if (existing != null && existing.source == BarcodeMappingSource.USER_ASSIGNED) {
                        "Barcode already assigned to a manual food (foodId=${existing.foodId}). You can open it to manage/unassign, or assign this barcode elsewhere."
                    } else r.reason

                    update {
                        it.copy(
                            scannedBarcode = cleaned,
                            isBarcodeFallbackOpen = true,
                            barcodeFallbackMessage = msg,
                            barcodeFallbackCreateName = "",
                            barcodeAlreadyAssignedFoodId = existing?.foodId,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            errorMessage = null
                        )
                    }
                }

                is SearchUsdaFoodsByBarcodeUseCase.Result.Failed -> {
                    Log.d("Meow", "FoodEditor> USDA search FAILED barcode='$cleaned' message='${r.message}'")
                    val msg = if (existing != null && existing.source == BarcodeMappingSource.USER_ASSIGNED) {
                        "Barcode already assigned to a manual food (foodId=${existing.foodId}). USDA lookup failed: ${r.message}"
                    } else r.message

                    update {
                        it.copy(
                            scannedBarcode = cleaned,
                            isBarcodeFallbackOpen = true,
                            barcodeFallbackMessage = msg,
                            barcodeFallbackCreateName = "",
                            barcodeAlreadyAssignedFoodId = existing?.foodId,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    fun onPickBarcodeCandidate(fdcId: Long) {
        val json = state.value.pendingUsdaSearchJson ?: run {
            update { it.copy(errorMessage = "Missing pending USDA JSON.") }
            return
        }

        val barcode = state.value.scannedBarcode.trim()
        if (barcode.isBlank()) {
            update { it.copy(errorMessage = "Missing scanned barcode.") }
            return
        }

        val picked = state.value.barcodePickItems.firstOrNull { it.fdcId == fdcId }
        if (picked == null) {
            update { it.copy(errorMessage = "Selected candidate not found.") }
            return
        }

        val incoming = ResolveBarcodeWithUsdaUseCase.UsdaBarcodeCandidateMeta(
            fdcId = picked.fdcId,
            gtinUpc = picked.gtinUpc.ifBlank { null },
            publishedDateIso = picked.publishedDateIso,
            modifiedDateIso = picked.modifiedDateIso,
            description = picked.description.ifBlank { null },
            brand = picked.brand.ifBlank { null }
        )

        fun candidateLabel(p: SearchUsdaFoodsByBarcodeUseCase.PickItem): String {
            val name = p.description.trim()
            val brand = p.brand.trim()
            return when {
                name.isBlank() && brand.isBlank() -> "USDA item (fdcId=${p.fdcId})"
                brand.isBlank() -> name
                name.isBlank() -> brand
                else -> "$name ($brand)"
            }
        }

        viewModelScope.launch {
            when (val decision = resolveBarcodeWithUsda.resolveCandidateChosen(barcode, incoming)) {
                is ResolveBarcodeWithUsdaUseCase.Result.ProceedToImport -> {
                    when (val r = importUsdaFromSearchJson(json, selectedFdcId = fdcId)) {
                        is ImportUsdaFoodFromSearchJsonUseCase.Result.Success -> {
                            val now = System.currentTimeMillis()

                            foodBarcodeRepo.upsertAndTouch(
                                entity = FoodBarcodeEntity(
                                    barcode = barcode,
                                    foodId = r.foodId,
                                    source = BarcodeMappingSource.USDA,
                                    usdaFdcId = r.fdcId,
                                    usdaPublishedDateIso = r.publishedDateIso ?: picked.publishedDateIso,
                                    assignedAtEpochMs = now,
                                    lastSeenAtEpochMs = now
                                ),
                                nowEpochMs = now
                            )

                            update {
                                it.copy(
                                    isBarcodeScannerOpen = false,
                                    pendingUsdaSearchJson = null,
                                    barcodePickItems = emptyList(),
                                    barcodeCollisionDialog = null,
                                    errorMessage = null
                                )
                            }

                            load(foodId = r.foodId, initialName = null, force = true)
                        }

                        is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked -> {
                            update { it.copy(errorMessage = r.reason) }
                        }
                    }
                }

                is ResolveBarcodeWithUsdaUseCase.Result.OpenExisting -> {
                    val now = System.currentTimeMillis()
                    foodBarcodeRepo.touchLastSeen(decision.barcode, now)

                    update {
                        it.copy(
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            barcodeCollisionDialog = null,
                            errorMessage = null
                        )
                    }

                    load(foodId = decision.foodId, initialName = null, force = true)
                }

                is ResolveBarcodeWithUsdaUseCase.Result.NeedsCollisionPrompt -> {
                    update {
                        it.copy(
                            pendingUsdaSearchJson = it.pendingUsdaSearchJson,
                            barcodePickItems = emptyList(),
                            barcodeCollisionDialog = BarcodeCollisionDialogState(
                                barcode = decision.barcode,
                                existingFoodId = decision.existingFoodId,
                                existingSource = decision.existingSource,
                                incomingFdcId = decision.incoming.fdcId,
                                incomingPublishedDateIso = decision.incoming.publishedDateIso,
                                incomingLabel = candidateLabel(picked),
                                reason = decision.reason,
                            ),
                            errorMessage = null
                        )
                    }
                }

                is ResolveBarcodeWithUsdaUseCase.Result.Blocked -> {
                    update {
                        it.copy(
                            isBarcodeFallbackOpen = true,
                            barcodeFallbackMessage = decision.reason,
                            barcodeFallbackCreateName = "",
                            barcodeAlreadyAssignedFoodId = null
                        )
                    }
                }
            }
        }
    }

    fun assignBarcodeToCurrentFood(barcode: String) {
        val foodId = _state.value.foodId ?: return
        val code = barcode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val existing = foodBarcodeRepo.getByBarcode(code)
            if (existing != null && existing.foodId != foodId) {
                pendingRemapBarcode = code
                pendingRemapTargetFoodId = foodId
                update {
                    it.copy(
                        barcodeRemapDialog = BarcodeRemapDialogState(
                            barcode = code,
                            fromFoodId = existing.foodId,
                            toFoodId = foodId,
                            fromSource = existing.source
                        )
                    )
                }
                return@launch
            }

            foodBarcodeRepo.upsertAndTouch(
                entity = FoodBarcodeEntity(
                    barcode = code,
                    foodId = foodId,
                    source = BarcodeMappingSource.USER_ASSIGNED,
                    usdaFdcId = null,
                    usdaPublishedDateIso = null,
                    assignedAtEpochMs = now,
                    lastSeenAtEpochMs = now
                ),
                nowEpochMs = now
            )

            val refreshed = foodBarcodeRepo.getAllBarcodesForFood(foodId).map { it.barcode }
            update { it.copy(assignedBarcodes = refreshed, scannedBarcode = code) }
        }
    }

    init {
        viewModelScope.launch {
            nutrientResultsFlow.collect { results ->
                update {
                    it.copy(
                        nutrientSearchResults = results.map { n ->
                            NutrientSearchResultUi(
                                id = n.id,
                                code = n.code,
                                name = n.displayName,
                                unit = n.unit,
                                category = n.category,
                                aliases = n.aliases
                            )
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            savedState.getStateFlow<Any?>(KEY_BARCODE_ASSIGN_FOOD_ID, null)
                .collect { raw ->
                    val pickedFoodId: Long = when (raw) {
                        is Long -> raw
                        is Int -> raw.toLong()
                        is String -> raw.toLongOrNull() ?: return@collect
                        else -> return@collect
                    }

                    Log.d("Meow", "BarcodeAssign> pickedFoodId=$pickedFoodId (raw=$raw)")

                    val barcodeFromSaved = savedState.get<String>(KEY_BARCODE_ASSIGN_BARCODE)
                        ?.trim()
                        .orEmpty()

                    val barcode = barcodeFromSaved.ifBlank { state.value.scannedBarcode.trim() }

                    savedState[KEY_BARCODE_ASSIGN_FOOD_ID] = null
                    savedState[KEY_BARCODE_ASSIGN_BARCODE] = null

                    if (barcode.isBlank()) {
                        update { it.copy(errorMessage = "Missing barcode to assign.") }
                        return@collect
                    }

                    val now = System.currentTimeMillis()

                    foodBarcodeRepo.upsertAndTouch(
                        entity = FoodBarcodeEntity(
                            barcode = barcode,
                            foodId = pickedFoodId,
                            source = BarcodeMappingSource.USER_ASSIGNED,
                            usdaFdcId = null,
                            usdaPublishedDateIso = null,
                            assignedAtEpochMs = now,
                            lastSeenAtEpochMs = now
                        ),
                        nowEpochMs = now
                    )

                    val refreshed = foodBarcodeRepo.getAllBarcodesForFood(pickedFoodId).map { it.barcode }

                    update {
                        it.copy(
                            scannedBarcode = barcode,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            errorMessage = null,
                            isBarcodeScannerOpen = false,
                            isBarcodeFallbackOpen = false,
                            assignedBarcodes = refreshed
                        )
                    }

                    load(foodId = pickedFoodId, initialName = null, true)
                }
        }

        viewModelScope.launch {
            savedState.getStateFlow<Long?>(KEY_MERGE_PICK_CANONICAL_FOOD_ID, null)
                .collect { canonicalFoodId ->
                    val pickedId = canonicalFoodId ?: return@collect
                    savedState[KEY_MERGE_PICK_CANONICAL_FOOD_ID] = null

                    val overrideFoodId = _state.value.foodId ?: return@collect
                    if (overrideFoodId == pickedId) {
                        update { it.copy(errorMessage = "Cannot merge a food into itself.") }
                        return@collect
                    }

                    mergeIntoFood(canonicalFoodId = pickedId)
                }
        }
    }
}

/**
 * FUTURE-YOU NOTE (2026-02-06):
 *
 * - Editor nutrient inputs are PER-SERVING UI values.
 * - Canonical math must stay out of UI: VM converts per-serving -> PER_100G or PER_100ML.
 * - Ambiguous volume units (cup/tbsp/fl oz/can/bottle) must prompt SOLID vs LIQUID
 *   instead of assuming PER_100ML.
 * - Never guess density. Never grams↔mL conversions.
 */