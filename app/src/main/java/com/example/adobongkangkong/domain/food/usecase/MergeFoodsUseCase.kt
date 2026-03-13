package com.example.adobongkangkong.domain.food.usecase

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodBarcodeDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * Merges one food into another canonical food while preserving historical logs.
 *
 * ## Why this use case exists
 * In the app, multiple real-world package variants may actually represent the same
 * nutritional identity.
 *
 * Examples:
 * - same product sold in different package sizes
 * - duplicate foods created through barcode scans
 * - USDA/user-import overlap where one row becomes the canonical survivor
 *
 * This use case allows the app to converge those duplicate/package-specific foods into
 * a single canonical food row without breaking historical data.
 *
 * ## Core model
 * The merge direction is:
 * - `overrideFoodId -> canonicalFoodId`
 *
 * Meaning:
 * - the **override food** is the retiring/inactivated food
 * - the **canonical food** is the surviving nutrition identity
 *
 * After a successful merge:
 * - the canonical food remains active
 * - barcode rows that used to point to the override food now point to the canonical food
 * - missing nutrients from the override food are copied into the canonical food
 * - the override food is preserved as a historical row, but marked merged + soft-deleted
 * - historical logs are intentionally left untouched
 *
 * ## Why logs are not rewritten
 * Existing logs may reference the old food identity and may also contain nutrition snapshots
 * or stable references that reflect what existed at log time.
 *
 * Rewriting old logs during merge would be risky because it could:
 * - alter historical truth
 * - create confusing audit/history behavior
 * - silently mutate previously recorded user data
 *
 * Therefore this merge is designed as a **forward-looking identity convergence**:
 * future lookups and future barcode resolution should converge to the canonical food,
 * while past logs remain historically accurate.
 *
 * ## Transaction contract
 * The full merge operation runs inside a single DB transaction.
 *
 * This guarantees that the following state changes succeed or fail together:
 * - food validation
 * - nutrient copy
 * - barcode reassignment
 * - override-food merged/deleted state update
 *
 * This is important because a partial merge would be dangerous. For example:
 * - moving barcodes without marking the old food merged
 * - copying nutrients but failing barcode reassignment
 * - marking the old food deleted before reassignment succeeds
 *
 * ## Merge rules enforced here
 * - `overrideFoodId` must be a valid positive id
 * - `canonicalFoodId` must be a valid positive id
 * - the two ids must be different
 * - both food rows must exist
 * - the override food must not already be merged into another food
 * - the canonical food must not already be merged into another food
 * - the canonical food must not be soft-deleted
 *
 * ## Nutrient reconciliation policy
 * Nutrients are reconciled conservatively:
 *
 * - If the canonical food already has a nutrient id, the canonical value wins.
 * - If the canonical food does not have that nutrient id, the nutrient row(s) from the
 *   override food are copied over.
 *
 * Important nuance:
 * - reconciliation is done by `nutrientId`
 * - if canonical already has that nutrient id in **any** basis, the override nutrient is skipped
 * - if canonical does not have that nutrient id at all, all basis rows for that nutrient
 *   from the override food are copied
 *
 * This keeps the rule simple and predictable:
 * - canonical stays authoritative where it already has data
 * - override only fills gaps
 *
 * ## Barcode reassignment policy
 * All barcode rows currently pointing to the override food are reassigned to the canonical food.
 *
 * Package-specific override fields on the barcode rows are intentionally preserved.
 *
 * That means packaging metadata such as:
 * - override serving size
 * - override serving unit
 * - override servings per package
 * - override household serving text
 *
 * continue to live on the barcode row after reassignment.
 *
 * ### Collision handling
 * If the canonical food already owns a barcode that also exists on the override food,
 * the duplicate override barcode is skipped during reassignment to prevent UNIQUE
 * constraint violations.
 *
 * ## Post-merge state of the override food
 * The override food is not hard-deleted.
 *
 * It is preserved and marked as:
 * - `isDeleted = true`
 * - `deletedAtEpochMs = now`
 * - `mergedIntoFoodId = canonicalFoodId`
 * - `mergedAtEpochMs = now`
 *
 * This preserves traceability and prevents the app from losing history about what happened.
 *
 * ## Scope boundaries of this use case
 * This use case intentionally does **not**:
 * - rewrite historical logs
 * - merge recipe references
 * - reconcile user-facing display names/brands
 * - resolve conflicts beyond the basic nutrient copy policy
 * - do any UI prompting or merge preview generation
 *
 * It is a low-level domain/data operation whose job is to perform the merge safely.
 *
 * ## Expected callers
 * Typical callers include:
 * - future barcode/editor flows where a newly scanned package should map to an existing food
 * - duplicate cleanup tools
 * - admin/debug merge flows
 *
 * ## Failure behavior
 * This use case throws when the merge request is invalid or violates merge invariants.
 *
 * Callers should treat failures as non-partial:
 * - either the merge fully completed
 * - or nothing was committed
 *
 * ## Future evolution notes
 * Potential future extensions may include:
 * - merge preview / dry-run reporting
 * - user-visible conflict summaries
 * - more advanced nutrient conflict policies
 * - optional stable-id/reference migration in some non-log tables
 * - explicit repository transaction runner instead of DB direct injection
 *
 * For now, keep this class boring, explicit, and transactional.
 */
class MergeFoodsUseCase @Inject constructor(
    private val appDatabase: NutriDatabase,
    private val foodRepository: FoodRepository,
    private val foodNutrientDao: FoodNutrientDao,
    private val foodBarcodeDao: FoodBarcodeDao
) {

    suspend operator fun invoke(
        overrideFoodId: Long,
        canonicalFoodId: Long
    ) {
        mergeFoods(
            overrideFoodId = overrideFoodId,
            canonicalFoodId = canonicalFoodId
        )
    }

    suspend fun mergeFoods(
        overrideFoodId: Long,
        canonicalFoodId: Long
    ) {
        require(overrideFoodId > 0L) { "overrideFoodId must be > 0" }
        require(canonicalFoodId > 0L) { "canonicalFoodId must be > 0" }
        require(overrideFoodId != canonicalFoodId) {
            "Cannot merge a food into itself."
        }

        appDatabase.withTransaction {
            val overrideFood = foodRepository.getById(overrideFoodId)
                ?: error("Override food not found. id=$overrideFoodId")

            val canonicalFood = foodRepository.getById(canonicalFoodId)
                ?: error("Canonical food not found. id=$canonicalFoodId")

            validate(
                overrideFood = overrideFood,
                canonicalFood = canonicalFood,
                overrideFoodId = overrideFoodId,
                canonicalFoodId = canonicalFoodId
            )

            copyMissingNutrients(
                fromFoodId = overrideFoodId,
                toFoodId = canonicalFoodId
            )

            reassignBarcodesSafely(
                fromFoodId = overrideFoodId,
                toFoodId = canonicalFoodId
            )

            val now = System.currentTimeMillis()

            foodRepository.upsert(
                overrideFood.copy(
                    isDeleted = true,
                    deletedAtEpochMs = now,
                    mergedIntoFoodId = canonicalFoodId,
                    mergedAtEpochMs = now
                )
            )
        }
    }

    private fun validate(
        overrideFood: Food,
        canonicalFood: Food,
        overrideFoodId: Long,
        canonicalFoodId: Long
    ) {
        require(overrideFoodId != canonicalFoodId) {
            "Cannot merge a food into itself."
        }

        check(overrideFood.mergedIntoFoodId == null) {
            "Override food is already merged. id=$overrideFoodId mergedIntoFoodId=${overrideFood.mergedIntoFoodId}"
        }

        check(canonicalFood.mergedIntoFoodId == null) {
            "Canonical food is already merged and cannot be a merge target. id=$canonicalFoodId mergedIntoFoodId=${canonicalFood.mergedIntoFoodId}"
        }

        check(!canonicalFood.isDeleted) {
            "Canonical food cannot be soft-deleted. id=$canonicalFoodId"
        }
    }

    private suspend fun copyMissingNutrients(
        fromFoodId: Long,
        toFoodId: Long
    ) {
        val fromRows = foodNutrientDao.getForFood(fromFoodId)
        if (fromRows.isEmpty()) return

        val existingToRows = foodNutrientDao.getForFood(toFoodId)
        val canonicalNutrientIds = existingToRows
            .asSequence()
            .map { it.nutrientId }
            .toSet()

        val rowsToCopy: List<FoodNutrientEntity> = fromRows
            .filter { row -> row.nutrientId !in canonicalNutrientIds }
            .map { row -> row.copy(foodId = toFoodId) }

        if (rowsToCopy.isNotEmpty()) {
            foodNutrientDao.upsertAll(rowsToCopy)
        }
    }

    private suspend fun reassignBarcodesSafely(
        fromFoodId: Long,
        toFoodId: Long
    ) {
        val overrideRows = foodBarcodeDao.getAllForFood(fromFoodId)
        val canonicalRows = foodBarcodeDao.getAllForFood(toFoodId)

        val canonicalBarcodes = canonicalRows.map { it.barcode }.toSet()

        overrideRows.forEach { row ->
            if (row.barcode !in canonicalBarcodes) {
                foodBarcodeDao.upsert(
                    row.copy(foodId = toFoodId)
                )
            }
        }
    }
}