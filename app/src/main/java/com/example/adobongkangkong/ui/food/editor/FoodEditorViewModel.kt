package com.example.adobongkangkong.ui.food.editor

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.model.AliasAddResult
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.NutrientBasisScaler
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
import com.example.adobongkangkong.domain.usda.ImportUsdaFoodFromSearchJsonUseCase
import com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByBarcodeUseCase
import com.example.adobongkangkong.domain.usecase.GetFoodEditorDataUseCase
import com.example.adobongkangkong.domain.usecase.HardDeleteFoodIfUnusedUseCase
import com.example.adobongkangkong.domain.usecase.SaveFoodWithNutrientsUseCase
import com.example.adobongkangkong.domain.usecase.SearchNutrientsUseCase
import com.example.adobongkangkong.domain.usecase.SoftDeleteFoodUseCase
import com.example.adobongkangkong.ui.common.banner.BannerSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
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
    private val foodBarcodeRepo: FoodBarcodeRepository,
    private val softDeleteFood: SoftDeleteFoodUseCase,
    private val hardDeleteFoodIfUnused: HardDeleteFoodIfUnusedUseCase,
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
                else searchNutrients(q, limit = 80)
            }

    val selectedAliases: StateFlow<List<String>> =
        aliasSheetNutrientIdFlow
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else nutrientAliasRepo.observeAliases(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(foodId: Long?, initialName: String?) {
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

            _state.value = current.copy(
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
                nutrientRows = sortNutrientRows(rows.map { r ->
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
                        name = r.nutrient.displayName,
                        unit = r.nutrient.unit,
                        category = r.nutrient.category,
                        amount = displayAmount.toString()
                    )
                }),
                favorite = flags?.favorite ?: false,
                eatMore = flags?.eatMore ?: false,
                limit = flags?.limit ?: false,
                hasUnsavedChanges = false,
            )
        }
    }

    fun onPickBasisType(type: BasisType) { update {
        it.copy(
            hasUnsavedChanges = it.basisType != type || it.gramsPerServingUnit.isNotBlank() || it.mlPerServingUnit.isNotBlank(),
            basisType = type,
            // Invariant: only keep the bridge for the active basis
            gramsPerServingUnit = if (type == BasisType.PER_100ML) "" else it.gramsPerServingUnit,
            mlPerServingUnit = if (type == BasisType.PER_100G) "" else it.mlPerServingUnit,
        )
    } }

    fun closeGroundingDialog() { update { it.copy(isGroundingDialogOpen = false) }}

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

            // Convenience: if LIQUID + known volume unit, prefill ml-per-1-unit using pure volume conversion.
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

        // Always mark unsaved if user typed something
        val base = s.copy(hasUnsavedChanges = true)

        // If we cannot compute yet (partial input, blank, etc.), just keep state
        if (totalMl == null || totalMl <= 0.0 || servingSize == null || servingSize <= 0.0) {
            return@update base
        }

        val perUnit = totalMl / servingSize

        base.copy(
            mlPerServingUnit = perUnit.roundForUi() // use your existing rounding helper
        )
    }

    fun dismissGroundingDialog() = update { it.copy(isGroundingDialogOpen = false) }

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
                nutrientRows = sortNutrientRows((s.nutrientRows + NutrientRowUi(
                    nutrientId = n.id,
                    name = n.name,
                    unit = n.unit,
                    category = n.category,
                    amount = ""
                )))
            )
        }
    }

    private fun sortNutrientRows(rows: List<NutrientRowUi>): List<NutrientRowUi> {
        fun macroRank(name: String): Int = when (name.trim().lowercase()) {
            "calories" -> 0
            "carbohydrates" -> 2
            "protein" -> 1
            "fat" -> 3
            else -> Int.MAX_VALUE
        }

        return rows.sortedWith(
            compareBy<NutrientRowUi> { macroRank(it.name) }
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
            (isAmbiguousForGrounding(unit)) &&
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
            isAmbiguousForGrounding(s.servingUnit) &&
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

        val id = s.foodId ?: 0L
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
                    code = "",
                    displayName = ui.name,
                    unit = ui.unit,
                    category = ui.category
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
        _state.value = block(_state.value)
    }

    private fun Double.roundForUi(): String {
        return "%,.2f".format(this).replace(",", "")
    }

    private fun isAmbiguousForGrounding(unit: ServingUnit): Boolean {
        return when (unit) {
            ServingUnit.TSP_US,
            ServingUnit.TBSP_US,
            ServingUnit.FL_OZ_US,
            ServingUnit.CUP_US,
            ServingUnit.CUP_METRIC,
            ServingUnit.CUP_JP,
            ServingUnit.RCCUP,
            ServingUnit.TSP,
            ServingUnit.TBSP,
            ServingUnit.CUP,
            ServingUnit.CAN,
            ServingUnit.BOTTLE,
            ServingUnit.JAR -> true
            else -> false
        }
    }

    private fun isVolumeGrounded(servingUnit: ServingUnit, mlPerServingUnit: Double?): Boolean {
        // Do NOT treat “cup/tbsp/fl oz” as volume-grounded by default.
        // Those units are ambiguous for user-entered foods; only ml/L or an explicit ml bridge is volume grounding.
        return mlPerServingUnit != null || servingUnit == ServingUnit.ML || servingUnit == ServingUnit.L
    }

    private fun mlPerServingUnitEffective(servingUnit: ServingUnit, mlPerServingUnit: Double?): Double? {
        return when (servingUnit) {
            ServingUnit.ML -> 1.0
            ServingUnit.L -> 1000.0
            else -> mlPerServingUnit
        }?.takeIf { it > 0.0 }
    }

    // Barcode flow (unchanged)
    fun openBarcodeScanner() = update { it.copy(isBarcodeScannerOpen = true, errorMessage = null) }

    fun closeBarcodeScanner() = update {
        it.copy(
            isBarcodeScannerOpen = false,
            pendingUsdaSearchJson = null,
            barcodePickItems = emptyList()
        )
    }

    fun onBarcodeScanned(barcode: String) {
        update { it.copy(isBarcodeScannerOpen = false) }
        viewModelScope.launch {
            when (val r = searchUsdaByBarcode(barcode)) {
                is SearchUsdaFoodsByBarcodeUseCase.Result.Success -> {
                    update {
                        it.copy(
                            scannedBarcode = r.scannedBarcode,
                            pendingUsdaSearchJson = r.searchJson,
                            barcodePickItems = r.candidates,
                            isBarcodeScannerOpen = false
                        )
                    }
                    if (r.candidates.size == 1) {
                        onPickBarcodeCandidate(r.candidates.first().fdcId)
                    }
                }
                is SearchUsdaFoodsByBarcodeUseCase.Result.Blocked ->
                    update { it.copy(errorMessage = r.reason) }
                is SearchUsdaFoodsByBarcodeUseCase.Result.Failed ->{
                    update {
                        it.copy(
                            scannedBarcode = barcode,
                            errorMessage = r.message
                        )
                    }
                    assignBarcodeToExistingFlow.value = barcode
                }
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
        viewModelScope.launch {
            savedState.getStateFlow<Long?>("barcode_assign_foodId", savedState.get<Long>("barcode_assign_foodId")).collect { pickedFoodId ->
                Log.d("Meow", "BarcodeAssign> pickedFoodId=$pickedFoodId")
                if (pickedFoodId == null) return@collect

                val barcode = state.value.scannedBarcode?.trim().orEmpty()
                if (barcode.isBlank()) {
                    // Clear the one-shot result so we don't loop.
                    savedState["barcode_assign_foodId"] = null
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

                // Clear the one-shot result so we don't re-handle it.
                savedState["barcode_assign_foodId"] = null

                // Clean up any pending USDA UI state (keep minimal).
                update {
                    it.copy(
                        pendingUsdaSearchJson = null,
                        barcodePickItems = emptyList(),
                        errorMessage = null,
                        isBarcodeScannerOpen = false
                    )
                }

                // Open the picked food in the editor.
                load(foodId = pickedFoodId, initialName = null)
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
