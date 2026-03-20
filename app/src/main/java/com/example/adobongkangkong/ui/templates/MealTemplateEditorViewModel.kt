package com.example.adobongkangkong.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientWithMetaRow
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.GetFoodNutrientsWithMetaUseCase
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.ui.meal.editor.MealEditorContract
import com.example.adobongkangkong.ui.meal.editor.MealEditorMode
import com.example.adobongkangkong.ui.meal.editor.MealEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * ViewModel backing the meal template editor.
 *
 * For developers:
 * - Supports create/edit/save/discard for templates.
 * - Owns template-only actions: duplicate/delete.
 * - Computes advisory live macro totals from the current in-memory draft.
 * - Persists editable `defaultSlot` for template metadata.
 * - Row-level macro/quantity display mirrors planned-meal editor behavior.
 */
@HiltViewModel
class MealTemplateEditorViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository,
    private val foods: FoodRepository,
    private val getFoodNutrients: GetFoodNutrientsWithMetaUseCase
) : ViewModel(), MealEditorContract {

    sealed interface Effect {
        data class OpenTemplate(val templateId: Long) : Effect
        data class Deleted(val templateId: Long) : Effect
    }

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 1)
    val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    private val _state = MutableStateFlow(
        MealEditorUiState(
            mealId = null,
            name = "",
            mode = MealEditorMode.TEMPLATE,
            subtitle = null,
            items = emptyList(),
            isSaving = false,
            canSave = true,
            errorMessage = null,
            liveMacroSummaryLine = "0 kcal • P 0 • C 0 • F 0"
        )
    )
    override val state: StateFlow<MealEditorUiState> = _state.asStateFlow()

    private val foodCache = mutableMapOf<Long, Food?>()
    private val nutrientCache = mutableMapOf<Long, List<FoodNutrientWithMetaRow>>()

    fun setTemplateId(templateId: Long) {
        _state.value = _state.value.copy(mealId = templateId, errorMessage = null)
        viewModelScope.launch {
            try {
                val template = templates.getById(templateId)
                val items = templateItems.getItemsForTemplate(templateId).sortedBy { it.sortOrder }
                val uiItems = items.map { entity ->
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
                    name = template?.name ?: _state.value.name,
                    templateDefaultSlot = template?.defaultSlot,
                    items = uiItems,
                    errorMessage = null,
                    isDirty = false
                )
                rebuildDerivedNutrition()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Failed to load template")
            }
        }
    }

    override fun setName(name: String) {
        _state.value = _state.value.copy(name = name, errorMessage = null, isDirty = true)
    }

    override fun setTemplateDefaultSlot(slot: MealSlot?) {
        _state.value = _state.value.copy(templateDefaultSlot = slot, errorMessage = null, isDirty = true)
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
            val name = _state.value.name.trim()
            if (name.isBlank()) {
                _state.value = _state.value.copy(errorMessage = "Template name is required.")
                return@launch
            }
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            try {
                val existingTemplateId = _state.value.mealId
                val templateId = if (existingTemplateId != null && existingTemplateId > 0L) {
                    templates.update(
                        MealTemplateEntity(
                            id = existingTemplateId,
                            name = name,
                            defaultSlot = _state.value.templateDefaultSlot
                        )
                    )
                    existingTemplateId
                } else {
                    templates.insert(
                        MealTemplateEntity(
                            id = 0L,
                            name = name,
                            defaultSlot = _state.value.templateDefaultSlot
                        )
                    )
                }

                templateItems.deleteItemsForTemplate(templateId)
                _state.value.items.forEachIndexed { index, ui ->
                    val sourceType = if (foods.getById(ui.foodId)?.isRecipe == true) {
                        PlannedItemSource.RECIPE
                    } else {
                        PlannedItemSource.FOOD
                    }
                    templateItems.insert(
                        MealTemplateItemEntity(
                            id = 0L,
                            templateId = templateId,
                            type = sourceType,
                            refId = ui.foodId,
                            grams = if (sourceType == PlannedItemSource.RECIPE) null else ui.grams,
                            servings = ui.servings.toDoubleOrNull(),
                            sortOrder = index
                        )
                    )
                }
                _state.value = _state.value.copy(mealId = templateId, isDirty = false)
                rebuildDerivedNutrition()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Save failed")
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    fun duplicateTemplate() {
        viewModelScope.launch {
            try {
                val newId = templates.insert(
                    MealTemplateEntity(
                        id = 0L,
                        name = _state.value.name.ifBlank { "Template" } + " Copy",
                        defaultSlot = _state.value.templateDefaultSlot
                    )
                )
                _state.value.items.forEachIndexed { index, ui ->
                    val sourceType = if (foods.getById(ui.foodId)?.isRecipe == true) {
                        PlannedItemSource.RECIPE
                    } else {
                        PlannedItemSource.FOOD
                    }
                    templateItems.insert(
                        MealTemplateItemEntity(
                            id = 0L,
                            templateId = newId,
                            type = sourceType,
                            refId = ui.foodId,
                            grams = if (sourceType == PlannedItemSource.RECIPE) null else ui.grams,
                            servings = ui.servings.toDoubleOrNull(),
                            sortOrder = index
                        )
                    )
                }
                _effects.tryEmit(Effect.OpenTemplate(newId))
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Duplicate failed")
            }
        }
    }

    fun deleteTemplate() {
        val templateId = _state.value.mealId ?: return
        viewModelScope.launch {
            try {
                templateItems.deleteItemsForTemplate(templateId)
                templates.getById(templateId)?.let { templates.delete(it) }
                _effects.tryEmit(Effect.Deleted(templateId))
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Delete failed")
            }
        }
    }

    override fun discardChanges() {
        val templateId = _state.value.mealId
        if (templateId == null) {
            _state.value = _state.value.copy(
                errorMessage = null,
                isDirty = false,
                liveMacroTotals = null,
                liveMacroSummaryLine = "0 kcal • P 0 • C 0 • F 0",
                mealMacroPreview = null,
                items = _state.value.items.map {
                    it.copy(
                        macroPreview = null,
                        macroSummaryLine = null,
                        effectiveQuantityText = null
                    )
                }
            )
            rebuildDerivedNutrition()
            return
        }
        viewModelScope.launch {
            try {
                val template = templates.getById(templateId)
                val items = templateItems.getItemsForTemplate(templateId).sortedBy { it.sortOrder }
                val uiItems = items.map { entity ->
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
                    name = template?.name ?: _state.value.name,
                    templateDefaultSlot = template?.defaultSlot,
                    items = uiItems,
                    errorMessage = null,
                    isDirty = false
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

                item.copy(
                    effectiveQuantityText = buildQuantityText(item, food),
                    macroPreview = macros,
                    macroSummaryLine = buildMacroSummaryLine(macros)
                )
            }

            val mealMacroPreview = aggregateMacros(updatedItems)

            _state.update {
                it.copy(
                    items = updatedItems,
                    mealMacroPreview = mealMacroPreview,
                    liveMacroTotals = mealMacroPreview?.toMacroTotals(),
                    liveMacroSummaryLine = buildMacroSummaryLine(mealMacroPreview) ?: "0 kcal • P 0 • C 0 • F 0"
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
            String.format("%.2f", value).trimEnd('0').trimEnd('.')
        }
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This ViewModel intentionally owns template-only actions/effects so AppNavHost can react to
 * duplicate/delete navigation outcomes without putting template business logic into the nav layer.
 */