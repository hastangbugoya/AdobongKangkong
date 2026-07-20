package com.example.adobongkangkong.data.local.db.entity

/**
 * String constants for BodyWeightMeasurementEntity.source.
 *
 * Kept as String constants instead of an enum converter so Room migrations stay
 * simple while the Health Connect weight-import MVP is being shaped.
 */
object BodyWeightMeasurementSource {
    const val MANUAL = "MANUAL"
    const val HEALTH_CONNECT = "HEALTH_CONNECT"
    const val LEGACY_WEIGHT_LOG = "LEGACY_WEIGHT_LOG"
}

/**
 * String constants for BodyWeightLogEntity.trendSelectionMethod.
 *
 * These values allow AK to preserve how a daily trend weight was chosen while
 * still supporting a future user preference such as “use the reading closest to
 * 7:00 AM.”
 */
object BodyWeightTrendSelectionMethod {
    const val CLOSEST_TO_PREFERRED_TIME = "CLOSEST_TO_PREFERRED_TIME"
    const val FIRST_OF_DAY = "FIRST_OF_DAY"
    const val LATEST_OF_DAY = "LATEST_OF_DAY"
    const val LOWEST_OF_DAY = "LOWEST_OF_DAY"
    const val HIGHEST_OF_DAY = "HIGHEST_OF_DAY"
    const val AVERAGE_OF_DAY = "AVERAGE_OF_DAY"
    const val MANUAL_SELECTED = "MANUAL_SELECTED"
}
