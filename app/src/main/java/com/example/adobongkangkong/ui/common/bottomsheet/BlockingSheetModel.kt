package com.example.adobongkangkong.ui.common.bottomsheet

data class BlockingSheetModel(
    val title: String,
    val message: String,
    val primaryButtonText: String,
    val secondaryButtonText: String? = null,
    val onPrimary: () -> Unit,
    val onSecondary: (() -> Unit)? = null
)