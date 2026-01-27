package com.example.adobongkangkong.ui.food.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.AliasAddResult
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
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
    private val nutrientAliasRepo: NutrientAliasRepository,
    private val flagsRepo: FoodGoalFlagsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodEditorState())
    val state: StateFlow<FoodEditorState> = _state

    @OptIn(FlowPreview::class)
    private val nutrientQueryFlow = MutableStateFlow("")

    private val aliasSheetMessageFlow = MutableStateFlow<String?>(null)
    val aliasSheetMessage: StateFlow<String?> = aliasSheetMessageFlow

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

            _state.value = current.copy(
                foodId = foodId,
                stableId = stableId,
                name = food?.name ?: (initialName ?: ""),
                brand = food?.brand.orEmpty(),
                servingSize = food?.servingSize?.toString() ?: "1.0",
                servingUnit = food?.servingUnit ?: ServingUnit.G,
                gramsPerServing = food?.gramsPerServing?.toString().orEmpty(),
                servingsPerPackage = food?.servingsPerPackage?.toString().orEmpty(),
                nutrientRows = rows.map { r ->
                    NutrientRowUi(
                        nutrientId = r.nutrient.id,
                        name = r.nutrient.displayName,
                        unit = r.nutrient.unit,
                        category = r.nutrient.category,
                        amount = r.amount.toString()
                    )
                },
                favorite = flags?.favorite ?: false,
                eatMore = flags?.eatMore ?: false,
                limit = flags?.limit ?: false,
            )
        }
    }

    fun onNameChange(v: String) = update { it.copy(name = v, errorMessage = null) }
    fun onBrandChange(v: String) = update { it.copy(brand = v) }
    fun onServingSizeChange(v: String) = update { it.copy(servingSize = v) }
    fun onServingUnitChange(v: ServingUnit) = update { it.copy(servingUnit = v) }
    fun onGramsPerServingChange(v: String) = update { it.copy(gramsPerServing = v) }
    fun onServingsPerPackageChange(v: String) = update { it.copy(servingsPerPackage = v) }

    // Flags
    fun onFavoriteChange(v: Boolean) = update { it.copy(favorite = v) }
    fun onEatMoreChange(v: Boolean) = update { it.copy(eatMore = v) }
    fun onLimitChange(v: Boolean) = update { it.copy(limit = v) }


    fun onNutrientAmountChange(nutrientId: Long, amount: String) {
        update { s ->
            s.copy(
                nutrientRows = s.nutrientRows.map {
                    if (it.nutrientId == nutrientId) it.copy(amount = amount) else it
                }
            )
        }
    }

    fun removeNutrientRow(nutrientId: Long) {
        update { s -> s.copy(nutrientRows = s.nutrientRows.filterNot { it.nutrientId == nutrientId }) }
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
        val gramsPerServing = parseDoubleOrNull(s.gramsPerServing)
        val servingsPerPackage = parseDoubleOrNull(s.servingsPerPackage)

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
            gramsPerServing = gramsPerServing,
            servingsPerPackage = servingsPerPackage,
            isRecipe = false,
            stableId = stableId
        )

        val rows: List<FoodNutrientRow> = s.nutrientRows.mapNotNull { ui ->
            val amt = ui.amount.trim()
            val amount = if (amt.isEmpty()) 0.0 else (amt.toDoubleOrNull() ?: return@mapNotNull null)

            FoodNutrientRow(
                nutrient = Nutrient(
                    id = ui.nutrientId,
                    code = "", // editor doesn’t need code to save
                    displayName = ui.name,
                    unit = ui.unit,
                    category = ui.category
                ),
                amount = amount,
                basisType = BasisType.PER_SERVING,
                basisGrams = null
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
                AliasAddResult.Added -> aliasSheetMessageFlow.value = null
                AliasAddResult.IgnoredEmpty -> aliasSheetMessageFlow.value = "Alias is empty."
                AliasAddResult.IgnoredDuplicate -> aliasSheetMessageFlow.value = "Alias already exists."
            }
        }
    }

    fun deleteAlias(alias: String) {
        val id = aliasSheetNutrientIdFlow.value ?: return
        viewModelScope.launch {
            nutrientAliasRepo.deleteAlias(id, alias)
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
                                category = n.category
                            )
                        }
                    )
                }
            }
        }
    }
}
