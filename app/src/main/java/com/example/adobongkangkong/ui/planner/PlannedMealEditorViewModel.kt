package com.example.adobongkangkong.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientWithMetaRow
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedOccurrenceStatus
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.GetFoodNutrientsWithMetaUseCase
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import com.example.adobongkangkong.ui.meal.editor.MealEditorContract
import com.example.adobongkangkong.ui.meal.editor.MealEditorMode
import com.example.adobongkangkong.ui.meal.editor.MealEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Planned Meal editor ViewModel.
 *
 * FoodEditor parity rules (behavior contract):
 * - Opening the editor in "new" mode MUST NOT write to the database.
 * - Only an explicit Save commits changes.
 * - Back/Cancel never saves.
 *
 * New vs Edit:
 * - New (draft): state.mealId == null, draftDateIso/draftSlot are set via startNewPlannedMeal()
 * - Edit: state.mealId != null, loaded via setMealId()
 */
@HiltViewModel
class PlannedMealEditorViewModel @Inject constructor(
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository,
    private val foods: FoodRepository,
    private val mealTemplates: MealTemplateRepository,
    private val mealTemplateItems: MealTemplateItemRepository,
    private val getFoodNutrients: GetFoodNutrientsWithMetaUseCase,
    private val userPinnedNutrientRepository: UserPinnedNutrientRepository,
) : ViewModel(), MealEditorContract {

    private val _state = MutableStateFlow(
        MealEditorUiState(
            mealId = null,
            name = "",
            mode = MealEditorMode.PLANNED,
            subtitle = null,
            items = emptyList(),
            isSaving = false,
            canSave = true,
            errorMessage = null
        )
    )

    override val state: StateFlow<MealEditorUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 1)
    val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    sealed interface Effect {
        data object Saved : Effect
    }

    // Draft-only fields for NEW planned meals (no DB row until Save).
    private var draftDateIso: String? = null
    private var draftSlot: MealSlot? = null
    private var draftTemplateId: Long? = null

    // Critical nutrients sourced from the same dashboard preference system.
    private var criticalNutrientKeys: Set<String> = emptySet()

    // Simple in-VM caches to reduce repeat DB reads during rebuilds.
    private val foodCache = mutableMapOf<Long, Food?>()
    private val nutrientCache = mutableMapOf<Long, List<FoodNutrientWithMetaRow>>()

    init {
        viewModelScope.launch {
            userPinnedNutrientRepository.observePreferences()
                .collect { prefs ->
                    criticalNutrientKeys = prefs
                        .filter { it.isCritical }
                        .map { it.key.value }
                        .toSet()
                    rebuildDerivedNutrition()
                }
        }
    }

    /**
     * Initialize the editor for creating a NEW planned meal (draft mode).
     *
     * IMPORTANT:
     * - This must not insert into the DB.
     * - The meal row is inserted only when Save is pressed.
     *
     * Returning from the food picker re-enters this route. In that case we must NOT
     * wipe the in-memory draft that already has picked foods / edits.
     */
    fun startNewPlannedMeal(
        dateIso: String,
        slot: MealSlot,
        subtitle: String? = null,
        templateId: Long? = null
    ) {
        val current = _state.value
        val sameDraftContext =
            current.mealId == null &&
                    draftDateIso == dateIso &&
                    draftSlot == slot &&
                    draftTemplateId == templateId

        val hasDraftContent =
            current.items.isNotEmpty() ||
                    current.isDirty ||
                    current.name.isNotBlank()

        if (sameDraftContext && hasDraftContent) {
            if (current.subtitle != subtitle) {
                _state.value = current.copy(subtitle = subtitle)
            }
            return
        }

        draftDateIso = dateIso
        draftSlot = slot
        draftTemplateId = templateId

        _state.value = _state.value.copy(
            mealId = null,
            mode = MealEditorMode.PLANNED,
            subtitle = subtitle,
            name = defaultNameOverride(slot, dateIso),
            items = emptyList(),
            errorMessage = null,
            isDirty = false,
            warnings = emptyList(),
            mealMacroPreview = null,
            liveMacroTotals = null,
            liveMacroSummaryLine = null,
            criticalNutrientTotals = emptyList(),
            hasUnknownCriticalNutrients = false
        )

        if (templateId != null && templateId > 0L) {
            viewModelScope.launch {
                try {
                    val template = mealTemplates.getById(templateId)
                        ?: throw IllegalStateException("Template not found.")
                    val templateItems = mealTemplateItems.getItemsForTemplate(templateId)
                        .sortedBy { it.sortOrder }

                    val uiItems = templateItems.map { entity ->
                        val foodName = getCachedFood(entity.refId)?.name ?: "Food #${entity.refId}"
                        MealEditorUiState.Item(
                            lineId = UUID.randomUUID().toString(),
                            id = null,
                            foodId = entity.refId,
                            foodName = foodName,
                            servings = entity.servings?.toString() ?: "",
                            grams = entity.grams,
                            milliliters = null
                        )
                    }

                    val currentState = _state.value
                    val stillSameDraft = currentState.mealId == null &&
                            draftDateIso == dateIso &&
                            draftSlot == slot &&
                            draftTemplateId == templateId

                    if (stillSameDraft && !currentState.isDirty) {
                        _state.value = currentState.copy(
                            name = template.name.ifBlank { defaultNameOverride(slot, dateIso) },
                            items = uiItems,
                            errorMessage = null,
                            isDirty = false,
                            warnings = emptyList()
                        )
                        rebuildDerivedNutrition()
                    }
                } catch (t: Throwable) {
                    _state.value = _state.value.copy(
                        errorMessage = t.message ?: "Failed to load template."
                    )
                }
            }
        } else {
            rebuildDerivedNutrition()
        }
    }

    /**
     * Initialize the editor for EDITING an existing planned meal.
     * (Route-level wiring.)
     */
    fun setMealId(mealId: Long, subtitle: String? = null) {
        draftDateIso = null
        draftSlot = null
        draftTemplateId = null

        _state.value = _state.value.copy(mealId = mealId, subtitle = subtitle, errorMessage = null)

        viewModelScope.launch {
            try {
                val meal = plannedMeals.getById(mealId)
                val existing = plannedItems.getItemsForMeal(mealId).sortedBy { it.sortOrder }

                val uiItems = existing.map { entity ->
                    val foodName = getCachedFood(entity.refId)?.name ?: "Food #${entity.refId}"
                    MealEditorUiState.Item(
                        lineId = UUID.randomUUID().toString(),
                        id = entity.id,
                        foodId = entity.refId,
                        foodName = foodName,
                        servings = entity.servings?.toString() ?: "",
                        grams = entity.grams,
                        milliliters = null
                    )
                }

                val resolvedName = if (meal != null) {
                    meal.nameOverride ?: defaultNameOverride(meal.slot, meal.date)
                } else {
                    _state.value.name
                }

                val current = _state.value
                if (!current.isDirty) {
                    _state.value = current.copy(
                        name = resolvedName,
                        items = uiItems,
                        errorMessage = null,
                        isDirty = false,
                        warnings = buildWarnings(meal)
                    )
                    rebuildDerivedNutrition()
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    errorMessage = t.message ?: "Failed to load meal."
                )
            }
        }
    }

    override fun setName(name: String) {
        _state.value = _state.value.copy(name = name, errorMessage = null, isDirty = true)
    }

    override fun addFood(foodId: Long) {
        viewModelScope.launch {
            val foodName = getCachedFood(foodId)?.name ?: "Food #$foodId"

            val newItem = MealEditorUiState.Item(
                lineId = UUID.randomUUID().toString(),
                id = null,
                foodId = foodId,
                foodName = foodName,
                servings = "1",
                grams = null,
                milliliters = null
            )

            _state.value = _state.value.copy(
                items = _state.value.items + newItem,
                errorMessage = null,
                isDirty = true
            )

            rebuildDerivedNutrition()
        }
    }

    override fun updateServings(lineId: String, servingsText: String) {
        _state.value = _state.value.copy(
            items = _state.value.items.map {
                if (it.lineId == lineId) it.copy(servings = servingsText) else it
            },
            errorMessage = null,
            isDirty = true
        )
        rebuildDerivedNutrition()
    }

    override fun updateGrams(lineId: String, grams: String) {
        val g = grams.toDoubleOrNull()
        _state.value = _state.value.copy(
            items = _state.value.items.map {
                if (it.lineId == lineId) it.copy(grams = g) else it
            },
            errorMessage = null,
            isDirty = true
        )
        rebuildDerivedNutrition()
    }

    override fun updateMilliliters(lineId: String, ml: String) {
        val v = ml.toDoubleOrNull()
        _state.value = _state.value.copy(
            items = _state.value.items.map {
                if (it.lineId == lineId) it.copy(milliliters = v) else it
            },
            errorMessage = null,
            isDirty = true
        )
        rebuildDerivedNutrition()
    }

    override fun removeItem(lineId: String) {
        _state.value = _state.value.copy(
            items = _state.value.items.filterNot { it.lineId == lineId },
            errorMessage = null,
            isDirty = true
        )
        rebuildDerivedNutrition()
    }

    override fun moveItem(fromIndex: Int, toIndex: Int) {
        val list = _state.value.items.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        _state.value = _state.value.copy(items = list, errorMessage = null, isDirty = true)
        rebuildDerivedNutrition()
    }

    override fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            try {
                val current = _state.value
                val existingMealId = current.mealId

                val resolvedMealId: Long =
                    if (existingMealId == null) {
                        val dateIso = draftDateIso
                        val slot = draftSlot
                        if (dateIso.isNullOrBlank() || slot == null) {
                            throw IllegalStateException("Cannot save: missing draft date/slot.")
                        }

                        val sortOrder = plannedMeals.getMaxSortOrderForDate(dateIso) + 1
                        val nameOverride = resolveNonNullNameOverrideForDraft(dateIso = dateIso, slot = slot)

                        val newId = plannedMeals.insert(
                            PlannedMealEntity(
                                id = 0L,
                                date = dateIso,
                                slot = slot,
                                customLabel = null,
                                nameOverride = nameOverride,
                                sortOrder = sortOrder,
                                seriesId = null
                            )
                        )

                        _state.value = _state.value.copy(mealId = newId, name = nameOverride)
                        newId
                    } else {
                        val meal = plannedMeals.getById(existingMealId)
                            ?: throw IllegalStateException("Cannot save: meal not found.")
                        val nameOverride = resolveNonNullNameOverrideForExisting(meal)
                        val shouldMarkOverridden = meal.seriesId != null && current.isDirty
                        val updatedMeal = meal.copy(
                            nameOverride = nameOverride,
                            status = if (shouldMarkOverridden) {
                                PlannedOccurrenceStatus.OVERRIDDEN.name
                            } else {
                                meal.status
                            }
                        )
                        if (meal != updatedMeal) {
                            plannedMeals.update(updatedMeal)
                        }
                        existingMealId
                    }

                plannedItems.deleteForMeal(resolvedMealId)
                _state.value.items.forEachIndexed { index, ui ->
                    plannedItems.insert(
                        PlannedItemEntity(
                            id = 0L,
                            mealId = resolvedMealId,
                            type = PlannedItemSource.FOOD,
                            refId = ui.foodId,
                            grams = ui.grams,
                            servings = ui.servings.toDoubleOrNull(),
                            sortOrder = index
                        )
                    )
                }

                _state.value = _state.value.copy(isDirty = false)
                rebuildDerivedNutrition()
                _effects.tryEmit(Effect.Saved)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Save failed")
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    override fun discardChanges() {
        val mealId = _state.value.mealId
        if (mealId == null) {
            val dateIso = draftDateIso
            val slot = draftSlot
            _state.value = _state.value.copy(
                name = if (dateIso != null && slot != null) defaultNameOverride(slot, dateIso) else _state.value.name,
                items = emptyList(),
                errorMessage = null,
                isDirty = false,
                mealMacroPreview = null,
                liveMacroTotals = null,
                liveMacroSummaryLine = null,
                criticalNutrientTotals = emptyList(),
                hasUnknownCriticalNutrients = false
            )
            rebuildDerivedNutrition()
            return
        }

        viewModelScope.launch {
            try {
                val meal = plannedMeals.getById(mealId)
                val existing = plannedItems.getItemsForMeal(mealId).sortedBy { it.sortOrder }

                val uiItems = existing.map { entity ->
                    val foodName = getCachedFood(entity.refId)?.name ?: "Food #${entity.refId}"
                    MealEditorUiState.Item(
                        lineId = UUID.randomUUID().toString(),
                        id = entity.id,
                        foodId = entity.refId,
                        foodName = foodName,
                        servings = entity.servings?.toString() ?: "",
                        grams = entity.grams,
                        milliliters = null
                    )
                }

                _state.value = _state.value.copy(
                    name = if (meal != null) resolveEditorName(meal) else _state.value.name,
                    items = uiItems,
                    errorMessage = null,
                    isDirty = false,
                    warnings = buildWarnings(meal)
                )
                rebuildDerivedNutrition()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Discard failed")
            }
        }
    }

    private suspend fun getCachedFood(foodId: Long): Food? {
        if (foodCache.containsKey(foodId)) return foodCache[foodId]
        val loaded = foods.getById(foodId)
        foodCache[foodId] = loaded
        return loaded
    }

    private suspend fun getCachedNutrients(foodId: Long): List<FoodNutrientWithMetaRow> {
        val cached = nutrientCache[foodId]
        if (cached != null) return cached
        val loaded = getFoodNutrients(foodId)
        nutrientCache[foodId] = loaded
        return loaded
    }

    private suspend fun prefetchFor(foodIds: List<Long>) {
        foodIds.distinct().forEach { foodId ->
            getCachedFood(foodId)
            getCachedNutrients(foodId)
        }
    }

    private fun rebuildDerivedNutrition() {
        viewModelScope.launch {
            val current = _state.value
            val foodIds = current.items.map { it.foodId }
            prefetchFor(foodIds)

            val updatedItems = current.items.map { item ->
                val food = getCachedFood(item.foodId)
                val nutrients = getCachedNutrients(item.foodId)

                val macros = computeMacros(item = item, food = food, rows = nutrients)
                val macroLine = buildMacroSummaryLine(macros)
                val critical = computeCritical(item = item, food = food, rows = nutrients)

                item.copy(
                    effectiveQuantityText = buildQuantityText(item, food),
                    macroPreview = macros,
                    macroSummaryLine = macroLine,
                    criticalNutrients = critical,
                    hasUnknownCriticalNutrients = critical.any { it.value == null }
                )
            }

            val mealMacroPreview = aggregateMacros(updatedItems)
            val criticalTotals = aggregateCritical(updatedItems)
            val hasUnknownCritical = updatedItems.any { it.hasUnknownCriticalNutrients }

            _state.update {
                it.copy(
                    items = updatedItems,
                    liveMacroTotals = mealMacroPreview?.toMacroTotals(),
                    liveMacroSummaryLine = buildMacroSummaryLine(mealMacroPreview),
                    mealMacroPreview = mealMacroPreview,
                    criticalNutrientTotals = criticalTotals,
                    hasUnknownCriticalNutrients = hasUnknownCritical
                )
            }
        }
    }

    private fun computeMacros(
        item: MealEditorUiState.Item,
        food: Food?,
        rows: List<FoodNutrientWithMetaRow>
    ): MealEditorUiState.MacroPreview {
        fun find(code: String): Double? {
            val row = rows.firstOrNull { it.code == code } ?: return null
            return scaleRowAmount(row = row, item = item, food = food)
        }

        return MealEditorUiState.MacroPreview(
            caloriesKcal = find("CALORIES_KCAL"),
            proteinG = find("PROTEIN_G"),
            carbsG = find("CARBS_G"),
            fatG = find("FAT_G")
        )
    }

    private fun computeCritical(
        item: MealEditorUiState.Item,
        food: Food?,
        rows: List<FoodNutrientWithMetaRow>
    ): List<MealEditorUiState.CriticalNutrientPreview> {
        val rowMap = rows.associateBy { it.code }

        return criticalNutrientKeys.map { code ->
            val row = rowMap[code]
            MealEditorUiState.CriticalNutrientPreview(
                nutrientId = row?.nutrientId ?: 0L,
                nutrientName = row?.displayName ?: code,
                unitName = row?.unit,
                value = row?.let { scaleRowAmount(row = it, item = item, food = food) },
                isMissing = row == null
            )
        }
    }

    private fun scaleRowAmount(
        row: FoodNutrientWithMetaRow,
        item: MealEditorUiState.Item,
        food: Food?
    ): Double? {
        val factor = resolveBasisFactor(
            basisType = row.basisType,
            item = item,
            food = food
        ) ?: return null

        return row.amount * factor
    }

    private fun resolveBasisFactor(
        basisType: BasisType,
        item: MealEditorUiState.Item,
        food: Food?
    ): Double? {
        val gramsOverride = item.grams
        val millilitersOverride = item.milliliters
        val servings = item.servings.toDoubleOrNull()

        return when (basisType) {
            BasisType.PER_100G -> {
                when {
                    gramsOverride != null -> gramsOverride / 100.0
                    millilitersOverride != null -> {
                        val gramsPerServing = food?.gramsPerServingUnit
                        val mlPerServing = food?.mlPerServingUnit
                        if (gramsPerServing != null && mlPerServing != null && mlPerServing > 0.0) {
                            val derivedGrams = millilitersOverride * (gramsPerServing / mlPerServing)
                            derivedGrams / 100.0
                        } else {
                            null
                        }
                    }
                    servings != null && food?.gramsPerServingUnit != null ->
                        (servings * food.gramsPerServingUnit) / 100.0
                    else -> null
                }
            }

            BasisType.PER_100ML -> {
                when {
                    millilitersOverride != null -> millilitersOverride / 100.0
                    gramsOverride != null -> {
                        val gramsPerServing = food?.gramsPerServingUnit
                        val mlPerServing = food?.mlPerServingUnit
                        if (gramsPerServing != null && gramsPerServing > 0.0 && mlPerServing != null) {
                            val derivedMl = gramsOverride * (mlPerServing / gramsPerServing)
                            derivedMl / 100.0
                        } else {
                            null
                        }
                    }
                    servings != null && food?.mlPerServingUnit != null ->
                        (servings * food.mlPerServingUnit) / 100.0
                    else -> null
                }
            }

            BasisType.USDA_REPORTED_SERVING -> {
                when {
                    servings != null -> servings
                    gramsOverride != null && food?.gramsPerServingUnit != null && food.gramsPerServingUnit > 0.0 ->
                        gramsOverride / food.gramsPerServingUnit
                    millilitersOverride != null && food?.mlPerServingUnit != null && food.mlPerServingUnit > 0.0 ->
                        millilitersOverride / food.mlPerServingUnit
                    else -> null
                }
            }
        }
    }

    private fun aggregateMacros(
        items: List<MealEditorUiState.Item>
    ): MealEditorUiState.MacroPreview? {
        if (items.isEmpty()) return null

        var hasAny = false
        var kcal = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0

        items.forEach { item ->
            val m = item.macroPreview ?: return@forEach
            if (m.caloriesKcal != null) {
                kcal += m.caloriesKcal
                hasAny = true
            }
            if (m.proteinG != null) {
                protein += m.proteinG
                hasAny = true
            }
            if (m.carbsG != null) {
                carbs += m.carbsG
                hasAny = true
            }
            if (m.fatG != null) {
                fat += m.fatG
                hasAny = true
            }
        }

        if (!hasAny) return null

        return MealEditorUiState.MacroPreview(
            caloriesKcal = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat
        )
    }

    private fun aggregateCritical(
        items: List<MealEditorUiState.Item>
    ): List<MealEditorUiState.CriticalNutrientPreview> {
        if (items.isEmpty()) return emptyList()

        val allRows = items.flatMap { it.criticalNutrients }
        if (allRows.isEmpty()) return emptyList()

        return allRows
            .groupBy { row ->
                if (row.nutrientId != 0L) "id:${row.nutrientId}" else "name:${row.nutrientName}"
            }
            .values
            .map { group ->
                val first = group.first()
                val knownValues = group.mapNotNull { it.value }
                MealEditorUiState.CriticalNutrientPreview(
                    nutrientId = first.nutrientId,
                    nutrientName = first.nutrientName,
                    unitName = first.unitName,
                    value = knownValues.takeIf { it.isNotEmpty() }?.sum(),
                    isMissing = group.any { it.isMissing },
                    isEstimated = group.any { it.isEstimated }
                )
            }
            .sortedBy { it.nutrientName }
    }

    private fun buildQuantityText(
        item: MealEditorUiState.Item,
        food: Food?
    ): String {
        item.grams?.let { return "${trimTrailingZero(it)} g" }
        item.milliliters?.let { return "${trimTrailingZero(it)} mL" }

        val servings = item.servings.toDoubleOrNull()
        if (servings != null) {
            val unitDisplay = food?.servingUnit?.display ?: "serving"
            return "${trimTrailingZero(servings)} $unitDisplay"
        }

        return ""
    }

    private fun buildMacroSummaryLine(
        preview: MealEditorUiState.MacroPreview?
    ): String? {
        preview ?: return null
        if (
            preview.caloriesKcal == null &&
            preview.proteinG == null &&
            preview.carbsG == null &&
            preview.fatG == null
        ) return null

        return "${trimNullable(preview.caloriesKcal)} kcal • " +
                "P ${trimNullable(preview.proteinG)} • " +
                "C ${trimNullable(preview.carbsG)} • " +
                "F ${trimNullable(preview.fatG)}"
    }

    private fun MealEditorUiState.MacroPreview.toMacroTotals(): com.example.adobongkangkong.domain.model.MacroTotals {
        return com.example.adobongkangkong.domain.model.MacroTotals(
            caloriesKcal = caloriesKcal ?: 0.0,
            proteinG = proteinG ?: 0.0,
            carbsG = carbsG ?: 0.0,
            fatG = fatG ?: 0.0
        )
    }

    private fun trimNullable(value: Double?): String {
        return value?.let { trimTrailingZero(it) } ?: "-"
    }

    private fun trimTrailingZero(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    private fun resolveEditorName(meal: PlannedMealEntity): String {
        return meal.nameOverride ?: defaultNameOverride(meal.slot, meal.date)
    }

    private fun buildWarnings(meal: PlannedMealEntity?): List<String> {
        if (meal?.seriesId == null) return emptyList()
        return listOf("Recurring occurrence: save applies to this occurrence only.")
    }

    private fun resolveNonNullNameOverrideForExisting(meal: PlannedMealEntity): String {
        val trimmed = _state.value.name.trim()
        return if (trimmed.isNotBlank()) {
            trimmed
        } else {
            defaultNameOverride(meal.slot, meal.date)
        }
    }

    private fun resolveNonNullNameOverrideForDraft(dateIso: String, slot: MealSlot): String {
        val trimmed = _state.value.name.trim()
        return if (trimmed.isNotBlank()) {
            trimmed
        } else {
            defaultNameOverride(slot, dateIso)
        }
    }

    private fun defaultNameOverride(slot: MealSlot, dateIso: String): String {
        val date = LocalDate.parse(dateIso)
        val dateStr = date.format(DateTimeFormatter.ofPattern("MMM-dd-yyyy", Locale.US))
        return "${slot.display}($dateStr)"
    }
}