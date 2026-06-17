package com.example.adobongkangkong.ui.recipevariant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeType
import com.example.adobongkangkong.domain.model.AssembledRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RemovedRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.model.RecipeVariantMacroComparison
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.NutrientCaution
import com.example.adobongkangkong.domain.nutrition.NutrientCautionCalculator
import com.example.adobongkangkong.domain.nutrition.NutrientCautionThresholds
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.usecase.recipevariant.AssembleRecipeVariantUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.CompareRecipeVariantMacrosUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.ComputeRecipeVariantNutritionUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.SaveRecipeVariantChangesUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.UpdateRecipeVariantUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

data class RecipeVariantEditorUiState(
    val recipeFoodId: Long = 0L,
    val variantId: Long = 0L,

    val recipeName: String = "",
    val name: String = "",
    val notes: String = "",

    val originalName: String = "",
    val originalNotes: String = "",

    val baseServingsYield: Double? = null,
    val variantServingsYieldOverride: Double? = null,
    val originalVariantServingsYieldOverride: Double? = null,
    val variantServingsYieldText: String = "",

    val finalIngredientLines: List<AssembledRecipeVariantIngredientLine> = emptyList(),
    val removedIngredientLines: List<RemovedRecipeVariantIngredientLine> = emptyList(),
    val warnings: List<String> = emptyList(),
    val macroComparison: RecipeVariantMacroComparison? = null,
    val variantPerServingCautions: List<NutrientCaution> = emptyList(),

    val addIngredientQuery: String = "",
    val addIngredientResults: List<Food> = emptyList(),
    val pickedFood: Food? = null,
    val pickedServings: Double = 1.0,
    val pickedGrams: Double? = null,
    val pickedInputUnit: ServingUnit = ServingUnit.G,
    val pickedInputAmount: Double? = null,

    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val draftChanges: List<RecipeVariantIngredientChangeEntity> = emptyList(),
    val originalDraftChanges: List<RecipeVariantIngredientChangeEntity> = emptyList(),
) {
    val hasUnsavedChanges: Boolean
        get() = name != originalName ||
                notes != originalNotes ||
                normalizedServingsOverride() != normalizedOriginalServingsOverride() ||
                draftChanges != originalDraftChanges

    fun normalizedServingsOverride(): Double? {
        val base = baseServingsYield
        val parsed = variantServingsYieldText
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }

        if (parsed == null) {
            return null
        }

        if (base != null && kotlin.math.abs(parsed - base) < 0.0001) {
            return null
        }

        return parsed
    }

    private fun normalizedOriginalServingsOverride(): Double? {
        val base = baseServingsYield
        val original = originalVariantServingsYieldOverride?.takeIf { it > 0.0 }

        if (original == null) {
            return null
        }

        if (base != null && kotlin.math.abs(original - base) < 0.0001) {
            return null
        }

        return original
    }

    val removedBaseIngredientIds: Set<Long>
        get() = draftChanges
            .filter { it.changeType == RecipeVariantIngredientChangeType.REMOVE }
            .mapNotNull { it.baseRecipeIngredientId }
            .toSet()

    val adjustedBaseIngredientIds: Set<Long>
        get() = draftChanges
            .filter { it.changeType == RecipeVariantIngredientChangeType.ADJUST }
            .mapNotNull { it.baseRecipeIngredientId }
            .toSet()
}

@HiltViewModel
class RecipeVariantEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeVariantRepository: RecipeVariantRepository,
    private val foodRepository: FoodRepository,
    private val updateRecipeVariant: UpdateRecipeVariantUseCase,
    private val assembleRecipeVariant: AssembleRecipeVariantUseCase,
    private val compareRecipeVariantMacros: CompareRecipeVariantMacrosUseCase,
    private val computeRecipeVariantNutrition: ComputeRecipeVariantNutritionUseCase,
    private val nutrientCautionCalculator: NutrientCautionCalculator,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveRecipeVariantChanges: SaveRecipeVariantChangesUseCase,
) : ViewModel() {

    private val recipeFoodId: Long =
        savedStateHandle.get<Long>("recipeFoodId")
            ?: savedStateHandle.get<String>("recipeFoodId")?.toLongOrNull()
            ?: error("recipeFoodId is required")

    private val variantId: Long =
        savedStateHandle.get<Long>("variantId")
            ?: savedStateHandle.get<String>("variantId")?.toLongOrNull()
            ?: error("variantId is required")

    private val _uiState = MutableStateFlow(
        RecipeVariantEditorUiState(
            recipeFoodId = recipeFoodId,
            variantId = variantId,
        )
    )

    val uiState: StateFlow<RecipeVariantEditorUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            runCatching {
                val variant = recipeVariantRepository.getVariantById(variantId)
                    ?: error("Variant not found.")

                val changes = recipeVariantRepository.getChangesForVariant(variantId)
                val assembled = assembleRecipeVariant(
                    variantId = variantId,
                    draftChanges = changes,
                )
                val macroComparison = compareRecipeVariantMacros(
                    variantId = variantId,
                    draftChanges = changes,
                    draftServingsYieldOverride = variant.servingsYieldOverride,
                )
                val variantNutrition = computeRecipeVariantNutrition(
                    variantId = variantId,
                    draftChanges = changes,
                    draftServingsYieldOverride = variant.servingsYieldOverride,
                )
                val variantPerServingCautions =
                    nutrientCautionCalculator.forRecipeServing(
                        perServingNutrients = variantNutrition.perServing,
                        thresholds = quickAddCautionThresholds(),
                    )

                _uiState.update {
                    it.copy(
                        recipeFoodId = recipeFoodId,
                        recipeName = assembled.recipeName,
                        variantId = variantId,
                        name = variant.name,
                        notes = variant.notes.orEmpty(),
                        originalName = variant.name,
                        originalNotes = variant.notes.orEmpty(),
                        baseServingsYield = assembled.baseServingsYield,
                        variantServingsYieldOverride = variant.servingsYieldOverride,
                        originalVariantServingsYieldOverride = variant.servingsYieldOverride,
                        variantServingsYieldText = formatServingsYield(
                            variant.servingsYieldOverride ?: assembled.baseServingsYield,
                        ),
                        finalIngredientLines = assembled.finalIngredientLines,
                        removedIngredientLines = assembled.removedIngredientLines,
                        warnings = macroComparison.warnings.distinct(),
                        macroComparison = macroComparison,
                        variantPerServingCautions = variantPerServingCautions,
                        isLoading = false,
                        errorMessage = null,
                        draftChanges = changes,
                        originalDraftChanges = changes,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Could not load variant.",
                    )
                }
            }
        }
    }

    fun onNameChanged(value: String) {
        _uiState.update {
            it.copy(
                name = value,
                errorMessage = null,
            )
        }
    }

    fun onNotesChanged(value: String) {
        _uiState.update {
            it.copy(
                notes = value,
                errorMessage = null,
            )
        }
    }

    fun onVariantServingsYieldTextChanged(value: String) {
        _uiState.update {
            it.copy(
                variantServingsYieldText = value.filter { character ->
                    character.isDigit() || character == '.'
                },
                errorMessage = null,
            )
        }
    }

    fun applyVariantServingsYieldOverride() {
        val current = _uiState.value
        val raw = current.variantServingsYieldText.trim()

        if (raw.isBlank()) {
            _uiState.update {
                it.copy(
                    variantServingsYieldOverride = null,
                    variantServingsYieldText = formatServingsYield(it.baseServingsYield),
                    errorMessage = null,
                )
            }
            refreshAssembledVariant(
                draftChanges = current.draftChanges,
                draftServingsYieldOverride = null,
            )
            return
        }

        val parsed = raw.toDoubleOrNull()

        if (parsed == null || parsed <= 0.0) {
            _uiState.update {
                it.copy(errorMessage = "Variant servings must be greater than zero.")
            }
            return
        }

        val normalized = if (
            current.baseServingsYield != null &&
            kotlin.math.abs(parsed - current.baseServingsYield) < 0.0001
        ) {
            null
        } else {
            parsed
        }

        _uiState.update {
            it.copy(
                variantServingsYieldOverride = normalized,
                variantServingsYieldText = formatServingsYield(parsed),
                errorMessage = null,
            )
        }

        refreshAssembledVariant(
            draftChanges = current.draftChanges,
            draftServingsYieldOverride = normalized,
        )
    }

    fun onAddIngredientQueryChanged(value: String) {
        _uiState.update {
            it.copy(
                addIngredientQuery = value,
                errorMessage = null,
            )
        }

        val query = value.trim()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(addIngredientResults = emptyList())
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                foodRepository.search(query, limit = 50).first()
            }.onSuccess { results ->
                _uiState.update { current ->
                    if (current.addIngredientQuery.trim() == query) {
                        current.copy(addIngredientResults = results)
                    } else {
                        current
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "Food search failed.")
                }
            }
        }
    }

    fun pickFoodForIngredient(food: Food) {
        val servings = 1.0
        _uiState.update {
            it.copy(
                pickedFood = food,
                pickedServings = servings,
                pickedGrams = food.gramsPerCurrentServingResolved()?.let { gramsPerServing ->
                    servings * gramsPerServing
                },
                pickedInputUnit = ServingUnit.G,
                pickedInputAmount = null,
                errorMessage = null,
            )
        }
    }

    fun clearPickedFoodForIngredient() {
        _uiState.update {
            it.copy(
                pickedFood = null,
                pickedServings = 1.0,
                pickedGrams = null,
                pickedInputUnit = ServingUnit.G,
                pickedInputAmount = null,
                errorMessage = null,
            )
        }
    }

    fun onPickedServingsChanged(servings: Double) {
        val food = _uiState.value.pickedFood ?: return
        val safeServings = max(0.0, servings)

        _uiState.update {
            it.copy(
                pickedServings = safeServings,
                pickedGrams = food.gramsPerCurrentServingResolved()?.let { gramsPerServing ->
                    safeServings * gramsPerServing
                },
                errorMessage = null,
            )
        }
    }

    fun onPickedServingUnitAmountChanged(amountInServingUnit: Double) {
        val food = _uiState.value.pickedFood ?: return
        val servingSize = food.servingSize.takeIf { it > 0.0 } ?: return
        val servings = (amountInServingUnit / servingSize).coerceAtLeast(0.0)
        onPickedServingsChanged(servings)
    }

    fun onPickedGramsChanged(grams: Double) {
        val food = _uiState.value.pickedFood ?: return
        val gramsPerServing = food.gramsPerCurrentServingResolved() ?: return

        if (gramsPerServing <= 0.0) {
            return
        }

        val safeGrams = grams.coerceAtLeast(0.0)
        val servings = safeGrams / gramsPerServing

        _uiState.update {
            it.copy(
                pickedServings = servings,
                pickedGrams = safeGrams,
                errorMessage = null,
            )
        }
    }

    fun onPickedInputUnitChanged(unit: ServingUnit) {
        _uiState.update {
            it.copy(pickedInputUnit = unit)
        }

        val amount = _uiState.value.pickedInputAmount ?: return
        onPickedInputAmountChanged(amount)
    }

    fun onPickedInputAmountChanged(amount: Double?) {
        val food = _uiState.value.pickedFood ?: return
        val unit = _uiState.value.pickedInputUnit

        _uiState.update {
            it.copy(pickedInputAmount = amount)
        }

        if (amount == null || amount <= 0.0) {
            return
        }

        val grams = computePickedInputGrams(
            food = food,
            amount = amount,
            unit = unit,
        ) ?: return

        onPickedGramsChanged(grams)
    }

    fun onPickedPackageClicked(multiplier: Double) {
        val food = _uiState.value.pickedFood ?: return
        val servingsPerPackage = food.servingsPerPackage ?: return

        if (servingsPerPackage <= 0.0) {
            return
        }

        onPickedServingsChanged(servingsPerPackage * multiplier)
    }

    fun addPickedVariantIngredientWithAmount(
        servings: Double,
        grams: Double?,
        preferGrams: Boolean,
    ) {
        val current = _uiState.value
        val food = current.pickedFood ?: return

        if (servings <= 0.0 && (grams == null || grams <= 0.0)) {
            _uiState.update {
                it.copy(errorMessage = "Ingredient amount must be greater than zero.")
            }
            return
        }

        val gramsPerServing = food.gramsPerCurrentServingResolved()
        val millilitersPerServing = food.millilitersPerCurrentServingResolved()

        val hasNutritionPath =
            (gramsPerServing != null && gramsPerServing > 0.0) ||
                    (millilitersPerServing != null && millilitersPerServing > 0.0)

        if (!hasNutritionPath) {
            _uiState.update {
                it.copy(
                    errorMessage = "Add grams-per-serving or mL-per-serving to enable recipe conversion.",
                )
            }
            return
        }

        val now = System.currentTimeMillis()
        val savedAsGrams = preferGrams && grams != null && grams > 0.0

        updateDraftChanges { changes ->
            changes + RecipeVariantIngredientChangeEntity(
                variantId = variantId,
                changeType = RecipeVariantIngredientChangeType.ADD,
                baseRecipeIngredientId = null,
                foodId = food.id,
                servings = if (savedAsGrams) null else servings,
                grams = if (savedAsGrams) grams else null,
                note = null,
                sortOrder = changes.size,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        }

        _uiState.update {
            it.copy(
                addIngredientQuery = "",
                addIngredientResults = emptyList(),
                pickedFood = null,
                pickedServings = 1.0,
                pickedGrams = null,
                pickedInputUnit = ServingUnit.G,
                pickedInputAmount = null,
                errorMessage = null,
            )
        }
    }

    fun addPickedVariantIngredient() {
        val current = _uiState.value
        val food = current.pickedFood ?: return
        val servings = current.pickedServings

        if (servings <= 0.0) {
            _uiState.update {
                it.copy(errorMessage = "Ingredient amount must be greater than zero.")
            }
            return
        }

        val gramsPerServing = food.gramsPerCurrentServingResolved()
        val millilitersPerServing = food.millilitersPerCurrentServingResolved()

        val hasNutritionPath =
            (gramsPerServing != null && gramsPerServing > 0.0) ||
                    (millilitersPerServing != null && millilitersPerServing > 0.0)

        if (!hasNutritionPath) {
            _uiState.update {
                it.copy(
                    errorMessage = "Add grams-per-serving or mL-per-serving to enable recipe conversion.",
                )
            }
            return
        }

        val now = System.currentTimeMillis()

        updateDraftChanges { changes ->
            changes + RecipeVariantIngredientChangeEntity(
                variantId = variantId,
                changeType = RecipeVariantIngredientChangeType.ADD,
                baseRecipeIngredientId = null,
                foodId = food.id,
                servings = servings,
                grams = null,
                note = null,
                sortOrder = changes.size,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        }

        _uiState.update {
            it.copy(
                addIngredientQuery = "",
                addIngredientResults = emptyList(),
                pickedFood = null,
                pickedServings = 1.0,
                pickedGrams = null,
                pickedInputUnit = ServingUnit.G,
                pickedInputAmount = null,
                errorMessage = null,
            )
        }
    }

    fun adjustAddedIngredientToGrams(
        foodId: Long,
        lineSortOrder: Int,
        grams: Double,
    ) {
        if (foodId <= 0L || grams <= 0.0) {
            _uiState.update {
                it.copy(errorMessage = "Grams must be greater than zero.")
            }
            return
        }

        updateAddedIngredientChange(
            foodId = foodId,
            lineSortOrder = lineSortOrder,
        ) { change ->
            change.copy(
                servings = null,
                grams = grams,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    fun adjustAddedIngredientToServings(
        foodId: Long,
        lineSortOrder: Int,
        servings: Double,
    ) {
        if (foodId <= 0L || servings <= 0.0) {
            _uiState.update {
                it.copy(errorMessage = "Servings must be greater than zero.")
            }
            return
        }

        updateAddedIngredientChange(
            foodId = foodId,
            lineSortOrder = lineSortOrder,
        ) { change ->
            change.copy(
                servings = servings,
                grams = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    fun removeAddedIngredient(
        foodId: Long,
        lineSortOrder: Int,
    ) {
        val addedIndex = findAddedIngredientIndex(
            foodId = foodId,
            lineSortOrder = lineSortOrder,
        )

        if (addedIndex < 0) {
            return
        }

        updateDraftChanges { changes ->
            var seenAddedIndex = -1

            changes.filterNot { change ->
                if (change.changeType != RecipeVariantIngredientChangeType.ADD) {
                    false
                } else {
                    seenAddedIndex += 1
                    seenAddedIndex == addedIndex
                }
            }
        }
    }

    private fun updateAddedIngredientChange(
        foodId: Long,
        lineSortOrder: Int,
        transform: (RecipeVariantIngredientChangeEntity) -> RecipeVariantIngredientChangeEntity,
    ) {
        val addedIndex = findAddedIngredientIndex(
            foodId = foodId,
            lineSortOrder = lineSortOrder,
        )

        if (addedIndex < 0) {
            return
        }

        updateDraftChanges { changes ->
            var seenAddedIndex = -1

            changes.map { change ->
                if (change.changeType != RecipeVariantIngredientChangeType.ADD) {
                    change
                } else {
                    seenAddedIndex += 1

                    if (seenAddedIndex == addedIndex) {
                        transform(change)
                    } else {
                        change
                    }
                }
            }
        }
    }

    private fun findAddedIngredientIndex(
        foodId: Long,
        lineSortOrder: Int,
    ): Int {
        return _uiState.value.finalIngredientLines
            .filter { it.source == com.example.adobongkangkong.domain.model.RecipeVariantIngredientLineSource.ADDED }
            .indexOfFirst { line ->
                line.food.id == foodId && line.sortOrder == lineSortOrder
            }
    }

    fun markIngredientRemoved(
        baseRecipeIngredientId: Long,
    ) {
        if (baseRecipeIngredientId <= 0L) return

        val now = System.currentTimeMillis()

        updateDraftChanges { current ->
            current
                .filterNot { it.baseRecipeIngredientId == baseRecipeIngredientId }
                .plus(
                    RecipeVariantIngredientChangeEntity(
                        variantId = variantId,
                        changeType = RecipeVariantIngredientChangeType.REMOVE,
                        baseRecipeIngredientId = baseRecipeIngredientId,
                        foodId = null,
                        servings = null,
                        grams = null,
                        note = null,
                        sortOrder = current.size,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )
                )
        }
    }

    fun restoreIngredientLine(
        baseRecipeIngredientId: Long,
    ) {
        if (baseRecipeIngredientId <= 0L) return

        updateDraftChanges { current ->
            current.filterNot { it.baseRecipeIngredientId == baseRecipeIngredientId }
        }
    }

    fun adjustIngredientToGrams(
        baseRecipeIngredientId: Long,
        grams: Double,
    ) {
        if (baseRecipeIngredientId <= 0L) return

        if (grams <= 0.0) {
            _uiState.update {
                it.copy(errorMessage = "Grams must be greater than zero.")
            }
            return
        }

        val now = System.currentTimeMillis()

        updateDraftChanges { current ->
            current
                .filterNot { it.baseRecipeIngredientId == baseRecipeIngredientId }
                .plus(
                    RecipeVariantIngredientChangeEntity(
                        variantId = variantId,
                        changeType = RecipeVariantIngredientChangeType.ADJUST,
                        baseRecipeIngredientId = baseRecipeIngredientId,
                        foodId = null,
                        servings = null,
                        grams = grams,
                        note = null,
                        sortOrder = current.size,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )
                )
        }
    }

    fun adjustIngredientToServings(
        baseRecipeIngredientId: Long,
        servings: Double,
    ) {
        if (baseRecipeIngredientId <= 0L) return

        if (servings <= 0.0) {
            _uiState.update {
                it.copy(errorMessage = "Servings must be greater than zero.")
            }
            return
        }

        val now = System.currentTimeMillis()

        updateDraftChanges { current ->
            current
                .filterNot { it.baseRecipeIngredientId == baseRecipeIngredientId }
                .plus(
                    RecipeVariantIngredientChangeEntity(
                        variantId = variantId,
                        changeType = RecipeVariantIngredientChangeType.ADJUST,
                        baseRecipeIngredientId = baseRecipeIngredientId,
                        foodId = null,
                        servings = servings,
                        grams = null,
                        note = null,
                        sortOrder = current.size,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )
                )
        }
    }

    fun clearIngredientAdjustment(
        baseRecipeIngredientId: Long,
    ) {
        if (baseRecipeIngredientId <= 0L) return

        updateDraftChanges { current ->
            current.filterNot {
                it.baseRecipeIngredientId == baseRecipeIngredientId &&
                        it.changeType == RecipeVariantIngredientChangeType.ADJUST
            }
        }
    }

    private fun updateDraftChanges(
        transform: (List<RecipeVariantIngredientChangeEntity>) -> List<RecipeVariantIngredientChangeEntity>,
    ) {
        val nextDraftChanges = transform(_uiState.value.draftChanges)
            .mapIndexed { index, change ->
                change.copy(sortOrder = index)
            }

        _uiState.update { current ->
            current.copy(
                draftChanges = nextDraftChanges,
                errorMessage = null,
            )
        }

        refreshAssembledVariant(draftChanges = nextDraftChanges)
    }

    fun save() {
        val current = uiState.value
        val cleanedName = current.name.trim()

        if (cleanedName.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Variant name cannot be blank.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            runCatching {
                updateRecipeVariant(
                    variantId = variantId,
                    name = cleanedName,
                    notes = current.notes,
                    servingsYieldOverride = current.normalizedServingsOverride(),
                )
                saveRecipeVariantChanges(
                    variantId = variantId,
                    changes = current.draftChanges,
                )
            }.onSuccess {
                val savedNotes = current.notes.trim()

                _uiState.update {
                    it.copy(
                        name = cleanedName,
                        notes = savedNotes,
                        originalName = cleanedName,
                        originalNotes = savedNotes,
                        variantServingsYieldOverride = current.normalizedServingsOverride(),
                        originalVariantServingsYieldOverride = current.normalizedServingsOverride(),
                        variantServingsYieldText = formatServingsYield(
                            current.normalizedServingsOverride() ?: current.baseServingsYield,
                        ),
                        originalDraftChanges = current.draftChanges,
                        isSaving = false,
                        errorMessage = null,
                    )
                }

                refreshAssembledVariant(draftChanges = current.draftChanges)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = throwable.message ?: "Could not save variant.",
                    )
                }
            }
        }
    }

    fun refreshAssembledVariant(
        draftChanges: List<RecipeVariantIngredientChangeEntity> = _uiState.value.draftChanges,
        draftServingsYieldOverride: Double? = _uiState.value.normalizedServingsOverride(),
    ) {
        viewModelScope.launch {
            runCatching {
                val assembled = assembleRecipeVariant(
                    variantId = variantId,
                    draftChanges = draftChanges,
                )
                val macroComparison = compareRecipeVariantMacros(
                    variantId = variantId,
                    draftChanges = draftChanges,
                    draftServingsYieldOverride = draftServingsYieldOverride,
                )
                val variantNutrition = computeRecipeVariantNutrition(
                    variantId = variantId,
                    draftChanges = draftChanges,
                    draftServingsYieldOverride = draftServingsYieldOverride,
                )
                val variantPerServingCautions =
                    nutrientCautionCalculator.forRecipeServing(
                        perServingNutrients = variantNutrition.perServing,
                        thresholds = quickAddCautionThresholds(),
                    )

                _uiState.update {
                    it.copy(
                        recipeName = assembled.recipeName,
                        baseServingsYield = assembled.baseServingsYield,
                        finalIngredientLines = assembled.finalIngredientLines,
                        removedIngredientLines = assembled.removedIngredientLines,
                        warnings = macroComparison.warnings.distinct(),
                        macroComparison = macroComparison,
                        variantPerServingCautions = variantPerServingCautions,
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        errorMessage = throwable.message ?: "Could not refresh variant preview.",
                    )
                }
            }
        }
    }

    private fun Food.gramsPerCurrentServingResolved(): Double? {
        val directMass = servingUnit.toGrams(servingSize)
        if (directMass != null && directMass > 0.0) {
            return directMass
        }

        val gramsPerOneUnit = gramsPerServingUnitResolved()
        if (gramsPerOneUnit != null && gramsPerOneUnit > 0.0 && servingSize > 0.0) {
            return servingSize * gramsPerOneUnit
        }

        return null
    }

    private fun Food.millilitersPerCurrentServingResolved(): Double? {
        val directVolume = servingUnit.toMilliliters(servingSize)
        if (directVolume != null && directVolume > 0.0) {
            return directVolume
        }

        val mlPerOneUnit = mlPerServingUnit
        if (mlPerOneUnit != null && mlPerOneUnit > 0.0 && servingSize > 0.0) {
            return servingSize * mlPerOneUnit
        }

        return null
    }

    private fun computePickedInputGrams(
        food: Food,
        amount: Double,
        unit: ServingUnit,
    ): Double? {
        val safeAmount = amount.coerceAtLeast(0.0)

        unit.toGrams(safeAmount)?.let { grams ->
            return grams
        }

        val mlInput = unit.toMilliliters(safeAmount) ?: return null
        val gramsPerServing = food.gramsPerCurrentServingResolved() ?: return null
        val mlPerServing = food.millilitersPerCurrentServingResolved() ?: return null

        if (gramsPerServing <= 0.0 || mlPerServing <= 0.0) {
            return null
        }

        val densityGPerMl = gramsPerServing / mlPerServing
        return mlInput * densityGPerMl
    }

    private fun formatServingsYield(value: Double?): String {
        val safeValue = value ?: return ""

        return if (safeValue % 1.0 == 0.0) {
            safeValue.toInt().toString()
        } else {
            java.lang.String.format(java.util.Locale.US, "%.2f", safeValue)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    private fun quickAddCautionThresholds(): NutrientCautionThresholds =
        NutrientCautionThresholds(
            sodiumMg = userPreferencesRepository.quickAddSodiumCautionMg.value,
            totalSugarG = userPreferencesRepository.quickAddSugarCautionG.value,
        )

}