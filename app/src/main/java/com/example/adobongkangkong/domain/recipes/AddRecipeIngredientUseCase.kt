package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import com.example.adobongkangkong.domain.usage.FoodUsageCheck
import com.example.adobongkangkong.domain.usage.ServingAmountConverter
import com.example.adobongkangkong.domain.usage.UsageContext

/**
 * Adds (or updates) a single ingredient line for a recipe, expressed in servings and optionally grams.
 *
 * Purpose
 * - Persist a recipe ingredient entry using the app’s canonical “servings-first” representation.
 *
 * Rationale (why this use case exists)
 * - Recipe ingredient storage is normalized around servings so nutrition computation can rely on a consistent unit
 *   even when the user enters grams.
 * - Some serving units (especially volume-ish units) cannot be safely converted from grams without a food-specific
 *   grams-per-serving bridge; this use case centralizes that gate so UI/callers don’t reimplement it.
 *
 * Behavior
 * - Reads a minimal [FoodSnapshot] via [FoodLookupForRecipe] to determine serving unit + grams bridge.
 * - Applies a usage gate via [CheckFoodUsableUseCase] using [UsageContext.RECIPE].
 *   - If blocked, returns [Result.Blocked] with a user-facing message and performs no write.
 * - Normalizes the user input to servings:
 *   - [AmountInput.ByServings] → use servings directly.
 *   - [AmountInput.ByGrams] → convert grams -> servings using [ServingAmountConverter.gramsToServings].
 *     - If conversion fails (typically missing gramsPerServingUnit), returns [Result.Blocked].
 * - Persists via [RecipeIngredientWriter.upsert] with:
 *   - `amountServings` always populated.
 *   - `amountGrams` populated only when the user entered grams.
 *
 * Parameters
 * - recipeId: Target recipe id to receive the ingredient line.
 * - ingredientFoodId: Food id of the ingredient being added/updated.
 * - amountInput: User-entered amount (servings or grams).
 *
 * Return
 * - [Result.Success] when the ingredient is successfully upserted.
 * - [Result.Blocked] when business rules prevent adding the ingredient (e.g., missing grams bridge for grams input).
 * - [Result.Error] when required upstream data is missing (e.g., ingredient food not found).
 *
 * Edge cases
 * - Missing ingredient food snapshot → [Result.Error] ("Ingredient food not found").
 * - Grams entered for a unit requiring gramsPerServingUnit when it is null → [Result.Blocked] (no write).
 * - Negative/zero amounts: not validated here; callers should constrain inputs. If passed through, it will persist.
 *
 * Pitfalls / gotchas
 * - Blocking messages are user-facing; keep wording stable to avoid breaking UI expectations/tests.
 * - Do not attempt to “infer” grams-per-serving (e.g., water-equivalent) here; that must remain an explicit user step.
 * - This use case intentionally keeps conversion and gating local and explicit; avoid refactors that alter ordering or
 *   short-circuit behavior.
 *
 * Architectural rules (if applicable)
 * - Domain-layer orchestration only: no UI concerns, no navigation, and no direct DB access.
 * - Persistence must occur only through [RecipeIngredientWriter].
 * - Uses snapshot-style lookup ([FoodLookupForRecipe]) and must not rejoin full mutable food models.
 * - Logging model note: ISO-date-based logging uses `logDateIso` as authoritative; this use case does not write logs,
 *   but must remain compatible with snapshot immutability (no “rejoin foods” patterns).
 */
class AddRecipeIngredientUseCase(
    private val foodLookup: FoodLookupForRecipe,
    private val writer: RecipeIngredientWriter,
    private val checkFoodUsable: CheckFoodUsableUseCase = CheckFoodUsableUseCase()
) {

    sealed interface Result {
        data object Success : Result
        data class Blocked(val message: String) : Result
        data class Error(val message: String) : Result
    }

    suspend fun execute(
        recipeId: Long,
        ingredientFoodId: Long,
        amountInput: AmountInput
    ): Result {
        val food = foodLookup.getFoodById(ingredientFoodId)
            ?: return Result.Error("Ingredient food not found")

        // Gate: servings-based usage for volume-ish units missing gramsPerServingUnit is blocked.
        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServingUnit = food.gramsPerServingUnit,
            amountInput = amountInput,
            context = UsageContext.RECIPE
        )) {
            FoodUsageCheck.Ok -> Unit
            is FoodUsageCheck.Blocked -> return Result.Blocked(check.message)
        }

        when (amountInput) {
            is AmountInput.ByServings -> amountInput.servings
            is AmountInput.ByGrams -> {
                val r = ServingAmountConverter.gramsToServings(
                    servingUnit = food.servingUnit,
                    gramsPerServingUnit = food.gramsPerServingUnit,
                    grams = amountInput.grams
                )
                r.getOrElse {
                    return Result.Blocked("Set grams-per-serving before adding grams for this ingredient.")
                }
            }
        }

        writer.upsert(
            RecipeIngredientEntity(
                recipeId = recipeId,
                foodId = ingredientFoodId,
                amountServings = when (amountInput) {
                    is AmountInput.ByServings -> amountInput.servings
                    is AmountInput.ByGrams -> {
                        val r = ServingAmountConverter.gramsToServings(
                            servingUnit = food.servingUnit,
                            gramsPerServingUnit = food.gramsPerServingUnit,
                            grams = amountInput.grams
                        )
                        r.getOrElse {
                            return Result.Blocked("Set grams-per-serving before adding grams for this ingredient.")
                        }
                    }
                },
                amountGrams = when (amountInput) {
                    is AmountInput.ByServings -> null
                    is AmountInput.ByGrams -> amountInput.grams
                }
            )
        )
        return Result.Success
    }
}

/**
 * Writes recipe ingredient rows to the persistence layer.
 *
 * Purpose
 * - Provide a domain-facing write abstraction for recipe ingredients.
 *
 * Rationale (why this exists)
 * - The domain layer must not depend on Room/DAO APIs directly. This interface allows:
 *   - swapping implementations (Room, network sync, test fakes),
 *   - keeping use cases pure orchestration,
 *   - and constraining write behavior to a single method contract.
 *
 * Behavior
 * - Implementations must upsert the provided [RecipeIngredientEntity] as a single logical operation:
 *   - insert if missing,
 *   - update if present (based on the underlying uniqueness constraints, typically recipeId + foodId).
 *
 * Parameters
 * - entity: The ingredient row to insert/update.
 *
 * Return
 * - Unit. Errors should be thrown by the implementation and handled by the caller/use case boundary if needed.
 *
 * Edge cases
 * - Duplicate keys: should result in an update semantics, not a second row.
 * - Partial writes: implementations should be atomic (transactional if backed by a database).
 *
 * Pitfalls / gotchas
 * - Do not add additional parameters (e.g., “position”, “notes”) here unless they are part of the persisted entity model.
 * - Do not introduce UI-facing validation in writers; keep writers focused on persistence.
 *
 * Architectural rules
 * - Domain interface only; implementations live in data layer.
 * - No Android framework types in the signature.
 */
interface RecipeIngredientWriter {
    suspend fun upsert(entity: RecipeIngredientEntity)
}

/**
 * Minimal lookup surface needed by recipe-domain use cases for ingredient operations.
 *
 * Purpose
 * - Provide the smallest set of food fields required to:
 *   - apply usage gating,
 *   - and convert user-entered amounts into canonical servings.
 *
 * Rationale (why this exists)
 * - Recipe ingredient operations must not rejoin full mutable Food graphs (foods + nutrients + barcodes, etc.).
 *   They only need serving metadata and bridge values.
 * - Keeping the contract minimal reduces IO cost and prevents accidental coupling to large data models.
 *
 * Behavior
 * - Returns a [FoodSnapshot] if the food exists; otherwise null.
 *
 * Parameters
 * - foodId: The id of the food to look up.
 *
 * Return
 * - [FoodSnapshot]? where null means “not found”.
 *
 * Edge cases
 * - If a food exists but is missing bridge data (e.g., gramsPerServingUnit null), that is still a valid snapshot;
 *   the caller (use case) decides whether an operation is blocked.
 *
 * Pitfalls / gotchas
 * - Do not expand this to return nutrients or other mutable fields; doing so invites “rejoin” behavior that breaks
 *   snapshot immutability assumptions elsewhere in the app (especially snapshot logs).
 *
 * Architectural rules
 * - Domain interface only; implementations live in data layer.
 * - Must remain compatible with the snapshot/immutability model (no implicit joins to mutable food state).
 */
interface FoodLookupForRecipe {
    suspend fun getFoodById(foodId: Long): FoodSnapshot?
}

/**
 * Minimal, stable food metadata needed for recipe ingredient conversions and gating.
 *
 * Purpose
 * - Carry only the fields needed to convert grams <-> servings for a specific food.
 *
 * Rationale (why this exists)
 * - Avoid pulling full Food models into recipe write flows.
 * - Make it explicit which food fields are required for safe conversions.
 *
 * Behavior
 * - [servingUnit] defines the unit basis for servings.
 * - [gramsPerServingUnit] provides the bridge required for grams-based entry when the serving unit is not inherently
 *   mass-based; it may be null for foods that have not been bridged by the user/importer.
 *
 * Parameters
 * - id: Food identifier.
 * - servingUnit: Serving unit enum used to interpret servings.
 * - gramsPerServingUnit: Optional grams-per-serving-unit bridge used for grams conversions.
 *
 * Return
 * - Data-only value object.
 *
 * Edge cases
 * - gramsPerServingUnit may be null; callers must decide whether an operation is blocked (do not “guess”).
 *
 * Pitfalls / gotchas
 * - Do not add mutable fields (name/brand/nutrients) here; this snapshot is intentionally minimal and stable.
 *
 * Architectural rules
 * - Pure domain model; must stay platform-agnostic (KMP-safe).
 */
data class FoodSnapshot(
    val id: Long,
    val servingUnit: com.example.adobongkangkong.domain.model.ServingUnit,
    val gramsPerServingUnit: Double?
)

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (what must not change)
 * - AddRecipeIngredientUseCase must:
 *   - Lookup → gate → normalize → write (in that order).
 *   - Not write anything when blocked.
 * - Ingredient persistence is servings-first:
 *   - `amountServings` is always populated.
 *   - `amountGrams` is only populated when the user input was grams.
 * - FoodSnapshot and FoodLookupForRecipe must remain minimal and must not rejoin full Food graphs.
 * - Snapshot logs are immutable and must not rejoin foods; do not introduce patterns here that require mutable joins.
 *
 * Do not refactor notes
 * - Avoid “DRY refactors” that change control flow or user-facing messages.
 * - Do not expand the helper interfaces/models beyond what recipe ingredient conversion/gating strictly needs.
 *
 * Architectural boundaries
 * - Domain-only abstractions:
 *   - Writers/lookups are interfaces; implementations belong in the data layer.
 * - No Android framework types, no Compose, no navigation, no DB/Room direct dependencies.
 *
 * Migration notes (KMP / time APIs if relevant)
 * - Keep these helpers platform-agnostic; do not introduce java.time or Android-only APIs here.
 *
 * Performance considerations
 * - Keep lookups lightweight (FoodSnapshot fields only).
 * - Avoid extra DB joins/queries inside the lookup implementation contract if not required.
 */