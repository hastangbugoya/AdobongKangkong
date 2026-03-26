package com.example.adobongkangkong.ui.food.editor
/**
 * Saves a [Food] and (optionally) its nutrient rows using canonical single-basis rules.
 *
 * ## Purpose
 * This use case persists:
 * - Food metadata (always)
 * - Nutrient rows (only when explicitly provided by caller)
 *
 * ## Critical behavior contract (Food Editor integration)
 *
 * The caller (FoodEditorViewModel) is responsible for deciding:
 *
 * ### 1. Metadata-only changes
 * - servingSize
 * - servingUnit
 * - gramsPerServingUnit
 * - mlPerServingUnit
 *
 * → Call saveFoodMetadata ONLY
 * → Existing canonical nutrients must remain unchanged
 *
 * ### 2. Nutrient edits present
 * → Call SaveFoodWithNutrientsUseCase
 * → Nutrient rows are treated as user-intended per-serving values
 * → Rows are converted into canonical PER_100G / PER_100ML / USDA basis and persisted
 *
 * ## Why this split exists
 * - Serving metadata is a DISPLAY SCALING mechanism, not nutrition data
 * - Rewriting nutrients on metadata-only edits corrupts canonical values
 * - Canonical nutrients must only change when user edits nutrient values
 *
 * ## Canonical guarantees
 * - Single basis per food (PER_100G / PER_100ML / USDA)
 * - No grams↔mL conversion without explicit density
 * - Exactly one row per nutrient id
 */
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
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.fromUsda
import com.example.adobongkangkong.domain.model.isAmbiguousForGrounding
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.ApplyEditedNutrientsUseCase
import com.example.adobongkangkong.domain.nutrition.NutrientBasisScaler
import com.example.adobongkangkong.domain.nutrition.RecomputeDisplayedNutrientsUseCase
import com.example.adobongkangkong.domain.nutrition.ResolveServingGroundingUseCase
import com.example.adobongkangkong.domain.nutrition.ServingResolution
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodCategoryRepository
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.usda.AdoptUsdaBarcodePackageIntoFoodUseCase
import com.example.adobongkangkong.domain.usda.AssignBarcodeToFoodUseCase
import com.example.adobongkangkong.domain.usda.BackfillUsdaNutrientsIntoFoodUseCase
import com.example.adobongkangkong.domain.usda.ImportUsdaFoodFromSearchJsonUseCase
import com.example.adobongkangkong.domain.usda.ResolveBarcodeWithUsdaUseCase
import com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByBarcodeUseCase
import com.example.adobongkangkong.domain.usda.model.BarcodeRemapDialogState
import com.example.adobongkangkong.domain.usecase.GetFoodEditorDataUseCase
import com.example.adobongkangkong.domain.usecase.HardDeleteFoodIfUnusedUseCase
import com.example.adobongkangkong.domain.usecase.SaveFoodMetadataUseCase
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
    private val saveFoodMetadata: SaveFoodMetadataUseCase,
    private val foodRepo: FoodRepository,
    private val foodCategoryRepo: FoodCategoryRepository,
    private val nutrientAliasRepo: NutrientAliasRepository,
    private val flagsRepo: FoodGoalFlagsRepository,
    private val searchUsdaByBarcode: SearchUsdaFoodsByBarcodeUseCase,
    private val importUsdaFromSearchJson: ImportUsdaFoodFromSearchJsonUseCase,
    private val foodBarcodeRepo: FoodBarcodeRepository,
    private val assignBarcodeToFood: AssignBarcodeToFoodUseCase,
    private val adoptUsdaBarcodePackageIntoFood: AdoptUsdaBarcodePackageIntoFoodUseCase,
    private val backfillUsdaNutrientsIntoFood: BackfillUsdaNutrientsIntoFoodUseCase,
    private val softDeleteFood: SoftDeleteFoodUseCase,
    private val hardDeleteFoodIfUnused: HardDeleteFoodIfUnusedUseCase,
    private val resolveBarcodeWithUsda: ResolveBarcodeWithUsdaUseCase,
    private val nutrientRepo: NutrientRepository,
    private val mergeFoodsUseCase: MergeFoodsUseCase,
    private val resolveServingGrounding: ResolveServingGroundingUseCase,
    private val recomputeDisplayedNutrients: RecomputeDisplayedNutrientsUseCase,
    private val applyEditedNutrients: ApplyEditedNutrientsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodEditorState())
    val state: StateFlow<FoodEditorState> = _state

    private val savedState = savedStateHandle

    private val assignBarcodeToExistingFlow = MutableStateFlow<String?>(null)
    val assignBarcodeToExistingBarcode: StateFlow<String?> = assignBarcodeToExistingFlow

    private val openFoodEditorRequestFlow = MutableStateFlow<Long?>(null)
    val openFoodEditorRequest: StateFlow<Long?> = openFoodEditorRequestFlow

    /**
     * Snapshot of the last loaded persisted food.
     *
     * Purpose
     * - Prevent no-op saves (or non-bridge-only edits like Favorite) from silently introducing
     *   inferred bridge values such as mlPerServingUnit=15 for ambiguous units like TBSP.
     * - Compare current bridge text fields against what was actually loaded from persistence,
     *   not against any inferred/current UI state.
     */
    private var loadedFoodSnapshot: Food? = null
    private var loadedBasisTypeSnapshot: BasisType? = null

    private var loadedCanonicalNutrientsSnapshot: Map<NutrientKey, Double> = emptyMap()

    fun consumeAssignBarcodeToExistingRequest() {
        assignBarcodeToExistingFlow.value = null
    }

    fun consumeOpenFoodEditorRequest() {
        openFoodEditorRequestFlow.value = null
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

        if (!force) {
            if (foodId == null && current.foodId != null) return
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
                gramsPerServingUnitEffective(
                    servingUnit = servingUnit,
                    gramsPerServingUnit = gramsPerServingUnit
                )

            val mlPerServingUnitEffectiveForDisplay: Double? =
                mlPerServingUnitEffective(
                    servingUnit = servingUnit,
                    mlPerServingUnit = mlPerServingUnit
                )

            val gramsPerServingEffectiveForDisplay: Double? =
                when {
                    servingUnit.isMassUnit() -> servingSize.takeIf { it > 0.0 }
                    gramsPerServingUnitEffectiveForDisplay != null ->
                        (servingSize * gramsPerServingUnitEffectiveForDisplay).takeIf { it > 0.0 }
                    else -> null
                }

            val mlPerServingEffectiveForDisplay: Double? =
                when {
                    servingUnit.isVolumeUnit() && !servingUnit.isAmbiguousForGrounding() ->
                        servingSize.takeIf { it > 0.0 }
                    mlPerServingUnitEffectiveForDisplay != null ->
                        (servingSize * mlPerServingUnitEffectiveForDisplay).takeIf { it > 0.0 }
                    else -> null
                }

            val inferredBasisType: BasisType =
                when {
                    rows.any { it.basisType == BasisType.PER_100ML } -> BasisType.PER_100ML
                    rows.any { it.basisType == BasisType.PER_100G } -> BasisType.PER_100G
                    servingUnit.isAmbiguousForGrounding() &&
                            gramsPerServingUnitEffectiveForDisplay != null -> BasisType.PER_100G
                    servingUnit.isAmbiguousForGrounding() &&
                            mlPerServingUnitEffectiveForDisplay != null -> BasisType.PER_100ML
                    servingUnit.isVolumeUnit() && !servingUnit.isAmbiguousForGrounding() ->
                        BasisType.PER_100ML
                    gramsPerServingUnitEffectiveForDisplay != null -> BasisType.PER_100G
                    food == null -> current.basisType ?: BasisType.USDA_REPORTED_SERVING
                    else -> BasisType.USDA_REPORTED_SERVING
                }

            val assignedBarcodes = if (foodId != null) {
                buildAssignedBarcodeUiList(foodId)
            } else {
                emptyList()
            }

            loadedCanonicalNutrientsSnapshot = rows.associate { row ->
                NutrientKey(row.nutrient.code) to row.amount
            }

            Log.d(
                "FoodEditorDebug",
                "load foodId=$foodId servingSize=$servingSize servingUnit=$servingUnit " +
                        "gramsPerServingUnit=$gramsPerServingUnit mlPerServingUnit=$mlPerServingUnit " +
                        "gramsPerServingUnitEffectiveForDisplay=$gramsPerServingUnitEffectiveForDisplay " +
                        "mlPerServingUnitEffectiveForDisplay=$mlPerServingUnitEffectiveForDisplay " +
                        "gramsPerServingEffectiveForDisplay=$gramsPerServingEffectiveForDisplay " +
                        "mlPerServingEffectiveForDisplay=$mlPerServingEffectiveForDisplay " +
                        "inferredBasisType=$inferredBasisType"
            )

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
                                mlPerServingUnit = mlPerServingUnitEffectiveForDisplay
                            ).amount

                        BasisType.USDA_REPORTED_SERVING -> r.amount
                    }

                Log.d(
                    "FoodEditorDebug",
                    "nutrient=${r.nutrient.code} basis=${r.basisType} stored=${r.amount} display=$displayAmount"
                )

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

            loadedFoodSnapshot = food
            loadedBasisTypeSnapshot = inferredBasisType
            Log.d("FoodEditorDebug", "FINAL UI servingUnit=${food?.servingUnit} servingSize=${food?.servingSize} gramsPerServingUnit=${food?.gramsPerServingUnit}")
            val next = current.copy(
                hasLoaded = true,
                foodId = foodId,
                stableId = stableId,
                name = food?.name ?: (initialName ?: ""),
                brand = food?.brand.orEmpty(),
                servingSize = food?.servingSize?.toString() ?: "1.0",
                servingUnit = food?.servingUnit ?: ServingUnit.SERVING,
                basisType = inferredBasisType,
                groundingMode = when (inferredBasisType) {
                    BasisType.PER_100ML -> GroundingMode.LIQUID
                    BasisType.PER_100G, BasisType.USDA_REPORTED_SERVING -> GroundingMode.SOLID
                },
                gramsPerServingUnit = current.gramsPerServingUnit.takeIf { it.isNotBlank() && food == null }
                    ?: food?.gramsPerServingUnit?.toString().orEmpty(),
                mlPerServingUnit = current.mlPerServingUnit.takeIf { it.isNotBlank() && food == null }
                    ?: food?.mlPerServingUnit?.toString().orEmpty(),
                servingsPerPackage = current.servingsPerPackage.takeIf { it.isNotBlank() && food == null }
                    ?: food?.servingsPerPackage?.toString().orEmpty(),
                servingDraft = ServingDraftState(
                    servingSize = food?.servingSize?.toString() ?: "1.0",
                    servingUnit = food?.servingUnit ?: ServingUnit.SERVING,
                    gramsPerServingUnit = current.gramsPerServingUnit.takeIf { it.isNotBlank() && food == null }
                        ?: food?.gramsPerServingUnit?.toString().orEmpty(),
                    mlPerServingUnit = current.mlPerServingUnit.takeIf { it.isNotBlank() && food == null }
                        ?: food?.mlPerServingUnit?.toString().orEmpty(),
                    servingsPerPackage = current.servingsPerPackage.takeIf { it.isNotBlank() && food == null }
                        ?: food?.servingsPerPackage?.toString().orEmpty(),
                ),
                nutrientDraft = NutrientDraftState(rows = sortNutrientRows(editorRows)),
                nutritionEditorStatus = NutritionEditorStatusState(
                    isServingDirty = false,
                    isNutrientsDirty = false,
                    hasPendingRecompute = false,
                    hasPendingApply = false,
                    showDiscardNutrientEditsDialog = false,
                    servingResolution = null
                ),
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
                isFoodMetadataDirty = false,
                areNutrientsDirty = false,
                isBasisInterpretationDirty = false,
                assignedBarcodes = assignedBarcodes,
                barcodeActionMessage = current.barcodeActionMessage,
                barcodePackageEditor = null,
                scannedBarcode = current.scannedBarcode
            )

            val resolvedNext = next.copy(
                nutritionEditorStatus = next.nutritionEditorStatus.copy(
                    servingResolution = computeServingResolution(next)
                )
            )

            _state.value = applyNeedsFix(resolvedNext, current)
        }
    }

    private fun computeServingResolution(state: FoodEditorState): ServingResolution? {
        val size = state.servingSize.toDoubleOrNull() ?: return null

        val grams = state.gramsPerServingUnit.trim().toDoubleOrNull()
        val ml = state.mlPerServingUnit.trim().toDoubleOrNull()

        return resolveServingGrounding.execute(
            servingSize = size,
            servingUnit = state.servingUnit,
            gramsPerServingUnit = grams,
            millilitersPerServingUnit = ml
        )
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

    fun dismissBarcodeCollision() {
        update { it.copy(barcodeCollisionDialog = null) }
    }

    fun dismissNeedsFixBanner() {
        update { it.copy(fixBannerDismissed = true) }
    }

    fun dismissUsdaInterpretationPrompt() {
        update {
            it.copy(
                pendingUsdaInterpretationPrompt = null,
                pendingUsdaSearchJson = null,
                barcodePickItems = emptyList()
            )
        }
    }

    fun confirmUsdaInterpretationPrompt(choice: UsdaNutrientInterpretationChoice) {
        val s = _state.value
        val prompt = s.pendingUsdaInterpretationPrompt ?: return
        val json = s.pendingUsdaSearchJson ?: run {
            update {
                it.copy(
                    pendingUsdaInterpretationPrompt = null,
                    errorMessage = "Missing pending USDA JSON for interpretation."
                )
            }
            return
        }

        val barcode = s.scannedBarcode.trim()
        if (barcode.isBlank()) {
            update {
                it.copy(
                    errorMessage = "Missing scanned barcode."
                )
            }
            return
        }

        val forcedInterpretation = when (choice) {
            UsdaNutrientInterpretationChoice.PER_100 ->
                ImportUsdaFoodFromSearchJsonUseCase.InterpretationChoice.PER_100_STYLE

            UsdaNutrientInterpretationChoice.PER_SERVING ->
                ImportUsdaFoodFromSearchJsonUseCase.InterpretationChoice.PER_SERVING_STYLE
        }

        viewModelScope.launch {
            when (
                val result = importUsdaFromSearchJson(
                    searchJson = json,
                    selectedFdcId = prompt.selectedFdcId,
                    forcedInterpretation = forcedInterpretation
                )
            ) {
                is ImportUsdaFoodFromSearchJsonUseCase.Result.Success -> {
                    val now = System.currentTimeMillis()

                    foodBarcodeRepo.upsertAndTouch(
                        entity = FoodBarcodeEntity(
                            barcode = barcode,
                            foodId = result.foodId,
                            source = BarcodeMappingSource.USDA,
                            usdaFdcId = result.fdcId,
                            usdaPublishedDateIso = result.publishedDateIso,
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
                            errorMessage = null,
                            pendingUsdaBackfillPrompt = null,
                            pendingUsdaInterpretationPrompt = null
                        )
                    }

                    load(foodId = result.foodId, initialName = null, force = true)
                }

                is ImportUsdaFoodFromSearchJsonUseCase.Result.NeedsInterpretationChoice -> {
                    update {
                        it.copy(
                            pendingUsdaInterpretationPrompt = PendingUsdaInterpretationPromptState(
                                foodId = result.foodId,
                                selectedFdcId = result.fdcId,
                                candidateLabel = result.candidateLabel,
                                servingText = result.servingText,
                                calories = result.calories,
                                carbs = result.carbs,
                                protein = result.protein,
                                fat = result.fat
                            ),
                            errorMessage = null
                        )
                    }
                }

                is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked -> {
                    update { it.copy(errorMessage = result.reason) }
                }
            }
        }
    }

    fun dismissUsdaBackfillPrompt() {
        update {
            it.copy(
                pendingUsdaBackfillPrompt = null,
                pendingUsdaSearchJson = null
            )
        }
    }

    fun dismissUsdaBackfillMessage() {
        update { it.copy(usdaBackfillMessage = null) }
    }

    fun confirmUsdaBackfillPrompt() {
        val s = _state.value
        val foodId = s.foodId ?: run {
            update {
                it.copy(
                    pendingUsdaBackfillPrompt = null,
                    pendingUsdaSearchJson = null,
                    errorMessage = "Food must be loaded before USDA nutrient backfill."
                )
            }
            return
        }
        val prompt = s.pendingUsdaBackfillPrompt ?: return
        val json = s.pendingUsdaSearchJson ?: run {
            update {
                it.copy(
                    pendingUsdaBackfillPrompt = null,
                    errorMessage = "Missing pending USDA JSON for nutrient backfill."
                )
            }
            return
        }

        viewModelScope.launch {
            when (
                val result = backfillUsdaNutrientsIntoFood(
                    targetFoodId = foodId,
                    searchJson = json,
                    selectedFdcId = prompt.selectedFdcId
                )
            ) {
                is BackfillUsdaNutrientsIntoFoodUseCase.Result.Success -> {
                    val message = when {
                        result.insertedCount > 0 ->
                            "Filled missing nutrients from USDA for ${prompt.candidateLabel}."
                        else ->
                            "No new USDA nutrients were added. Existing nutrients were already present."
                    }

                    update {
                        it.copy(
                            pendingUsdaBackfillPrompt = null,
                            pendingUsdaSearchJson = null,
                            errorMessage = null,
                            usdaBackfillMessage = UsdaBackfillMessageState(
                                message = message,
                                insertedCount = result.insertedCount,
                                skippedExistingCount = result.skippedExistingCount
                            ),
                            barcodeActionMessage = if (result.insertedCount > 0) {
                                "USDA package adopted and missing nutrients filled."
                            } else {
                                "USDA package adopted. No missing nutrients were added."
                            }
                        )
                    }

                    load(foodId = foodId, initialName = null, force = true)
                }

                is BackfillUsdaNutrientsIntoFoodUseCase.Result.Blocked -> {
                    update {
                        it.copy(
                            pendingUsdaBackfillPrompt = null,
                            pendingUsdaSearchJson = null,
                            usdaBackfillMessage = UsdaBackfillMessageState(
                                message = result.reason,
                                insertedCount = 0,
                                skippedExistingCount = 0
                            ),
                            errorMessage = null
                        )
                    }
                }
            }
        }
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

        if (s.servingUnit.isAmbiguousForGrounding()) {
            return when (s.basisType) {
                BasisType.PER_100ML ->
                    if (ml == null) "Food needs mL per 1 ${s.servingUnit.display} when using PER 100mL grounding."
                    else null

                BasisType.PER_100G ->
                    if (grams == null) "Food needs grams per 1 ${s.servingUnit.display} when using PER 100g grounding."
                    else null

                BasisType.USDA_REPORTED_SERVING, null ->
                    "Serving unit needs grounding (PER 100g or PER 100mL) to be reliable for serving-based logging."
            }
        }

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
                            errorMessage = null,
                            pendingUsdaInterpretationPrompt = null
                        )
                    }

                    load(foodId = r.foodId, initialName = null, force = true)
                }

                is ImportUsdaFoodFromSearchJsonUseCase.Result.NeedsInterpretationChoice -> {
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
                            barcodePickItems = emptyList(),
                            errorMessage = null,
                            pendingUsdaInterpretationPrompt = PendingUsdaInterpretationPromptState(
                                foodId = r.foodId,
                                selectedFdcId = r.fdcId,
                                candidateLabel = r.candidateLabel,
                                servingText = r.servingText,
                                calories = r.calories,
                                carbs = r.carbs,
                                protein = r.protein,
                                fat = r.fat
                            )
                        )
                    }
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
                hasUnsavedChanges = true,
                isFoodMetadataDirty = true,
                isBasisInterpretationDirty = true,
                isGroundingDialogOpen = false,
                basisType = type,
                groundingMode = when (type) {
                    BasisType.PER_100ML -> GroundingMode.LIQUID
                    BasisType.PER_100G, BasisType.USDA_REPORTED_SERVING -> GroundingMode.SOLID
                },
                gramsPerServingUnit = if (type == BasisType.PER_100ML) "" else it.gramsPerServingUnit,
                mlPerServingUnit = if (type == BasisType.PER_100G) "" else it.mlPerServingUnit,
                errorMessage = null
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

                val createdRow = FoodBarcodeEntity(
                    barcode = barcode,
                    foodId = newFoodId,
                    source = BarcodeMappingSource.USER_ASSIGNED,
                    usdaFdcId = null,
                    usdaPublishedDateIso = null,
                    assignedAtEpochMs = now,
                    lastSeenAtEpochMs = now
                )

                foodBarcodeRepo.upsertAndTouch(
                    entity = createdRow,
                    nowEpochMs = now
                )

                val refreshed = buildAssignedBarcodeUiList(newFoodId)

                update {
                    it.copy(
                        isBarcodeFallbackOpen = false,
                        barcodeFallbackMessage = null,
                        barcodeFallbackCreateName = "",
                        pendingUsdaSearchJson = null,
                        barcodePickItems = emptyList(),
                        errorMessage = null,
                        isBarcodeScannerOpen = false,
                        assignedBarcodes = refreshed,
                        barcodePackageEditor = toBarcodePackageEditorState(createdRow),
                        scannedBarcode = barcode
                    )
                }

                load(foodId = newFoodId, initialName = null, force = true)
                openBarcodePackageEditor(barcode)
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

                val refreshed = buildAssignedBarcodeUiList(foodId)

                update {
                    it.copy(
                        assignedBarcodes = refreshed,
                        barcodePackageEditor = if (it.barcodePackageEditor?.barcode == code) null else it.barcodePackageEditor,
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

    fun onNameChange(v: String) = update {
        it.copy(
            name = v,
            errorMessage = null,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true
        )
    }

    fun onBrandChange(v: String) = update {
        it.copy(
            brand = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true
        )
    }

    fun onServingSizeChange(v: String) = update {
        it.copy(
            servingSize = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true,
            nutritionEditorStatus = it.nutritionEditorStatus.copy(
                isServingDirty = true,
                hasPendingRecompute = true
            )
        )
    }

    fun onServingUnitChange(v: ServingUnit) = update {
        it.copy(
            servingUnit = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true,
            nutritionEditorStatus = it.nutritionEditorStatus.copy(
                isServingDirty = true,
                hasPendingRecompute = true
            )
        )
    }

    fun onGramsPerServingChange(v: String) = update {
        it.copy(
            gramsPerServingUnit = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true,
            nutritionEditorStatus = it.nutritionEditorStatus.copy(
                isServingDirty = true,
                hasPendingRecompute = true
            )
        )
    }

    fun onMlPerServingChange(v: String) = update {
        it.copy(
            mlPerServingUnit = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true,
            nutritionEditorStatus = it.nutritionEditorStatus.copy(
                isServingDirty = true,
                hasPendingRecompute = true
            )
        )
    }

    fun onServingsPerPackageChange(v: String) = update {
        it.copy(
            servingsPerPackage = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true,
            servingDraft = it.servingDraft.copy(servingsPerPackage = v),
            nutritionEditorStatus = it.nutritionEditorStatus.copy(
                isServingDirty = true,
                hasPendingRecompute = true
            )
        )
    }

    fun onGroundingModeChange(v: GroundingMode) {
        update { s ->
            val next = s.copy(
                groundingMode = v,
                nutritionEditorStatus = s.nutritionEditorStatus.copy(
                    isServingDirty = true,
                    hasPendingRecompute = true
                )
            )

            if (v == GroundingMode.LIQUID && next.mlPerServingUnit.isBlank()) {
                val auto = next.servingUnit.toMilliliters(1.0)
                if (auto != null && auto > 0.0) {
                    return@update next.copy(
                        mlPerServingUnit = auto.roundForUi(),
                        hasUnsavedChanges = true,
                        isFoodMetadataDirty = true,
                        servingDraft = next.servingDraft.copy(mlPerServingUnit = auto.roundForUi()),
                        nutritionEditorStatus = next.nutritionEditorStatus.copy(
                            isServingDirty = true,
                            hasPendingRecompute = true
                        )
                    )
                }
            }
            next
        }
    }

    fun onMlPerServingTotalChange(totalMlText: String) = update { s ->
        val totalMl = totalMlText.toDoubleOrNull()
        val servingSize = s.servingSize.toDoubleOrNull()

        val base = s.copy(
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true,
            nutritionEditorStatus = s.nutritionEditorStatus.copy(
                isServingDirty = true,
                hasPendingRecompute = true
            )
        )

        if (totalMl == null || totalMl <= 0.0 || servingSize == null || servingSize <= 0.0) {
            return@update base
        }

        val perUnit = totalMl / servingSize

        base.copy(
            mlPerServingUnit = perUnit.roundForUi(),
            servingDraft = base.servingDraft.copy(mlPerServingUnit = perUnit.roundForUi())
        )
    }

    fun dismissGroundingDialog() = update { it.copy(isGroundingDialogOpen = false) }

    fun onCategoryCheckedChange(categoryId: Long, checked: Boolean) {
        update { s ->
            val nextIds = s.selectedCategoryIds.toMutableSet().apply {
                if (checked) add(categoryId) else remove(categoryId)
            }
            s.copy(
                selectedCategoryIds = nextIds,
                hasUnsavedChanges = true,
                isFoodMetadataDirty = true
            )
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
                        isFoodMetadataDirty = true,
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                update { it.copy(errorMessage = t.message ?: "Failed to create category.") }
            }
        }
    }

    fun onFavoriteChange(v: Boolean) = update {
        it.copy(
            favorite = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true
        )
    }

    fun onEatMoreChange(v: Boolean) = update {
        it.copy(
            eatMore = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true
        )
    }

    fun onLimitChange(v: Boolean) = update {
        it.copy(
            limit = v,
            hasUnsavedChanges = true,
            isFoodMetadataDirty = true
        )
    }

    fun onNutrientAmountChange(nutrientId: Long, amount: String) {
        update { s ->
            val updatedRows = s.nutrientRows.map {
                if (it.nutrientId == nutrientId) it.copy(amount = amount) else it
            }

            s.copy(
                nutrientRows = updatedRows,
                nutrientDraft = s.nutrientDraft.copy(rows = updatedRows),
                hasUnsavedChanges = true,
                areNutrientsDirty = true,
                nutritionEditorStatus = s.nutritionEditorStatus.copy(
                    isNutrientsDirty = true,
                    hasPendingApply = true
                )
            )
        }
    }

    fun removeNutrientRow(nutrientId: Long) {
        update { s ->
            val updatedRows = s.nutrientRows.filterNot { it.nutrientId == nutrientId }
            s.copy(
                nutrientRows = updatedRows,
                nutrientDraft = s.nutrientDraft.copy(rows = updatedRows),
                hasUnsavedChanges = true,
                areNutrientsDirty = true,
                nutritionEditorStatus = s.nutritionEditorStatus.copy(
                    isNutrientsDirty = true,
                    hasPendingApply = true
                )
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
            else {
                val updatedRows = sortNutrientRows(
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

                s.copy(
                    hasUnsavedChanges = true,
                    areNutrientsDirty = true,
                    nutrientRows = updatedRows,
                    nutrientDraft = s.nutrientDraft.copy(rows = updatedRows),
                    nutritionEditorStatus = s.nutritionEditorStatus.copy(
                        isNutrientsDirty = true,
                        hasPendingApply = true
                    )
                )
            }
        }
    }

    // =========================
    // RECOMPUTE (SAFE + GUARDED)
    // =========================

    fun onRecomputeDisplayedNutrientsClicked() {
        val s = _state.value

        if (s.nutritionEditorStatus.isNutrientsDirty) {
            update {
                it.copy(
                    nutritionEditorStatus = it.nutritionEditorStatus.copy(
                        showDiscardNutrientEditsDialog = true
                    )
                )
            }
            return
        }

        performRecompute(s)
    }

    fun confirmDiscardNutrientEditsAndRecompute() {
        update {
            it.copy(
                nutritionEditorStatus = it.nutritionEditorStatus.copy(
                    showDiscardNutrientEditsDialog = false,
                    isNutrientsDirty = false,
                    hasPendingApply = false
                )
            )
        }

        performRecompute(_state.value)
    }

    fun dismissDiscardNutrientEditsDialog() {
        update {
            it.copy(
                nutritionEditorStatus = it.nutritionEditorStatus.copy(
                    showDiscardNutrientEditsDialog = false
                )
            )
        }
    }

    private fun performRecompute(s: FoodEditorState) {
        val basisType = s.basisType ?: run {
            update { it.copy(errorMessage = "Missing basis type for nutrient recompute.") }
            return
        }

        val resolution = s.servingResolution ?: computeServingResolution(s) ?: run {
            update {
                it.copy(
                    errorMessage = "Serving definition is incomplete or invalid.",
                    nutritionEditorStatus = it.nutritionEditorStatus.copy(
                        hasPendingRecompute = true
                    )
                )
            }
            return
        }

        val canonicalNutrients = loadedCanonicalNutrientsSnapshot
        if (canonicalNutrients.isEmpty()) {
            update {
                it.copy(
                    errorMessage = "No canonical nutrients are available to recompute from.",
                    nutritionEditorStatus = it.nutritionEditorStatus.copy(
                        hasPendingRecompute = true
                    )
                )
            }
            return
        }

        when (
            val result = recomputeDisplayedNutrients.execute(
                canonicalNutrients = canonicalNutrients,
                basisType = basisType,
                resolution = resolution
            )
        ) {
            is RecomputeDisplayedNutrientsUseCase.Result.Success -> {
                val recomputedRows = s.nutrientRows.map { row ->
                    val recomputed = result.nutrients[NutrientKey(row.code)]
                    row.copy(amount = recomputed?.toString().orEmpty())
                }

                update {
                    it.copy(
                        nutrientRows = recomputedRows,
                        nutrientDraft = it.nutrientDraft.copy(rows = recomputedRows),
                        errorMessage = null,
                        nutritionEditorStatus = it.nutritionEditorStatus.copy(
                            isServingDirty = false,
                            isNutrientsDirty = false,
                            hasPendingRecompute = false,
                            hasPendingApply = false,
                            showDiscardNutrientEditsDialog = false,
                            servingResolution = resolution
                        )
                    )
                }
            }

            is RecomputeDisplayedNutrientsUseCase.Result.Blocked -> {
                val message = when (result.reason) {
                    RecomputeDisplayedNutrientsUseCase.BlockReason.NO_GRAM_PATH ->
                        "Cannot recompute from PER 100g without a gram grounding path."
                    RecomputeDisplayedNutrientsUseCase.BlockReason.NO_ML_PATH ->
                        "Cannot recompute from PER 100mL without an mL grounding path."
                    RecomputeDisplayedNutrientsUseCase.BlockReason.NO_NUTRIENTS ->
                        "No nutrients are available to recompute."
                    RecomputeDisplayedNutrientsUseCase.BlockReason.UNSUPPORTED_BASIS ->
                        "Unsupported nutrient basis for recompute."
                }

                update {
                    it.copy(
                        errorMessage = message,
                        nutritionEditorStatus = it.nutritionEditorStatus.copy(
                            hasPendingRecompute = true,
                            showDiscardNutrientEditsDialog = false,
                            servingResolution = resolution
                        )
                    )
                }
            }
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

        fun parseDoubleOrNull(x: String): Double? =
            x.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        fun normalizePositive(value: Double?): Double? = value?.takeIf { it > 0.0 }

        val servingSize = parseDoubleOrNull(s.servingSize) ?: 1.0
        val gramsInput = normalizePositive(parseDoubleOrNull(s.gramsPerServingUnit))
        val mlInput = normalizePositive(parseDoubleOrNull(s.mlPerServingUnit))
        val servingsPerPackage = parseDoubleOrNull(s.servingsPerPackage)

        viewModelScope.launch {
            try {
                val existing: Food? = s.foodId?.let { foodRepo.getById(it) }

                // ✅ CRITICAL FIX: DO NOT RE-INTERPRET BASIS
                val resolvedBasisType =
                    loadedBasisTypeSnapshot
                        ?: s.basisType
                        ?: BasisType.USDA_REPORTED_SERVING

                // ✅ CRITICAL FIX: DO NOT WIPE OR FORCE BRIDGES
                val gramsPerServingUnitFinal = gramsInput
                val mlPerServingUnitFinal = mlInput

                val rows = if (s.areNutrientsDirty) {
                    val resolution = s.servingResolution ?: computeServingResolution(s)

                    if (resolution == null) {
                        update {
                            it.copy(
                                errorMessage = "Serving definition is incomplete or invalid for nutrient save."
                            )
                        }
                        return@launch
                    }

                    val displayedNutrients = s.nutrientRows.mapNotNull { ui ->
                        val amt = ui.amount.trim()
                        val value =
                            if (amt.isEmpty()) 0.0 else (amt.toDoubleOrNull() ?: return@mapNotNull null)
                        NutrientKey(ui.code) to value
                    }.toMap()

                    when (
                        val result = applyEditedNutrients.execute(
                            displayedNutrients = displayedNutrients,
                            basisType = resolvedBasisType,
                            resolution = resolution
                        )
                    ) {
                        is ApplyEditedNutrientsUseCase.Result.Success -> {
                            s.nutrientRows.map { ui ->
                                val canonical =
                                    result.canonicalNutrients[NutrientKey(ui.code)] ?: 0.0

                                FoodNutrientRow(
                                    nutrient = Nutrient(
                                        id = ui.nutrientId,
                                        code = ui.code,
                                        displayName = ui.name,
                                        unit = ui.unit,
                                        category = ui.category,
                                        aliases = ui.aliases
                                    ),
                                    amount = canonical,
                                    basisType = resolvedBasisType,
                                    basisGrams = when (resolvedBasisType) {
                                        BasisType.PER_100G -> 100.0
                                        BasisType.PER_100ML -> 100.0
                                        BasisType.USDA_REPORTED_SERVING -> null
                                    }
                                )
                            }
                        }

                        is ApplyEditedNutrientsUseCase.Result.Blocked -> {
                            val message = when (result.reason) {
                                ApplyEditedNutrientsUseCase.BlockReason.NO_GRAM_PATH ->
                                    "Cannot save PER 100g nutrients without a gram grounding path."
                                ApplyEditedNutrientsUseCase.BlockReason.NO_ML_PATH ->
                                    "Cannot save PER 100mL nutrients without an mL grounding path."
                                ApplyEditedNutrientsUseCase.BlockReason.NO_NUTRIENTS ->
                                    "No nutrients are available to save."
                                ApplyEditedNutrientsUseCase.BlockReason.INVALID_SERVING_AMOUNT ->
                                    "Serving amount is invalid for nutrient save."
                                ApplyEditedNutrientsUseCase.BlockReason.UNSUPPORTED_BASIS ->
                                    "Unsupported nutrient basis for save."
                            }

                            update { it.copy(errorMessage = message) }
                            return@launch
                        }
                    }
                } else {
                    emptyList()
                }

                val stableId = s.stableId?.takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString()

                update {
                    it.copy(
                        isSaving = true,
                        errorMessage = null,
                        stableId = stableId,
                        basisType = resolvedBasisType,
                        groundingMode = when (resolvedBasisType) {
                            BasisType.PER_100ML -> GroundingMode.LIQUID
                            BasisType.PER_100G, BasisType.USDA_REPORTED_SERVING -> GroundingMode.SOLID
                        }
                    )
                }

                val food =
                    if (existing != null) {
                        existing.copy(
                            name = name,
                            brand = s.brand.trim().ifEmpty { null },
                            servingSize = servingSize,
                            servingUnit = s.servingUnit,
                            gramsPerServingUnit = gramsPerServingUnitFinal,
                            servingsPerPackage = servingsPerPackage,
                            mlPerServingUnit = mlPerServingUnitFinal,
                            isRecipe = false
                        )
                    } else {
                        Food(
                            id = 0L,
                            name = name,
                            brand = s.brand.trim().ifEmpty { null },
                            servingSize = servingSize,
                            servingUnit = s.servingUnit,
                            gramsPerServingUnit = gramsPerServingUnitFinal,
                            servingsPerPackage = servingsPerPackage,
                            isRecipe = false,
                            stableId = stableId,
                            mlPerServingUnit = mlPerServingUnitFinal,
                            isLowSodium = null
                        )
                    }

                val savedId =
                    if (s.areNutrientsDirty) {
                        saveFoodWithNutrients(food, rows)
                    } else {
                        saveFoodMetadata(food)
                    }

                flagsRepo.setFlags(
                    foodId = savedId,
                    favorite = s.favorite,
                    eatMore = s.eatMore,
                    limit = s.limit
                )

                foodCategoryRepo.replaceForFood(
                    foodId = savedId,
                    categoryIds = s.selectedCategoryIds
                )

                update {
                    it.copy(
                        hasUnsavedChanges = false,
                        isFoodMetadataDirty = false,
                        areNutrientsDirty = false,
                        isBasisInterpretationDirty = false,
                        nutritionEditorStatus = it.nutritionEditorStatus.copy(
                            isServingDirty = false,
                            isNutrientsDirty = false,
                            hasPendingRecompute = false,
                            hasPendingApply = false
                        )
                    )
                }

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
                    update {
                        it.copy(
                            hasUnsavedChanges = true,
                            isFoodMetadataDirty = true
                        )
                    }
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
            update {
                it.copy(
                    hasUnsavedChanges = true,
                    isFoodMetadataDirty = true
                )
            }
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

    fun openBarcodePackageEditor(barcode: String) {
        val code = barcode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            val entity = foodBarcodeRepo.getByBarcode(code) ?: run {
                update { it.copy(errorMessage = "Barcode not found.") }
                return@launch
            }

            val currentFoodId = _state.value.foodId
            if (currentFoodId == null || entity.foodId != currentFoodId) {
                update {
                    it.copy(
                        barcodePackageEditor = null,
                        errorMessage = "Barcode is assigned to another food."
                    )
                }
                openFoodEditorRequestFlow.value = entity.foodId
                return@launch
            }

            update {
                it.copy(
                    barcodePackageEditor = toBarcodePackageEditorState(entity),
                    errorMessage = null,
                    barcodeActionMessage = null
                )
            }
        }
    }

    fun dismissBarcodePackageEditor() {
        update { it.copy(barcodePackageEditor = null) }
    }

    fun onBarcodePackageOverrideServingsPerPackageChange(v: String) {
        update { s ->
            val editor = s.barcodePackageEditor ?: return@update s
            s.copy(barcodePackageEditor = editor.copy(overrideServingsPerPackage = v))
        }
    }

    fun onBarcodePackageOverrideHouseholdServingTextChange(v: String) {
        update { s ->
            val editor = s.barcodePackageEditor ?: return@update s
            s.copy(barcodePackageEditor = editor.copy(overrideHouseholdServingText = v))
        }
    }

    fun onBarcodePackageOverrideServingSizeChange(v: String) {
        update { s ->
            val editor = s.barcodePackageEditor ?: return@update s
            s.copy(barcodePackageEditor = editor.copy(overrideServingSize = v))
        }
    }

    fun onBarcodePackageOverrideServingUnitChange(v: ServingUnit?) {
        update { s ->
            val editor = s.barcodePackageEditor ?: return@update s
            s.copy(barcodePackageEditor = editor.copy(overrideServingUnit = v))
        }
    }

    fun saveBarcodePackageOverrides() {
        val editor = _state.value.barcodePackageEditor ?: return
        val currentFoodId = _state.value.foodId ?: return

        fun parseNullablePositiveDouble(text: String): Double? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            return trimmed.toDoubleOrNull()?.takeIf { it > 0.0 }
        }

        val overrideServingsPerPackage = parseNullablePositiveDouble(editor.overrideServingsPerPackage)
        val overrideServingSize = parseNullablePositiveDouble(editor.overrideServingSize)
        val overrideHouseholdServingText = editor.overrideHouseholdServingText.trim().ifBlank { null }

        if (editor.overrideServingsPerPackage.trim().isNotBlank() && overrideServingsPerPackage == null) {
            update { it.copy(errorMessage = "Override servings per package must be a positive number.") }
            return
        }

        if (editor.overrideServingSize.trim().isNotBlank() && overrideServingSize == null) {
            update { it.copy(errorMessage = "Override serving size must be a positive number.") }
            return
        }

        viewModelScope.launch {
            val existing = foodBarcodeRepo.getByBarcode(editor.barcode) ?: run {
                update { it.copy(errorMessage = "Barcode not found.", barcodePackageEditor = null) }
                return@launch
            }

            if (existing.foodId != currentFoodId) {
                update {
                    it.copy(
                        barcodePackageEditor = null,
                        errorMessage = "Barcode is assigned to another food."
                    )
                }
                openFoodEditorRequestFlow.value = existing.foodId
                return@launch
            }

            foodBarcodeRepo.upsert(
                existing.copy(
                    overrideServingsPerPackage = overrideServingsPerPackage,
                    overrideHouseholdServingText = overrideHouseholdServingText,
                    overrideServingSize = overrideServingSize,
                    overrideServingUnit = editor.overrideServingUnit
                )
            )

            val refreshed = buildAssignedBarcodeUiList(currentFoodId)

            update {
                it.copy(
                    assignedBarcodes = refreshed,
                    barcodePackageEditor = null,
                    errorMessage = null,
                    barcodeActionMessage = "Package overrides saved for ${editor.barcode}."
                )
            }
        }
    }

    private fun update(block: (FoodEditorState) -> FoodEditorState) {
        val prev = _state.value
        val nextRaw = block(prev)

        val resolution = computeServingResolution(nextRaw)

        val nextWithResolution = nextRaw.copy(
            nutritionEditorStatus = nextRaw.nutritionEditorStatus.copy(
                servingResolution = resolution
            )
        )

        _state.value = applyNeedsFix(nextWithResolution, prev)
    }

    private fun Double.roundForUi(): String {
        return "%,.2f".format(this).replace(",", "")
    }

    private fun isVolumeGrounded(servingUnit: ServingUnit, mlPerServingUnit: Double?): Boolean {
        return mlPerServingUnitEffective(servingUnit, mlPerServingUnit) != null
    }

    private fun gramsPerServingUnitEffective(
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?
    ): Double? {
        return when {
            servingUnit.isMassUnit() -> servingUnit.toGrams(1.0)
            else -> gramsPerServingUnit
        }?.takeIf { it > 0.0 }
    }

    private fun mlPerServingUnitEffective(
        servingUnit: ServingUnit,
        mlPerServingUnit: Double?
    ): Double? {
        return when (servingUnit) {
            ServingUnit.ML -> 1.0
            ServingUnit.L -> 1000.0
            else -> mlPerServingUnit
        }?.takeIf { it > 0.0 }
    }

    fun openBarcodeScanner() = update {
        it.copy(
            isBarcodeScannerOpen = true,
            errorMessage = null,
            barcodeActionMessage = null,
            pendingUsdaSearchJson = null,
            barcodePickItems = emptyList(),
            pendingUsdaBackfillPrompt = null,
            pendingUsdaInterpretationPrompt = null,
            usdaBackfillMessage = null
        )
    }

    fun closeBarcodeScanner() = update {
        it.copy(
            isBarcodeScannerOpen = false,
            pendingUsdaSearchJson = null,
            barcodePickItems = emptyList(),
            pendingUsdaInterpretationPrompt = null
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
                        isBarcodeScannerOpen = false,
                        pendingUsdaBackfillPrompt = null,
                        pendingUsdaInterpretationPrompt = null
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
                            barcodeCollisionDialog = null,
                            pendingUsdaBackfillPrompt = null,
                            pendingUsdaInterpretationPrompt = null
                        )
                    }
                }

                is SearchUsdaFoodsByBarcodeUseCase.Result.Blocked -> {
                    Log.d("Meow", "FoodEditor> USDA search BLOCKED barcode='$cleaned' reason='${r.reason}'")
                    val msg = if (existing != null && existing.source == BarcodeMappingSource.USER_ASSIGNED) {
                        "Barcode already assigned to a manual food (foodId=${existing.foodId}). You can open it to manage/unassign, or assign this barcode elsewhere."
                    } else {
                        r.reason
                    }

                    update {
                        it.copy(
                            scannedBarcode = cleaned,
                            isBarcodeFallbackOpen = true,
                            barcodeFallbackMessage = msg,
                            barcodeFallbackCreateName = "",
                            barcodeAlreadyAssignedFoodId = existing?.foodId,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            errorMessage = null,
                            pendingUsdaInterpretationPrompt = null
                        )
                    }
                }

                is SearchUsdaFoodsByBarcodeUseCase.Result.Failed -> {
                    Log.d("Meow", "FoodEditor> USDA search FAILED barcode='$cleaned' message='${r.message}'")
                    val msg = if (existing != null && existing.source == BarcodeMappingSource.USER_ASSIGNED) {
                        "Barcode already assigned to a manual food (foodId=${existing.foodId}). USDA lookup failed: ${r.message}"
                    } else {
                        r.message
                    }

                    update {
                        it.copy(
                            scannedBarcode = cleaned,
                            isBarcodeFallbackOpen = true,
                            barcodeFallbackMessage = msg,
                            barcodeFallbackCreateName = "",
                            barcodeAlreadyAssignedFoodId = existing?.foodId,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            errorMessage = null,
                            pendingUsdaInterpretationPrompt = null
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
                    val currentFoodId = _state.value.foodId

                    if (currentFoodId != null) {
                        val parsedServingUnit = picked.servingSizeUnit
                            ?.let { rawUnit: String -> ServingUnit.fromUsda(rawUnit) }

                        when (
                            val adoptResult = adoptUsdaBarcodePackageIntoFood(
                                rawBarcode = barcode,
                                targetFoodId = currentFoodId,
                                usda = AdoptUsdaBarcodePackageIntoFoodUseCase.UsdaPackageSeed(
                                    fdcId = picked.fdcId,
                                    publishedDateIso = picked.publishedDateIso,
                                    modifiedDateIso = picked.modifiedDateIso,
                                    householdServingText = picked.servingText.ifBlank { null },
                                    overrideServingSize = picked.servingSize?.takeIf { it > 0.0 },
                                    overrideServingUnit = parsedServingUnit,
                                    overrideServingsPerPackage = null
                                )
                            )
                        ) {
                            is AdoptUsdaBarcodePackageIntoFoodUseCase.Result.AdoptedNew -> {
                                val entity = foodBarcodeRepo.getByBarcode(adoptResult.barcode) ?: run {
                                    update { it.copy(errorMessage = "USDA barcode adopted but could not be reloaded.") }
                                    return@launch
                                }

                                val refreshed = buildAssignedBarcodeUiList(currentFoodId)

                                update {
                                    it.copy(
                                        assignedBarcodes = refreshed,
                                        scannedBarcode = adoptResult.barcode,
                                        barcodePackageEditor = toBarcodePackageEditorState(entity),
                                        barcodeActionMessage = "USDA package adopted into this food. Review package details below.",
                                        errorMessage = null,
                                        isBarcodeScannerOpen = false,
                                        pendingUsdaSearchJson = json,
                                        barcodePickItems = emptyList(),
                                        barcodeCollisionDialog = null,
                                        pendingUsdaBackfillPrompt = PendingUsdaBackfillPromptState(
                                            barcode = adoptResult.barcode,
                                            selectedFdcId = picked.fdcId,
                                            candidateLabel = candidateLabel(picked)
                                        ),
                                        usdaBackfillMessage = null,
                                        pendingUsdaInterpretationPrompt = null
                                    )
                                }
                            }

                            is AdoptUsdaBarcodePackageIntoFoodUseCase.Result.AlreadyOnSameFood -> {
                                val entity = foodBarcodeRepo.getByBarcode(adoptResult.barcode) ?: run {
                                    update { it.copy(errorMessage = "USDA barcode exists but could not be reloaded.") }
                                    return@launch
                                }

                                val refreshed = buildAssignedBarcodeUiList(currentFoodId)

                                update {
                                    it.copy(
                                        assignedBarcodes = refreshed,
                                        scannedBarcode = adoptResult.barcode,
                                        barcodePackageEditor = toBarcodePackageEditorState(entity),
                                        barcodeActionMessage = "USDA package was refreshed on this food. Review package details below.",
                                        errorMessage = null,
                                        isBarcodeScannerOpen = false,
                                        pendingUsdaSearchJson = json,
                                        barcodePickItems = emptyList(),
                                        barcodeCollisionDialog = null,
                                        pendingUsdaBackfillPrompt = PendingUsdaBackfillPromptState(
                                            barcode = adoptResult.barcode,
                                            selectedFdcId = picked.fdcId,
                                            candidateLabel = candidateLabel(picked)
                                        ),
                                        usdaBackfillMessage = null,
                                        pendingUsdaInterpretationPrompt = null
                                    )
                                }
                            }

                            is AdoptUsdaBarcodePackageIntoFoodUseCase.Result.AssignedToOtherFood -> {
                                update {
                                    it.copy(
                                        pendingUsdaSearchJson = null,
                                        barcodePickItems = emptyList(),
                                        barcodePackageEditor = null,
                                        scannedBarcode = adoptResult.barcode,
                                        barcodeActionMessage = "Barcode ${adoptResult.barcode} is already assigned to another food. Opening that food so you can decide what to do next.",
                                        errorMessage = null,
                                        isBarcodeScannerOpen = false,
                                        barcodeCollisionDialog = null,
                                        pendingUsdaBackfillPrompt = null,
                                        pendingUsdaInterpretationPrompt = null
                                    )
                                }
                                openFoodEditorRequestFlow.value = adoptResult.existingFoodId
                            }

                            is AdoptUsdaBarcodePackageIntoFoodUseCase.Result.Blocked -> {
                                update {
                                    it.copy(
                                        errorMessage = adoptResult.reason,
                                        isBarcodeScannerOpen = false
                                    )
                                }
                            }
                        }
                    } else {
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
                                        errorMessage = null,
                                        pendingUsdaBackfillPrompt = null,
                                        pendingUsdaInterpretationPrompt = null
                                    )
                                }

                                load(foodId = r.foodId, initialName = null, force = true)
                            }

                            is ImportUsdaFoodFromSearchJsonUseCase.Result.NeedsInterpretationChoice -> {
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
                                        barcodePickItems = emptyList(),
                                        barcodeCollisionDialog = null,
                                        errorMessage = null,
                                        pendingUsdaBackfillPrompt = null,
                                        pendingUsdaInterpretationPrompt = PendingUsdaInterpretationPromptState(
                                            foodId = r.foodId,
                                            selectedFdcId = r.fdcId,
                                            candidateLabel = r.candidateLabel,
                                            servingText = r.servingText,
                                            calories = r.calories,
                                            carbs = r.carbs,
                                            protein = r.protein,
                                            fat = r.fat
                                        )
                                    )
                                }
                            }

                            is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked -> {
                                update { it.copy(errorMessage = r.reason) }
                            }
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
                            errorMessage = null,
                            pendingUsdaBackfillPrompt = null,
                            pendingUsdaInterpretationPrompt = null
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
                            errorMessage = null,
                            pendingUsdaBackfillPrompt = null,
                            pendingUsdaInterpretationPrompt = null
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
        val currentFoodId = _state.value.foodId ?: return
        val code = barcode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            when (val result = assignBarcodeToFood(rawBarcode = code, foodId = currentFoodId)) {
                is AssignBarcodeToFoodUseCase.Result.AssignedNew -> {
                    val entity = foodBarcodeRepo.getByBarcode(result.barcode) ?: run {
                        update { it.copy(errorMessage = "Barcode was assigned but could not be reloaded.") }
                        return@launch
                    }

                    val refreshed = buildAssignedBarcodeUiList(currentFoodId)

                    update {
                        it.copy(
                            assignedBarcodes = refreshed,
                            scannedBarcode = result.barcode,
                            barcodePackageEditor = toBarcodePackageEditorState(entity),
                            barcodeActionMessage = "Barcode added to this food. Review package details if needed.",
                            errorMessage = null,
                            isBarcodeScannerOpen = false,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList()
                        )
                    }
                }

                is AssignBarcodeToFoodUseCase.Result.AlreadyAssignedToSameFood -> {
                    val entity = foodBarcodeRepo.getByBarcode(result.barcode) ?: run {
                        update { it.copy(errorMessage = "Barcode exists but could not be reloaded.") }
                        return@launch
                    }

                    val refreshed = buildAssignedBarcodeUiList(currentFoodId)

                    update {
                        it.copy(
                            assignedBarcodes = refreshed,
                            scannedBarcode = result.barcode,
                            barcodePackageEditor = toBarcodePackageEditorState(entity),
                            barcodeActionMessage = "Barcode already assigned to this food. Review package details below.",
                            errorMessage = null,
                            isBarcodeScannerOpen = false,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList()
                        )
                    }
                }

                is AssignBarcodeToFoodUseCase.Result.AssignedToOtherFood -> {
                    update {
                        it.copy(
                            barcodePackageEditor = null,
                            pendingUsdaSearchJson = null,
                            barcodePickItems = emptyList(),
                            isBarcodeScannerOpen = false,
                            scannedBarcode = result.barcode,
                            barcodeActionMessage = "Barcode ${result.barcode} is already assigned to another food. Opening that food so you can decide what to do next.",
                            errorMessage = null
                        )
                    }
                    openFoodEditorRequestFlow.value = result.existingFoodId
                }

                is AssignBarcodeToFoodUseCase.Result.Blocked -> {
                    update {
                        it.copy(
                            isBarcodeScannerOpen = false,
                            errorMessage = result.reason
                        )
                    }
                }
            }
        }
    }

    private suspend fun buildAssignedBarcodeUiList(foodId: Long): List<AssignedBarcodeUi> {
        return foodBarcodeRepo.getAllBarcodesForFood(foodId)
            .sortedBy { it.barcode }
            .map { entity: FoodBarcodeEntity -> toAssignedBarcodeUi(entity) }
    }

    private fun toAssignedBarcodeUi(entity: FoodBarcodeEntity): AssignedBarcodeUi {
        return AssignedBarcodeUi(
            barcode = entity.barcode,
            source = entity.source,
            overrideServingsPerPackage = entity.overrideServingsPerPackage,
            overrideHouseholdServingText = entity.overrideHouseholdServingText,
            overrideServingSize = entity.overrideServingSize,
            overrideServingUnit = entity.overrideServingUnit
        )
    }

    private fun toBarcodePackageEditorState(entity: FoodBarcodeEntity): BarcodePackageEditorState {
        return BarcodePackageEditorState(
            barcode = entity.barcode,
            overrideServingsPerPackage = entity.overrideServingsPerPackage?.toString().orEmpty(),
            overrideHouseholdServingText = entity.overrideHouseholdServingText.orEmpty(),
            overrideServingSize = entity.overrideServingSize?.toString().orEmpty(),
            overrideServingUnit = entity.overrideServingUnit
        )
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

                    when (val result = assignBarcodeToFood(rawBarcode = barcode, foodId = pickedFoodId)) {
                        is AssignBarcodeToFoodUseCase.Result.AssignedNew,
                        is AssignBarcodeToFoodUseCase.Result.AlreadyAssignedToSameFood -> {
                            val entity = foodBarcodeRepo.getByBarcode(barcode) ?: run {
                                update { it.copy(errorMessage = "Barcode could not be reloaded after assignment.") }
                                return@collect
                            }

                            val refreshed = buildAssignedBarcodeUiList(pickedFoodId)

                            update {
                                it.copy(
                                    scannedBarcode = barcode,
                                    pendingUsdaSearchJson = null,
                                    barcodePickItems = emptyList(),
                                    errorMessage = null,
                                    isBarcodeScannerOpen = false,
                                    isBarcodeFallbackOpen = false,
                                    assignedBarcodes = refreshed,
                                    barcodePackageEditor = toBarcodePackageEditorState(entity)
                                )
                            }

                            load(foodId = pickedFoodId, initialName = null, force = true)
                            openBarcodePackageEditor(barcode)
                        }

                        is AssignBarcodeToFoodUseCase.Result.AssignedToOtherFood -> {
                            update {
                                it.copy(
                                    errorMessage = "Barcode ${result.barcode} is already assigned to another food."
                                )
                            }
                            openFoodEditorRequestFlow.value = result.existingFoodId
                        }

                        is AssignBarcodeToFoodUseCase.Result.Blocked -> {
                            update { it.copy(errorMessage = result.reason) }
                        }
                    }
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
 * - Load-time display conversion must treat deterministic mass/volume serving units as already grounded:
 *   - 50 g serving => use 50 g for display scaling
 *   - 250 mL serving => use 250 mL for display scaling
 *   - only ambiguous/container-ish units should rely on gramsPerServingUnit / mlPerServingUnit bridges.
 * - Ambiguous volume units (cup/tbsp/fl oz/can/bottle) must prompt SOLID vs LIQUID
 *   instead of assuming PER_100ML.
 * - Never guess density. Never grams↔mL conversions.
 *
 * 2026-03-19 repair rule:
 * - Ambiguous-unit bridge fields must preserve the last loaded persisted truth on no-op saves.
 * - Non-bridge edits (favorite/category/etc.) must not silently introduce gramsPerServingUnit/mlPerServingUnit.
 *
 * 2026/03/24
 * ## Editor integration rule (CRITICAL)
 *
 * - This use case MUST NOT be called for metadata-only edits.
 * - Doing so will rewrite canonical nutrients using a new serving lens and corrupt data.
 *
 * - FoodEditorViewModel must:
 *   - call saveFoodMetadata(...) when only metadata changes
 *   - call SaveFoodWithNutrientsUseCase(...) when nutrient rows are dirty
 *
 * - This separation is REQUIRED to maintain nutrition correctness across:
 *   - Foods list
 *   - Recipe system
 *   - Logging
 *   - Snapshot generation
 */