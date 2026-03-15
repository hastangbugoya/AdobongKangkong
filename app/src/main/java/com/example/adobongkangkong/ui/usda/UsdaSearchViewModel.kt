package com.example.adobongkangkong.ui.usda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.usda.ImportUsdaFoodFromSearchJsonUseCase
import com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByKeywordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UsdaSearchViewModel @Inject constructor(
    private val searchUsdaFoodsByKeywords: SearchUsdaFoodsByKeywordsUseCase,
    private val importUsdaFoodFromSearchJson: ImportUsdaFoodFromSearchJsonUseCase
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val isSearching: Boolean = false,
        val isImporting: Boolean = false,
        val results: List<SearchUsdaFoodsByKeywordsUseCase.PickItem> = emptyList(),
        val errorMessage: String? = null,
        val lastSearchedQuery: String? = null,
        val pageNumber: Int = 1,
    )

    private val stateFlow = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = stateFlow.asStateFlow()

    private val snackbarFlow = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = snackbarFlow.asStateFlow()

    private var lastSearchJson: String? = null

    fun onQueryChanged(value: String) {
        stateFlow.value = stateFlow.value.copy(
            query = value,
            errorMessage = null
        )
    }

    fun search(pageSize: Int = 20) {
        val query = stateFlow.value.query.trim()
        if (query.isBlank()) {
            stateFlow.value = stateFlow.value.copy(
                errorMessage = "Enter USDA search keywords."
            )
            return
        }

        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(
                isSearching = true,
                errorMessage = null,
                results = emptyList()
            )

            try {
                when (
                    val result = searchUsdaFoodsByKeywords(
                        query = query,
                        pageSize = pageSize,
                        pageNumber = 1
                    )
                ) {
                    is SearchUsdaFoodsByKeywordsUseCase.Result.Success -> {
                        lastSearchJson = result.searchJson
                        stateFlow.value = stateFlow.value.copy(
                            isSearching = false,
                            results = result.candidates,
                            errorMessage = null,
                            lastSearchedQuery = result.query,
                            pageNumber = result.pageNumber
                        )
                    }

                    is SearchUsdaFoodsByKeywordsUseCase.Result.Blocked -> {
                        lastSearchJson = null
                        stateFlow.value = stateFlow.value.copy(
                            isSearching = false,
                            results = emptyList(),
                            errorMessage = result.reason,
                            lastSearchedQuery = query,
                            pageNumber = 1
                        )
                    }

                    is SearchUsdaFoodsByKeywordsUseCase.Result.Failed -> {
                        lastSearchJson = null
                        stateFlow.value = stateFlow.value.copy(
                            isSearching = false,
                            results = emptyList(),
                            errorMessage = result.message,
                            lastSearchedQuery = query,
                            pageNumber = 1
                        )
                    }
                }
            } catch (t: Throwable) {
                lastSearchJson = null
                stateFlow.value = stateFlow.value.copy(
                    isSearching = false,
                    results = emptyList(),
                    errorMessage = t.message ?: "USDA search failed.",
                    lastSearchedQuery = query,
                    pageNumber = 1
                )
            }
        }
    }

    fun importSelected(fdcId: Long) {
        val searchJson = lastSearchJson
        if (searchJson.isNullOrBlank()) {
            snackbarFlow.value = "USDA search data is unavailable."
            return
        }

        if (stateFlow.value.isImporting) return

        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(
                isImporting = true,
                errorMessage = null
            )

            try {
                when (val result = importUsdaFoodFromSearchJson(searchJson, selectedFdcId = fdcId)) {
                    is ImportUsdaFoodFromSearchJsonUseCase.Result.Success -> {
                        stateFlow.value = stateFlow.value.copy(
                            isImporting = false
                        )
                        snackbarFlow.value = "Imported USDA food."
                    }

                    is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked -> {
                        stateFlow.value = stateFlow.value.copy(
                            isImporting = false,
                            errorMessage = result.reason
                        )
                    }
                }
            } catch (t: Throwable) {
                stateFlow.value = stateFlow.value.copy(
                    isImporting = false,
                    errorMessage = t.message ?: "USDA import failed."
                )
            }
        }
    }

    fun clearError() {
        stateFlow.value = stateFlow.value.copy(errorMessage = null)
    }

    fun snackbarShown() {
        snackbarFlow.value = null
    }
}
