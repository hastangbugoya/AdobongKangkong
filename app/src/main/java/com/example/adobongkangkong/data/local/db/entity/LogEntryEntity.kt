package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for a single persisted "log entry" (a logged consumption event).
 *
 * ## Core concepts
 *
 * **1) Snapshot-at-log-time (immutability for history)**
 * - [nutrientsJson] stores the *final resolved nutrient totals* for this specific log event.
 * - We intentionally do NOT join against Foods / Recipes / Nutrient tables when displaying or
 *   totaling logs. This keeps history stable even if foods are edited/deleted later.
 *
 * **2) Day membership uses `logDateIso`, NOT timestamps**
 * - The day bucket is defined by [logDateIso] (ISO yyyy-MM-dd).
 * - All Day Log / per-day totals must filter by `logDateIso == selectedDateIso`.
 * - [timestamp] is kept for ordering and posterity only; it must not be used to decide which day
 *   an entry belongs to.
 *
 * **3) User intent is preserved**
 * - [amount] + [unit] preserve the user's input intent (e.g., grams vs servings vs item).
 * - Even though [nutrientsJson] is already “fully resolved totals”, we keep amount/unit because:
 *   - it provides auditability (what user typed),
 *   - it enables future “edit log entry” UX without reverse-deriving intent,
 *   - it supports future features (exports, history views, etc.).
 *
 * **4) Optional provenance / context**
 * - [foodStableId] is used to associate the log with a food identity that survives edits.
 * - [recipeBatchId] and [gramsPerServingCooked] capture cooked-yield context when logging recipe
 *   batches so the snapshot reflects the correct cooked yield.
 * - [mealSlot] optionally categorizes the log entry into a slot (breakfast/lunch/dinner/etc) for
 *   UI grouping and day planning.
 *
 * ## Agreed rules (hard requirements)
 * - **Day Log screen shows only entries where `logDateIso == selectedDateIso`.**
 * - Timestamp windows (start/end Instants) are NOT used for day membership.
 * - `logDateIso` is computed by the caller (based on the selected calendar day); do not derive it
 *   from [timestamp] during reads.
 *
 * ## Ordering and display
 * - When showing a single day: order by [timestamp] (typically DESC) for a natural “most recent”
 *   feed.
 * - When showing a range: order by [logDateIso] then [timestamp] (direction depends on screen).
 *
 * ## Edge cases / gotchas
 * - **Timezone:** Because day membership is [logDateIso]-based, timezone only matters at the moment
 *   [logDateIso] is chosen by the caller. Once persisted, reads must treat it as authoritative.
 * - **Manual edits / imports:** If an entry is inserted with a mismatched [timestamp] and
 *   [logDateIso], the UI must still trust [logDateIso] for day grouping.
 * - **Null [foodStableId]:** Allowed for logs that are not tied to a food identity (if you support
 *   truly custom/manual log rows). If you never create such rows, it can still remain nullable for
 *   flexibility.
 */
@Entity(
    tableName = "log_entries",
    indices = [
        // For timeline/feed ordering and any “recent logs” view.
        Index(value = ["timestamp"]),

        // Primary index for Day Log / per-day totals.
        Index(value = ["logDateIso"]),

        // For food usage queries / dependency checks / “where was this food used”.
        Index(value = ["foodStableId"]),

        // For batch-based logs and batch usage queries.
        Index(value = ["recipeBatchId"])
    ]
)
data class LogEntryEntity(
    /**
     * Primary key.
     *
     * Auto-generated. Used as the stable identity for deleting/editing a specific log entry.
     */
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    /**
     * 🔑 Cross-app stable identity (UUID).
     *
     * - Generated ONCE at insert
     * - Never changes
     * - Used for cross-app upsert (HH, future apps)
     */
    val stableId: String,

    /**
     * Created timestamp (first insertion time).
     *
     * Immutable.
     */
    val createdAt: Instant,

    /**
     * Last modification timestamp.
     *
     * Updated ONLY when meaningful fields change.
     * Used for cross-app change detection.
     */
    val modifiedAt: Instant,

    /**
     * Instant when the log entry was recorded.
     *
     * Used for ordering and posterity. MUST NOT be used for deciding which day bucket the entry
     * belongs to (see [logDateIso]).
     */
    val timestamp: Instant,

    /**
     * ISO day bucket for this log entry (yyyy-MM-dd).
     *
     * This is the authoritative day key for:
     * - Day Log display
     * - per-day totals
     * - calendar day grouping
     *
     * Reads must filter by equality on this column for a single day.
     */
    val logDateIso: String,

    /**
     * Display name captured at log time.
     *
     * Stored redundantly so history stays readable even if the underlying food/recipe name changes
     * later.
     */
    val itemName: String,

    /**
     * Stable identifier for the logged food (or recipe-as-food) captured at log time.
     *
     * This supports usage queries and preserves identity across food edits. Nullable to allow
     * manual/custom logs if needed.
     */
    val foodStableId: String?,

    /**
     * User-entered quantity amount.
     *
     * This preserves the user's input intent even though [nutrientsJson] stores final totals.
     *
     * Examples:
     * - amount=100, unit=GRAM
     * - amount=1.5, unit=SERVING
     * - amount=1, unit=ITEM
     */
    val amount: Double = 1.0,

    /**
     * Unit associated with [amount].
     *
     * This is NOT used to compute totals at read time; totals are already resolved into
     * [nutrientsJson]. It exists for auditability and future “edit log” UX.
     */
    val unit: com.example.adobongkangkong.domain.model.LogUnit =
        com.example.adobongkangkong.domain.model.LogUnit.ITEM,

    /**
     * Optional recipe batch id when this log entry corresponds to a cooked batch context.
     *
     * Used to preserve which cooked yield context was applied when producing [nutrientsJson].
     */
    val recipeBatchId: Long? = null,

    /**
     * Optional cooked grams-per-serving used when logging a recipe batch.
     *
     * Captures the cooked yield scaling basis so the log event remains reproducible and auditable
     * even if batch/recipe metadata changes later.
     */
    val gramsPerServingCooked: Double? = null,

    /**
     * JSON-serialized immutable nutrient totals for this log event.
     *
     * This is the *final* totals after scaling by the user's input (grams/servings/etc).
     * The UI and summary calculations should use this snapshot rather than joining other tables.
     */
    val nutrientsJson: String,

    /**
     * Optional meal slot categorization (Breakfast/Lunch/Dinner/Snack/etc).
     *
     * Used for grouping and display within a day. Not required for correctness of totals.
     */
    val mealSlot: MealSlot? = null
)