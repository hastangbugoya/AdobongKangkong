package com.example.adobongkangkong.domain.food.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType

/**
 * Resolves the "grams per serving" bridge value when possible, without changing the food's canonical
 * nutrient basis.
 *
 * Why this exists:
 * - Your canonical nutrient math is controlled by a BasisType (e.g., PER_100G vs PER_SERVING).
 * - Some UI / logging flows want to accept mass (grams) even when the canonical basis is serving- or
 *   volume-based. That requires a reliable gramsPerServing value.
 *
 * Rules:
 * 1) If gramsPerServing is already known, keep it (and keep/assume its source).
 * 2) Else if mlPerServing is known, derive gramsPerServing using the "water density default":
 *      1 mL ≈ 1 g
 *    and mark the result as ESTIMATED_WATER_DENSITY so UI can show an "Estimated" badge.
 * 3) Else return null gramsPerServing (no derivation possible).
 *
 * Notes:
 * - This use case does NOT convert nutrients, and does NOT change BasisType.
 * - If a user edits gramsPerServing, pass source = USER_OVERRIDE so derivation won't overwrite it.
 */
class ResolveServingMassUseCase {

    /**
     * @param basisType Canonical nutrient basis. Included for call-site clarity / future-proofing.
     *                 This use case does not branch on basisType today, but keeping it in the
     *                 signature makes it explicit that canonicalization lives elsewhere.
     * @param gramsPerServing Known grams per serving (canonical for mass bridge). If non-null, this
     *                        always wins.
     * @param mlPerServing Known milliliters per serving. Used only as a fallback when grams is null.
     * @param source Where gramsPerServing came from, if already known. If gramsPerServing is non-null
     *               but source is null, we conservatively treat it as PROVIDED_BY_SOURCE.
     */
    operator fun invoke(
        basisType: BasisType,
        gramsPerServing: Double?,
        mlPerServing: Double?,
        source: ServingMassSource?
    ): Result {
        // 1) Already have grams: keep it (and normalize missing source).
        if (gramsPerServing != null) {
            return Result(
                gramsPerServing = gramsPerServing,
                source = source ?: ServingMassSource.PROVIDED_BY_SOURCE
            )
        }

        // 2) No grams, but we have mL: estimate grams using water density.
        if (mlPerServing != null) {
            return Result(
                gramsPerServing = mlPerServing, // water default: 1 mL ≈ 1 g
                source = ServingMassSource.ESTIMATED_WATER_DENSITY
            )
        }

        // 3) Can't resolve.
        return Result(
            gramsPerServing = null,
            source = null
        )
    }

    /**
     * Result is private to avoid creating additional package-level model types that leak across the
     * codebase. This use case is intended to be called and immediately applied at the call-site.
     *
     * If you later want to persist the resolved values, do so by assigning:
     * - entity.gramsPerServing = result.gramsPerServing
     * - entity.gramsPerServingSource = result.source?.name (or a converter)
     */
    data class Result(
        val gramsPerServing: Double?,
        val source: ServingMassSource?
    )

    /**
     * Minimal basis enum for the signature.
     * Keep using your existing project enum if you already have one; in that case, delete this and
     * import your project's BasisType instead.
     */
//    enum class BasisType {
//        PER_100G,
//        PER_SERVING,
//        PER_100ML
//    }

    /**
     * Tracks how gramsPerServing was obtained so UI can communicate confidence and so user edits can
     * be preserved intentionally.
     *
     * - ESTIMATED_WATER_DENSITY: Derived from mlPerServing assuming 1 mL = 1 g.
     * - PROVIDED_BY_SOURCE: Imported / parsed from USDA or label metadata (non-user).
     * - USER_OVERRIDE: User manually entered / confirmed gramsPerServing.
     */
    enum class ServingMassSource {
        ESTIMATED_WATER_DENSITY,
        PROVIDED_BY_SOURCE,
        USER_OVERRIDE
    }
}
