package com.example.adobongkangkong.feature.camera

/**
 * Stable owner reference for reusable banner infrastructure.
 *
 * We keep a single banner storage/rendering pipeline for foods and meal templates so future
 * development does not fork into two incompatible approaches.
 */
enum class BannerOwnerType {
    FOOD,
    TEMPLATE
}

data class BannerOwnerRef(
    val type: BannerOwnerType,
    val id: Long
)
