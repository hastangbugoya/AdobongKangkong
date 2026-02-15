package com.example.adobongkangkong.domain.usda.model

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource

data class BarcodeRemapDialogState(
    val barcode: String,
    val fromFoodId: Long,
    val toFoodId: Long,
    val fromSource: BarcodeMappingSource
    )