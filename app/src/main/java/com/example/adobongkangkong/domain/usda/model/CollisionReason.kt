package com.example.adobongkangkong.domain.usda.model

enum class CollisionReason {
    ExistingUserAssignedMapping,
    ExistingUsdaFdcIdMismatch,      // barcode mapped to USDA A, scan suggests USDA B
    ExistingMappingCorruptMissingFood // optional future
}
