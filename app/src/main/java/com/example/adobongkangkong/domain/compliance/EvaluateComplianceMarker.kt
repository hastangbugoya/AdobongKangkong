package com.example.adobongkangkong.domain.compliance

import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.ui.calendar.model.TargetRange

/**
 * Generic mapping:
 * - If no bounds at all -> NO_TARGET
 * - If min-only: MISS if < min; HIT if >= target (if target exists); else OK
 * - If max-only: MISS if > max; HIT if <= target (if target exists); else OK
 * - If min+max: MISS if outside [min,max]; HIT if meets target direction (if target exists); else OK
 */
fun evaluateMarker(consumed: Double, range: TargetRange): TargetStatus {
    val min = range.min
    val target = range.target
    val max = range.max

    if (min == null && max == null && target == null) return TargetStatus.NO_TARGET

    // lower-is-better (max-only) if min is null and max is not
    val lowerBetter = (min == null && max != null)
    val higherBetter = (min != null && max == null)
    val ranged = (min != null && max != null)

    if (higherBetter) {
        if (consumed < min!!) return TargetStatus.LOW
        return TargetStatus.OK
    }

    if (lowerBetter) {
        if (consumed > max!!) return TargetStatus.HIGH
        return TargetStatus.OK
    }

    if (ranged) {
        val lo = min!!
        val hi = max!!
        if (consumed > lo && consumed < hi) return TargetStatus.OK
        // If a target is provided, choose a direction based on which side it’s closer to:
        // - If target is closer to lo, treat as higher-better; if closer to hi, treat as lower-better.
        // (This avoids needing an extra “direction” flag.)
        if (target != null) {
            val midpoint = (lo + hi) / 2.0
            val treatHigherBetter = target >= midpoint
            return if (treatHigherBetter) {
                if (consumed >= target) TargetStatus.OK else TargetStatus.LOW
            } else {
                if (consumed <= target) TargetStatus.OK else TargetStatus.HIGH
            }
        }
        return TargetStatus.OK // compliant range with no target
    }

    // target-only case (rare): treat “hit” as meeting/exceeding target
    if (target != null) {
        return if (consumed >= target) TargetStatus.OK else TargetStatus.LOW
    }

    return TargetStatus.OK
}
