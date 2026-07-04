# AdobongKangkong Plan: Recipe Measured Yield Instead of Batch-Style Recipe Logging

## Summary

AdobongKangkong should keep cooked-batch logging shelved for now and move toward a lighter **measured recipe yield** model.

The new direction:

- Recipes and recipe variants can always be logged by **servings**.
- Recipes and recipe variants can be logged by **grams** only after the user enters a measured cooked yield.
- The app uses the measured yield to convert grams eaten into a fraction of the recipe.
- Ingredient changes and one-off recipe changes should be handled by **recipe variants**, not cooked batches.
- Old logs must remain immutable by freezing the measured yield assumption used at log time.

This gives the app a clear separation of responsibilities:

```text
Recipe variant = ingredient / nutrition mutation
Measured yield = gram-scaling assumption
Cooked batch = shelved legacy/internal concept
```

---

## Problem Being Solved

The app needs to support this user scenario:

> “I cooked this recipe, weighed the final yield, and now I want to log portions by grams.”

For example:

```text
Recipe: Adobo
Recipe serving yield: 6 servings
Measured cooked yield: 1,200 g

User logs: 200 g
Fraction eaten: 200 / 1,200 = 1/6 recipe
Nutrients logged: 1 recipe serving
```

Without a measured cooked yield, the app cannot safely convert grams into recipe nutrition, because the final cooked weight depends on water loss, cooking method, trimming, evaporation, and other real-world factors.

---

## Why Not Revive Batch-Style Recipe Logging Now?

The older cooked-batch idea was doing too many jobs at once.

It tried to represent:

1. A real cooked batch with final yield.
2. Ingredient substitutions or ingredient quantity changes.
3. Portion logging by grams.
4. Potential future inventory/lifetime tracking.
5. Historical nutrition preservation.

Now that recipe variants exist, the ingredient-change part is better handled elsewhere.

### Recipe Variants Already Cover Recipe “Mutations”

Examples of recipe mutations:

- More onion than usual.
- Less pork than usual.
- Regular milk instead of low-fat milk.
- Removing sugar.
- Adding extra vegetables.
- Making a low-sodium version.

These are not yield problems. They are recipe-definition problems.

Recipe variants are a better fit because they can explicitly say:

```text
Base recipe: Adobo
Variant: Low sodium Adobo
Variant: Extra onion Adobo
Variant: No sugar Adobo
```

Each variant has its own nutrition calculation and can be logged independently.

### Measured Yield Covers the Remaining Problem

After variants cover ingredient changes, the remaining question is simply:

> “How much does the finished recipe weigh?”

That does not require a full batch system.

It only requires a stored measured yield that says:

```text
For this recipe or variant, use this cooked yield for gram-based logging until the user updates it.
```

The app does not need to know:

- Whether the physical batch is still in the fridge.
- How many grams remain.
- Whether the user finished the batch.
- Whether the batch should expire.
- Whether this exact pot of food is still active.

Those are inventory concerns, and they are intentionally out of scope for now.

---

## Core Rule

```text
Food by serving        -> allowed
Food by grams          -> allowed

Recipe by serving      -> allowed
Recipe by grams        -> allowed only if measured yield exists

Variant by serving     -> allowed
Variant by grams       -> allowed only if measured yield exists

Cooked batch path      -> shelved / legacy / internal for now
```

The user-facing explanation should be simple:

```text
Recipes can always be logged by serving. To log a recipe by grams, enter a measured cooked yield first. The app uses that yield to convert grams into a recipe fraction.
```

Blocked gram-logging message:

```text
Recipes can be logged by servings for now. To log this recipe by grams, enter a measured cooked yield first.
```

Optional longer note:

```text
Measured yield is needed because the final cooked weight is unknown until the recipe is cooked and weighed.
```

---

## Data Model Proposal

Create a lightweight measured-yield table.

Suggested entity:

```kotlin
/**
 * Stores a measured cooked yield for a recipe or recipe variant.
 *
 * This is not full cooked-batch tracking. It does not represent inventory,
 * remaining quantity, expiration, or whether a physical batch is still available.
 *
 * The latest active measured yield is used only to convert gram-based recipe logs
 * into a fraction of the recipe or variant at log time.
 *
 * Historical logs must freeze the yield value used so later yield changes do not
 * alter old nutrition snapshots.
 */
@Entity(
    tableName = "recipe_measured_yields",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipeVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("recipeId"),
        Index("variantId"),
        Index(value = ["recipeId", "variantId"])
    ]
)
data class RecipeMeasuredYieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val recipeId: Long,

    /**
     * Null means this yield belongs to the base recipe.
     * Non-null means this yield belongs to a specific recipe variant.
     */
    val variantId: Long? = null,

    /**
     * Final cooked yield in grams.
     */
    val yieldGrams: Double,

    /**
     * When the user measured or entered this yield.
     */
    val measuredAtEpochMs: Long,

    /**
     * Optional user note, such as "air fryer batch", "large pot", or "less water".
     */
    val note: String? = null,

    /**
     * If using active-row semantics, only one active row should exist for a given
     * recipeId + variantId pair.
     *
     * Simpler alternative: omit this field and let latest measuredAtEpochMs win.
     */
    val isActive: Boolean = true
)
```

### Recommended Keying

The measured yield should belong to the exact recipe form:

```text
recipeId + variantId nullable
```

Examples:

```text
Base Adobo yield:           recipeId=10, variantId=null, yield=1,200 g
Low Sodium Adobo yield:     recipeId=10, variantId=5,    yield=1,100 g
Extra Onion Adobo yield:    recipeId=10, variantId=6,    yield=1,350 g
```

This prevents the app from incorrectly using the base recipe yield for a variant whose ingredient amounts changed.

---

## Latest Yield vs Active Yield

There are two reasonable approaches.

### Option A: Latest Measured Yield Wins

The app uses the newest row for the recipe or variant.

Pros:

- Simple.
- No active-state maintenance.
- Easy to understand.

Cons:

- Harder to preserve older yield records as inactive if the user wants to keep history but temporarily revert.

### Option B: One Active Yield Per Recipe Form

The app stores history but marks one row active.

Pros:

- Clear current assumption.
- Allows yield history.
- Allows future “restore previous yield” behavior.

Cons:

- Slightly more DAO logic.
- Need to deactivate old active rows before activating a new one.

Recommended for now:

```text
Use active-row semantics if implementation effort is acceptable.
Otherwise, latest measured yield wins is fine for MVP.
```

---

## DAO Behavior

Suggested DAO operations:

```kotlin
@Dao
interface RecipeMeasuredYieldDao {

    @Query(
        """
        SELECT * FROM recipe_measured_yields
        WHERE recipeId = :recipeId
          AND (
              (:variantId IS NULL AND variantId IS NULL)
              OR variantId = :variantId
          )
          AND isActive = 1
        ORDER BY measuredAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getActiveYield(
        recipeId: Long,
        variantId: Long?
    ): RecipeMeasuredYieldEntity?

    @Query(
        """
        SELECT * FROM recipe_measured_yields
        WHERE recipeId = :recipeId
          AND (
              (:variantId IS NULL AND variantId IS NULL)
              OR variantId = :variantId
          )
          AND isActive = 1
        ORDER BY measuredAtEpochMs DESC
        LIMIT 1
        """
    )
    fun observeActiveYield(
        recipeId: Long,
        variantId: Long?
    ): Flow<RecipeMeasuredYieldEntity?>

    @Query(
        """
        UPDATE recipe_measured_yields
        SET isActive = 0
        WHERE recipeId = :recipeId
          AND (
              (:variantId IS NULL AND variantId IS NULL)
              OR variantId = :variantId
          )
        """
    )
    suspend fun deactivateYieldsForRecipeForm(
        recipeId: Long,
        variantId: Long?
    )

    @Insert
    suspend fun insert(entity: RecipeMeasuredYieldEntity): Long
}
```

Recommended repository/use case behavior:

```text
SetMeasuredRecipeYieldUseCase:
1. Validate yieldGrams > 0.
2. Deactivate existing active yield for recipeId + variantId.
3. Insert new active yield.
```

This should be done in a Room transaction.

---

## Logging Math

For gram-based recipe logging:

```text
fractionOfRecipe = gramsLogged / measuredYieldGrams
servingsEquivalent = fractionOfRecipe * recipeServingsYield
```

Example:

```text
Recipe yield: 6 servings
Measured cooked yield: 1,200 g
Logged amount: 300 g

fractionOfRecipe = 300 / 1,200 = 0.25
servingsEquivalent = 0.25 * 6 = 1.5 servings
```

The app can then reuse the existing recipe/variant nutrient calculation by passing the equivalent serving amount.

---

## Log Snapshot Requirements

Old logs must stay historically correct.

When logging by grams using measured yield, freeze these values into the log entry or log metadata:

```text
recipeId
recipeVariantId
measuredYieldIdUsed
measuredYieldGramsUsed
gramsLogged
servingsEquivalent
nutrientSnapshotJson
```

The most important frozen fields are:

```text
measuredYieldGramsUsed
gramsLogged
servingsEquivalent
nutrientSnapshotJson
```

If the user later changes the measured yield from 1,200 g to 1,050 g, old logs must not change.

---

## UI Plan

### Recipe Detail Screen

Add a small section:

```text
Measured cooked yield
Current: 1,200 g
Last updated: Jun 25, 2026

[Update measured yield]
```

If no yield exists:

```text
Measured cooked yield
Not set

Enter a measured yield to log this recipe by grams.
[Set measured yield]
```

For variants, show the measured yield on the variant detail/editor screen, or in the recipe variant selector area.

### Quick Add

When selected item is a recipe or recipe variant:

- Servings mode remains available.
- Grams mode is available only if measured yield exists.
- If the user chooses grams without measured yield, show a clear note and offer a path to set yield.

Suggested note:

```text
Recipe gram logging needs a measured cooked yield. Log by serving, or enter a measured yield for this recipe.
```

For MVP, it is acceptable to block and show a message instead of opening an inline yield editor.

### Planner Whole-Meal Logging

When logging a planned meal:

- Planned food items by grams or servings work as before.
- Planned recipe items by servings should log normally.
- Planned recipe items by grams should:
  - log if measured yield exists
  - otherwise return a blocked item outcome with a clear message

Suggested blocked message:

```text
Recipe gram logging needs a measured cooked yield.
```

The whole-meal result should not say the entire meal failed if some items logged. It can say:

```text
Logged 3 items • Blocked 1
```

Longer future UI could show item-level details.

---

## Domain Rules

### Allowed

```text
Recipe by serving
Recipe variant by serving
Recipe by grams with measured yield
Recipe variant by grams with measured yield
```

### Blocked

```text
Recipe by grams without measured yield
Recipe variant by grams without measured yield
```

### Shelved

```text
Cooked batch creation
Cooked batch selection
Batch inventory
Batch remaining amount
Batch expiration
Batch lifetime tracking
```

---

## Relationship to Cooked Batches

Cooked batch code may remain in the codebase for now, but active user flows should not promote it.

Future-dev note:

```text
Cooked batches are intentionally shelved. Do not route users through batch creation for now.
Recipe variants handle ingredient changes. Measured recipe yield handles gram scaling.
Only revive cooked batches if the app later needs true batch inventory/lifetime tracking.
```

The measured-yield path should replace batch-style recipe gram logging for the current roadmap.

---

## Migration / Implementation Steps

### Phase 1: Fix Current Whole-Meal Recipe Logging

1. Update planned-meal logging so plain recipes by servings are allowed.
2. Keep recipe by grams blocked for now.
3. Replace any batch-oriented user message with measured-yield language.

Current behavior should become:

```text
Recipe planned by servings -> logs
Recipe planned by grams    -> blocked with measured-yield explanation
```

### Phase 2: Add Measured Yield Storage

1. Add `RecipeMeasuredYieldEntity`.
2. Add DAO.
3. Add repository/use case.
4. Add migration.
5. Add basic unit tests for:
   - insert active yield
   - replace active yield
   - base recipe yield lookup
   - variant yield lookup

### Phase 3: Enable Recipe Gram Logging

1. In Quick Add, when recipe is logged by grams:
   - lookup measured yield for recipeId + variantId
   - if missing, block with message
   - if present, convert grams to servings equivalent
2. Pass equivalent serving amount to canonical recipe logging.
3. Freeze measured-yield values in the resulting log snapshot/metadata.

### Phase 4: Enable Planned Meal Gram Recipe Logging

1. In `LogPlannedMealUseCase`, when planned recipe uses grams:
   - lookup measured yield
   - convert grams to servings equivalent
   - log recipe normally using serving-equivalent amount
2. For variants, use variant-specific measured yield.
3. Return item-level blocked outcome if measured yield is missing.

### Phase 5: UI Polish

1. Add measured yield section to recipe detail.
2. Add measured yield section to variant editor/detail.
3. Add Quick Add note when recipe grams are unavailable.
4. Add item-level feedback for whole-meal logging if only some items logged.

---

## Testing Plan

### Unit Tests

Test measured-yield lookup:

```text
Base recipe gets base yield.
Variant gets variant yield.
Variant does not accidentally use base yield.
Missing yield returns null.
Updating yield replaces active yield.
```

Test logging conversion:

```text
1,200 g yield + 300 g logged + 6 serving recipe = 1.5 servings.
1,000 g yield + 100 g logged + 4 serving recipe = 0.4 servings.
Zero or negative yield is rejected.
```

Test whole-meal logging:

```text
Food item logs.
Recipe serving item logs.
Recipe gram item blocks when yield missing.
Recipe gram item logs when yield exists.
Variant gram item requires variant yield.
Partial meal result reports logged and blocked counts correctly.
```

### Regression Tests

Confirm old rules remain stable:

```text
Food logs still use normal food serving/gram rules.
Recipe serving logs still produce immutable nutrition snapshots.
Existing recipe variant serving logs still use variant nutrition.
Changing measured yield later does not alter old logs.
Cooked batch UI remains hidden/shelved.
```

---

## Open Questions

1. Should the measured yield editor live on:
   - recipe detail screen only,
   - recipe variant editor only,
   - Quick Add inline,
   - or all of the above eventually?

2. Should latest measured yield win, or should the app maintain an explicit active yield?

3. Should measured yield history be visible to users?

4. Should planned recipes by grams be allowed in the planner before yield exists, then blocked only at logging time?

5. Should the app allow a fallback from variant yield to base recipe yield?
   - Current recommendation: no, because variants can materially change cooked yield.

---

## Recommended Decision

Adopt **measured recipe yield** as the active path.

Do not revive cooked-batch logging unless the app later needs true inventory tracking.

Final direction:

```text
Recipe variants handle ingredient changes.
Measured recipe yield handles gram scaling.
Cooked batches remain shelved.
```
