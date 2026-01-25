package com.example.adobongkangkong.domain.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.importing.model.ImportProgress
import com.example.adobongkangkong.domain.repository.ImportReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val runImport: RunFoodsCsvImportUseCase,
    private val reportRepo: ImportReportRepository
) : ViewModel() {

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress

    val latestRun = reportRepo.observeLatestRun().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startImport() {
        viewModelScope.launch {
            runImport.execute("foods.csv").collect { p ->
                _progress.value = p
            }
        }
    }
}
