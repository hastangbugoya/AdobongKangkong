package com.example.adobongkangkong.ui.recipevariant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeType
import com.example.adobongkangkong.domain.model.AssembledRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.model.RemovedRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.model.RecipeVariantMacroComparison
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import com.example.adobongkangkong.domain.usecase.recipevariant.AssembleRecipeVariantUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.CompareRecipeVariantMacrosUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.SaveRecipeVariantChangesUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.UpdateRecipeVariantUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeVariantEditorUiState(
    val recipeFoodId: Long = 0L,
    val variantId: Long = 0L,

    val recipeName: String = "",
    val name: String = "",
    val notes: String = "",

    val originalName: String = "",
    val originalNotes: String = "",

    val finalIngredientLines: List<AssembledRecipeVariantIngredientLine> = emptyList(),
    val removedIngredientLines: List<RemovedRecipeVariantIngredientLine> = emptyList(),
    val warnings: List<String> = emptyList(),
    val macroComparison: RecipeVariantMacroComparison? = null,

    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val draftChanges: List<RecipeVariantIngredientChangeEntity> = emptyList(),
    val originalDraftChanges: List<RecipeVariantIngredientChangeEntity> = emptyList(),
) {
    val hasUnsavedChanges: Boolean
        get() = name != originalName ||
                notes != originalNotes ||
                draftChanges != originalDraftChanges

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
    private val updateRecipeVariant: UpdateRecipeVariantUseCase,
    private val assembleRecipeVariant: AssembleRecipeVariantUseCase,
    private val compareRecipeVariantMacros: CompareRecipeVariantMacrosUseCase,
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
                        finalIngredientLines = assembled.finalIngredientLines,
                        removedIngredientLines = assembled.removedIngredientLines,
                        warnings = macroComparison.warnings.distinct(),
                        macroComparison = macroComparison,
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
                )

                _uiState.update {
                    it.copy(
                        recipeName = assembled.recipeName,
                        finalIngredientLines = assembled.finalIngredientLines,
                        removedIngredientLines = assembled.removedIngredientLines,
                        warnings = macroComparison.warnings.distinct(),
                        macroComparison = macroComparison,
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

    fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }
}