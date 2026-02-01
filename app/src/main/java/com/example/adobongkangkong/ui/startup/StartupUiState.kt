package com.example.adobongkangkong.ui.startup

data class StartupUiState(
    val isWorking: Boolean = false,
    val isDone: Boolean = false,
    val message: String? = null,
    val error: String? = null
)