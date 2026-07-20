package com.example.adobongkangkong.domain.weight

/**
 * Rule used to choose one daily trend weight from one or more raw measurements.
 *
 * MVP can keep MANUAL_SELECTED for existing/manual entries and use
 * CLOSEST_TO_PREFERRED_TIME as the future default preference. User preference
 * values such as preferred time and minimum import gap should live in DataStore.
 */
enum class BodyWeightTrendSelectionMethod {
    CLOSEST_TO_PREFERRED_TIME,
    FIRST_OF_DAY,
    LATEST_OF_DAY,
    LOWEST_OF_DAY,
    HIGHEST_OF_DAY,
    AVERAGE_OF_DAY,
    MANUAL_SELECTED
}
