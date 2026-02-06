package com.example.adobongkangkong.ui.food.editor

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.AliasAddResult
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.nutrition.NutrientBasisScaler
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
import com.example.adobongkangkong.domain.usda.ImportUsdaFoodFromSearchJsonUseCase
import com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByBarcodeUseCase
import com.example.adobongkangkong.domain.usecase.GetFoodEditorDataUseCase
import com.example.adobongkangkong.domain.usecase.SaveFoodWithNutrientsUseCase
import com.example.adobongkangkong.domain.usecase.SearchNutrientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Food editor state holder.
 *
 * Key rule: Food.stableId is generated ONCE for new foods and then persists forever.
 * This allows logs/import/export to remain stable even if Room primary keys change.
 */
@HiltViewModel
class FoodEditorViewModel @Inject constructor(
    private val getData: GetFoodEditorDataUseCase,
    private val searchNutrients: SearchNutrientsUseCase,
    private val saveFoodWithNutrients: SaveFoodWithNutrientsUseCase,
    private val foodRepo: FoodRepository,
    private val nutrientAliasRepo: NutrientAliasRepository,
    private val flagsRepo: FoodGoalFlagsRepository,
    private val searchUsdaByBarcode: SearchUsdaFoodsByBarcodeUseCase,
    private val importUsdaFromSearchJson: ImportUsdaFoodFromSearchJsonUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodEditorState())
    val state: StateFlow<FoodEditorState> = _state

    @OptIn(FlowPreview::class)
    private val nutrientQueryFlow = MutableStateFlow("")

    private val aliasSheetMessageFlow = MutableStateFlow<String?>(null)
    val aliasSheetMessage: StateFlow<String?> = aliasSheetMessageFlow

    private val didDeleteFlow = MutableStateFlow(false)
    val didDelete: StateFlow<Boolean> = didDeleteFlow

    private val aliasSheetNutrientIdFlow = MutableStateFlow<Long?>(null)
    private val aliasSheetNutrientNameFlow = MutableStateFlow<String?>(null)
    val aliasSheetNutrientName: StateFlow<String?> = aliasSheetNutrientNameFlow

    @OptIn(FlowPreview::class)
    private val nutrientResultsFlow =
        nutrientQueryFlow
            .map { it.trim() }
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList())
                else searchNutrients(q, limit = 80) // Flow<List<Nutrient>>
            }

    val selectedAliases: StateFlow<List<String>> =
        aliasSheetNutrientIdFlow
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else nutrientAliasRepo.observeAliases(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Loads editor data for an existing food, or initializes defaults for a new food.
     * For new foods, a stableId is generated once and stored in state.
     */
    fun load(foodId: Long?, initialName: String?) {
        Log.d("Meow", "load(FOOD_EDITOR > foodId=$foodId) vm=${System.identityHashCode(this)} currentFoodId=${_state.value.foodId}")
        // If already loaded for this foodId, do nothing.
        val current = _state.value
        if (current.foodId == foodId && (foodId != null)) return

        viewModelScope.launch {
            val flags = if (foodId != null) flagsRepo.get(foodId) else null
            val data = getData(foodId)
            val food = data.food
            val rows = data.nutrients

            val stableId =
                food?.stableId
                    ?: current.stableId
                    ?: UUID.randomUUID().toString()
            val servingSize = food?.servingSize ?: 1.0
            val gramsPerServingUnit = food?.gramsPerServingUnit
            _state.value = current.copy(
                foodId = foodId,
                stableId = stableId,
                name = food?.name ?: (initialName ?: ""),
                brand = food?.brand.orEmpty(),
                servingSize = food?.servingSize?.toString() ?: "1.0",
                servingUnit = food?.servingUnit ?: ServingUnit.G,
                gramsPerServingUnit = current.gramsPerServingUnit.takeIf { it.isNotBlank() }
                    ?: food?.gramsPerServingUnit?.toString().orEmpty(),
                servingsPerPackage = current.servingsPerPackage.takeIf { it.isNotBlank() }
                    ?: food?.servingsPerPackage?.toString().orEmpty(),
                nutrientRows = rows.map { r ->
                    val displayAmount =
                        when (r.basisType) {
                            BasisType.PER_100G -> NutrientBasisScaler
                                .canonicalToDisplayPerServing(
                                    storedAmount = r.amount,
                                    storedBasis = r.basisType,
                                    servingSize = servingSize,
                                    gramsPerServingUnit = gramsPerServingUnit
                                ).amount

                            // if you ever store PER_100ML, use the volume scaler here (you’d need mlPerServingUnit)
                            else -> r.amount
                        }

                    NutrientRowUi(
                        nutrientId = r.nutrient.id,
                        name = r.nutrient.displayName,
                        unit = r.nutrient.unit,
                        category = r.nutrient.category,
                        amount = displayAmount.toString()
                    )
                },
                favorite = flags?.favorite ?: false,
                eatMore = flags?.eatMore ?: false,
                limit = flags?.limit ?: false,
                hasUnsavedChanges = false,
            )
        }
    }

    fun onNameChange(v: String) = update { it.copy(name = v, errorMessage = null, hasUnsavedChanges = true) }
    fun onBrandChange(v: String) = update { it.copy(brand = v, hasUnsavedChanges = true) }
    fun onServingSizeChange(v: String) = update { it.copy(servingSize = v, hasUnsavedChanges = true) }
    fun onServingUnitChange(v: ServingUnit) = update { it.copy(servingUnit = v, hasUnsavedChanges = true) }
    fun onGramsPerServingChange(v: String) = update { it.copy(gramsPerServingUnit = v, hasUnsavedChanges = true) }
    fun onServingsPerPackageChange(v: String) = update { it.copy(servingsPerPackage = v, hasUnsavedChanges = true) }

    // Flags
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
                nutrientRows = (s.nutrientRows + NutrientRowUi(
                    nutrientId = n.id,
                    name = n.name,
                    unit = n.unit,
                    category = n.category,
                    amount = ""
                )).sortedWith(compareBy({ it.category }, { it.name }))
            )
        }
    }

    fun save(onDone: (Long) -> Unit) {
        val s = _state.value
        val name = s.name.trim()
        if (name.isBlank()) {
            update { it.copy(errorMessage = "Food name is required.") }
            return
        }

        fun parseDoubleOrNull(x: String): Double? =
            x.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        val servingSize = parseDoubleOrNull(s.servingSize) ?: 1.0
        val gramsPerServingUnitInput = parseDoubleOrNull(s.gramsPerServingUnit)
        val servingsPerPackage = parseDoubleOrNull(s.servingsPerPackage)

        // Rule: if user provided gramsPerServingUnit, trust it.
        // If blank AND serving unit is a mass unit (e.g., OZ/LB), auto-compute grams per 1 unit.
        // If serving unit is G, we don't need gramsPerServingUnit (but for consistency we treat it as 1 g per 1 g).
        val gramsPerServingUnitFinal: Double? =
            when {
                gramsPerServingUnitInput != null && gramsPerServingUnitInput > 0.0 -> gramsPerServingUnitInput
                s.servingUnit.isMassUnit() && s.servingUnit != ServingUnit.G -> s.servingUnit.toGrams(1.0)
                else -> null
            }?.takeIf { it > 0.0 }

        // IMPORTANT:
        // - For new foods: id = 0L so Room auto-generates.
        // - stableId must be generated once and persisted.
        val id = s.foodId ?: 0L
        val stableId = s.stableId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        val food = Food(
            id = id,
            name = name,
            brand = s.brand.trim().ifEmpty { null },
            servingSize = servingSize,
            servingUnit = s.servingUnit,
            gramsPerServingUnit = gramsPerServingUnitFinal,
            servingsPerPackage = servingsPerPackage,
            isRecipe = false,
            stableId = stableId,
        )

        // Decide default basis for nutrients *stored from this editor*.
        // Editor amounts are per-serving UI amounts.
        // If we can ground serving in grams, store canonical PER_100G by converting the UI per-serving amount -> per-100g.
        // Otherwise store USDA_REPORTED_SERVING amounts as-is (use case can later canonicalize once grounded).
        val defaultBasisType: BasisType =
            if (s.servingUnit == ServingUnit.G || gramsPerServingUnitFinal != null) {
                BasisType.PER_100G
            } else {
                BasisType.USDA_REPORTED_SERVING
            }

        val gramsPerServingUnitEffective: Double? =
            when (s.servingUnit) {
                ServingUnit.G -> 1.0
                else -> gramsPerServingUnitFinal
            }?.takeIf { it > 0.0 }

        val rows: List<FoodNutrientRow> = s.nutrientRows.mapNotNull { ui ->
            val amt = ui.amount.trim()
            val uiPerServingAmount = if (amt.isEmpty()) 0.0 else (amt.toDoubleOrNull() ?: return@mapNotNull null)

            val amountToStore =
                if (defaultBasisType == BasisType.PER_100G) {
                    NutrientBasisScaler.displayPerServingToCanonical(
                        uiPerServingAmount = uiPerServingAmount,
                        canonicalBasis = BasisType.PER_100G,
                        servingSize = servingSize,
                        gramsPerServingUnit = gramsPerServingUnitEffective
                    ).amount
                } else {
                    uiPerServingAmount
                }

            FoodNutrientRow(
                nutrient = Nutrient(
                    id = ui.nutrientId,
                    code = "", // editor doesn’t need code to save
                    displayName = ui.name,
                    unit = ui.unit,
                    category = ui.category
                ),
                amount = amountToStore,
                basisType = defaultBasisType,
                basisGrams = if (defaultBasisType == BasisType.PER_100G) 100.0 else null
            )
        }

        viewModelScope.launch {
            update { it.copy(isSaving = true, errorMessage = null, stableId = stableId) }
            try {
                val savedId = saveFoodWithNutrients(food, rows)
                // Save flags separately
                flagsRepo.setFlags(
                    foodId = savedId,
                    favorite = s.favorite,
                    eatMore = s.eatMore,
                    limit = s.limit
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

    fun deleteFood() {
        val foodId = _state.value.foodId ?: return

        viewModelScope.launch {
            // Reuse isSaving to disable actions while deleting.
            update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val deleted = foodRepo.deleteFood(foodId)
                if (deleted) {
                    didDeleteFlow.value = true
                } else {
                    update {
                        it.copy(
                            errorMessage = "Cannot delete: this food is used in one or more recipes."
                        )
                    }
                }
            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to delete food.") }
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

    private fun update(block: (FoodEditorState) -> FoodEditorState) {
        _state.value = block(_state.value)
    }

    fun openLbDialog() = update {
        it.copy(isLbDialogOpen = true, lbInputText = "")
    }

    fun closeLbDialog() = update {
        it.copy(isLbDialogOpen = false)
    }

    fun onLbInputChange(v: String) = update {
        it.copy(lbInputText = v)
    }

    fun confirmLbToGrams() {
        val raw = state.value.lbInputText
        val pounds = raw.toPoundsOrNull()
            ?: run {
                update { it.copy(errorMessage = "Enter pounds like 1.5") }
                return
            }

        val grams = pounds * 453.59237

        // if your grams field is string-backed:
        onGramsPerServingChange(grams.roundForUi())

        // close dialog
        update { it.copy(isLbDialogOpen = false) }
    }

    private fun String.toPoundsOrNull(): Double? {
        // accepts: "1.5", "1.5lb", " 1.5 lb "
        val cleaned = trim()
            .lowercase()
            .replace("lbs", "")
            .replace("lb", "")
            .trim()

        return cleaned.toDoubleOrNull()
    }

    private fun Double.roundForUi(): String {
        // simple: 2 decimals; you can tune
        return "%,.2f".format(this).replace(",", "")
    }

    fun openBarcodeScanner() = update {
        it.copy(
            isBarcodeScannerOpen = true,
            errorMessage = null
        )
    }

    fun closeBarcodeScanner() = update {
        it.copy(
            isBarcodeScannerOpen = false,
            pendingUsdaSearchJson = null,
            barcodePickItems = emptyList()
        )
    }

    fun onBarcodeScanned(barcode: String) {
        Log.d("Meow", "FOOD_EDITOR > onBarcodeScanned($barcode) vm=${System.identityHashCode(this)}")
        update { it.copy(isBarcodeScannerOpen = false) }
        viewModelScope.launch {
            when (val r = searchUsdaByBarcode(barcode)) {
                is SearchUsdaFoodsByBarcodeUseCase.Result.Success -> {
                    // Store barcode + JSON + candidates (UI-only)
                    update {
                        it.copy(
                            scannedBarcode = r.scannedBarcode,
                            pendingUsdaSearchJson = r.searchJson,
                            barcodePickItems = r.candidates,
                            isBarcodeScannerOpen = false
                        )
                    }

                    if (r.candidates.size == 1) {
                        // Auto import if unambiguous
                        onPickBarcodeCandidate(r.candidates.first().fdcId)
                    }
                }

                is SearchUsdaFoodsByBarcodeUseCase.Result.Blocked ->
                    update { it.copy(errorMessage = r.reason) }

                is SearchUsdaFoodsByBarcodeUseCase.Result.Failed ->
                    update { it.copy(errorMessage = r.message) }
            }
        }
    }

    fun onPickBarcodeCandidate(fdcId: Long) {
        val json = state.value.pendingUsdaSearchJson ?: run {
            update { it.copy(errorMessage = "Missing pending USDA JSON.") }
            return
        }

        viewModelScope.launch {
            when (val r = importUsdaFromSearchJson(json, selectedFdcId = fdcId)) {
                is ImportUsdaFoodFromSearchJsonUseCase.Result.Success -> {
                    // Close scanner/picker and load imported food into editor
                    update {
                        it.copy(
                            isBarcodeScannerOpen = false,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            errorMessage = null,
                        )
                    }
                    load(foodId = r.foodId, initialName = null)
                }

                is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked ->
                    update { it.copy(errorMessage = r.reason) }
            }
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
    }
}

/**
 * FUTURE-YOU NOTE (2026-02-06):
 *
 * Editor nutrient inputs are PER-SERVING UI values.
 *
 * - If we store PER_100G in DB, we MUST convert UI per-serving -> canonical per-100g (do not just relabel basisType).
 * - gramsPerServingUnit means: grams per 1 unit of servingUnit (NOT grams for the whole serving).
 * - If gramsPerServingUnit is blank and servingUnit is a mass unit (OZ/LB), auto-compute grams per 1 unit using ServingUnit.toGrams(1.0).
 * - Never guess density for volume units (CUP/TBSP/TSP/FLOZ). If not grounded, keep USDA_REPORTED_SERVING.
 */
