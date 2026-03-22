package com.example.adobongkangkong.ui.food.editor

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.domain.usda.model.CollisionReason

/**
 * 3-button collision prompt state:
 * Remap / Open Existing / Cancel
 *
 * This is distinct from BarcodeRemapDialogState (YES/NO) which is used for explicit user-driven remaps
 * when assigning barcodes manually.
 */