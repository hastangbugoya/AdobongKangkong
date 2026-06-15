package com.example.adobongkangkong.ui.recipevariant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.domain.usecase.recipevariant.ArchiveRecipeVariantUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.CreateRecipeVariantUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.DeleteArchivedRecipeVariantUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.ObserveRecipeVariantsForRecipeUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.RestoreRecipeVariantUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecipeVariantFilter {
    CURRENT,
    ARCHIVED,
    ALL,
}

data class RecipeVariantListUiState(
    val recipeFoodId: Long = 0L,
    val filter: RecipeVariantFilter = RecipeVariantFilter.CURRENT,
    val variants: List<RecipeVariantEntity> = emptyList(),
    val visibleVariants: List<RecipeVariantEntity> = emptyList(),
    val isCreateDialogOpen: Boolean = false,
    val pendingDeleteVariant: RecipeVariantEntity? = null,
    val newVariantName: String = "",
    val newVariantNotes: String = "",
    val errorMessage: String? = null,
)

private data class CreateVariantDialogState(
    val isOpen: Boolean = false,
    val name: String = "",
    val notes: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
class RecipeVariantListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecipeVariantsForRecipe: ObserveRecipeVariantsForRecipeUseCase,
    private val createRecipeVariant: CreateRecipeVariantUseCase,
    private val archiveRecipeVariant: ArchiveRecipeVariantUseCase,
    private val restoreRecipeVariant: RestoreRecipeVariantUseCase,
    private val deleteArchivedRecipeVariant: DeleteArchivedRecipeVariantUseCase,
) : ViewModel() {

    private val recipeFoodId: Long =
        savedStateHandle.get<Long>("recipeFoodId")
            ?: savedStateHandle.get<String>("recipeFoodId")?.toLongOrNull()
            ?: error("recipeFoodId is required")

    private val filter = MutableStateFlow(RecipeVariantFilter.CURRENT)
    private val isCreateDialogOpen = MutableStateFlow(false)
    private val newVariantName = MutableStateFlow("")
    private val newVariantNotes = MutableStateFlow("")
    private val errorMessage = MutableStateFlow<String?>(null)
    private val pendingDeleteVariantId = MutableStateFlow<Long?>(null)

    private val variants = observeRecipeVariantsForRecipe(recipeFoodId)

    private val createDialogState =
        combine(
            isCreateDialogOpen,
            newVariantName,
            newVariantNotes,
            errorMessage,
        ) { isOpen, name, notes, errorMessage ->
            CreateVariantDialogState(
                isOpen = isOpen,
                name = name,
                notes = notes,
                errorMessage = errorMessage,
            )
        }

    val uiState: StateFlow<RecipeVariantListUiState> =
        combine(
            variants,
            filter,
            createDialogState,
            pendingDeleteVariantId,
        ) { variants, selectedFilter, createDialogState, pendingDeleteVariantId ->
            val visibleVariants = when (selectedFilter) {
                RecipeVariantFilter.CURRENT -> variants.filterNot { it.isArchived }
                RecipeVariantFilter.ARCHIVED -> variants.filter { it.isArchived }
                RecipeVariantFilter.ALL -> variants
            }

            val pendingDeleteVariant = pendingDeleteVariantId?.let { pendingId ->
                variants.firstOrNull { variant ->
                    variant.id == pendingId && variant.isArchived
                }
            }

            RecipeVariantListUiState(
                recipeFoodId = recipeFoodId,
                filter = selectedFilter,
                variants = variants,
                visibleVariants = visibleVariants,
                isCreateDialogOpen = createDialogState.isOpen,
                pendingDeleteVariant = pendingDeleteVariant,
                newVariantName = createDialogState.name,
                newVariantNotes = createDialogState.notes,
                errorMessage = createDialogState.errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecipeVariantListUiState(recipeFoodId = recipeFoodId),
        )

    fun setFilter(newFilter: RecipeVariantFilter) {
        filter.value = newFilter
    }

    fun openCreateDialog() {
        errorMessage.value = null
        newVariantName.value = ""
        newVariantNotes.value = ""
        isCreateDialogOpen.value = true
    }

    fun closeCreateDialog() {
        isCreateDialogOpen.value = false
    }

    fun updateNewVariantName(value: String) {
        newVariantName.value = value
    }

    fun updateNewVariantNotes(value: String) {
        newVariantNotes.value = value
    }

    fun createVariant() {
        val name = newVariantName.value.trim()

        if (name.isBlank()) {
            errorMessage.value = "Variant name cannot be blank."
            return
        }

        viewModelScope.launch {
            runCatching {
                createRecipeVariant(
                    recipeFoodId = recipeFoodId,
                    name = name,
                    notes = newVariantNotes.value,
                )
            }.onSuccess {
                isCreateDialogOpen.value = false
                newVariantName.value = ""
                newVariantNotes.value = ""
                errorMessage.value = null
                filter.value = RecipeVariantFilter.CURRENT
            }.onFailure { throwable ->
                errorMessage.value =
                    throwable.message ?: "Could not create variant."
            }
        }
    }

    fun archiveVariant(variantId: Long) {
        viewModelScope.launch {
            runCatching {
                archiveRecipeVariant(variantId)
            }.onFailure { throwable ->
                errorMessage.value =
                    throwable.message ?: "Could not archive variant."
            }
        }
    }

    fun restoreVariant(variantId: Long) {
        viewModelScope.launch {
            runCatching {
                restoreRecipeVariant(variantId)
            }.onFailure { throwable ->
                errorMessage.value =
                    throwable.message ?: "Could not restore variant."
            }
        }
    }

    fun openDeleteArchivedVariantDialog(variantId: Long) {
        errorMessage.value = null
        pendingDeleteVariantId.value = variantId
    }

    fun closeDeleteArchivedVariantDialog() {
        pendingDeleteVariantId.value = null
    }

    fun deletePendingArchivedVariant() {
        val variantId = pendingDeleteVariantId.value ?: return

        viewModelScope.launch {
            runCatching {
                deleteArchivedRecipeVariant(variantId)
            }.onSuccess {
                pendingDeleteVariantId.value = null
                errorMessage.value = null
                filter.value = RecipeVariantFilter.ARCHIVED
            }.onFailure { throwable ->
                errorMessage.value =
                    throwable.message ?: "Could not delete archived variant."
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}