# AdobongKangkong Wear OS Support Plan

**Document purpose:** Preserve the planned Wear OS support design for AdobongKangkong so it can be implemented later without losing the original scope and rationale.

**Project:** AdobongKangkong / AK  
**Feature area:** Wear OS companion support  
**Status:** Planned / deferred until a Wear OS test device is available  
**Core decision:** The Wear OS app should be a quick-action and glance surface. The phone remains the source of truth for nutrition math, food data, recipe variants, planner data, and logged-food snapshots.

---

## 1. Goal

The AK Wear OS support app should let the user perform small, high-frequency food logging actions from the watch without turning the watch into a full nutrition app.

The first useful AK Wear OS version should focus on:

1. Quick caffeine logging, matching the existing phone widget concept.
2. Viewing today’s planned meals.
3. Logging a planned meal from the watch.
4. Showing a simple today summary.

The watch should not perform complex food search, USDA import, recipe editing, serving-unit correction, or nutrition calculations.

---

## 2. Main Design Rule

The Wear OS app should reuse existing AK concepts and use cases. It should not create a separate watch-specific nutrition system.

Recommended mental model:

```text
Phone app = source of truth
Wear OS app = quick action / glance companion
```

The phone owns foods, recipes, recipe variants, nutrient values, serving conversions, planner meals, log snapshots, caffeine quick-log slot configuration, and day totals.

The watch only displays a small subset of this data and sends simple log commands back to the phone.

---

## 3. Rationale

### 3.1 AK is too complex to clone on a watch

AK has complex behaviors that are not watch-friendly:

- USDA search/import
- Barcode/generic food differences
- Missing nutrient warnings
- Recipe builder
- Recipe variants
- Serving-unit grounding
- Per-gram and per-mL nutrition math
- Planned meals and recurring series
- Immutable logged nutrient snapshots
- Store price data
- Food editor validation

These should remain on the phone.

### 3.2 Caffeine quick logging is watch-friendly

Caffeine logging is one of the best watch use cases because it is frequent, repetitive, and simple.

The phone widget idea already limits the user to three quick-log slots. Wear OS can reuse that same configuration.

Example:

```text
Coffee
Soda
Monster
```

User taps one item and AK logs one serving using the same logic as the phone.

### 3.3 Planned meals are already decided on the phone

The watch does not need a full planner. It only needs to answer:

```text
What did I plan to eat today?
Can I log it now?
```

That is a good watch interaction.

### 3.4 Nutrition math must stay centralized

AK’s core rule is that logs use immutable nutrient snapshots. The watch should not independently calculate nutrition.

The watch should request:

```text
Log this configured caffeine item.
Log this planned meal.
```

The phone should execute the normal use case and create the normal snapshot.

---

## 4. MVP Scope

The AK Wear OS MVP should include:

```text
1. Caffeine quick log screen
2. Today’s planned meals screen
3. Planned meal detail screen
4. Log planned meal action
5. Simple today totals
6. Basic haptic/visible confirmation
```

Not required for MVP:

```text
Food search
USDA import
Barcode scan
Recipe editing
Recipe variant editing
Planner editing
Recurring planner creation
Serving-unit correction
Store price features
Full Day Nutrients screen
Full dashboard recreation
```

---

## 5. Feature 1: Caffeine Quick Log

### 5.1 Goal

Allow the user to log one of the configured caffeine items from the watch.

This should mirror the phone caffeine widget concept.

### 5.2 Expected watch screen

```text
Caffeine

Coffee
Soda
Monster

Today: 180 mg
```

Each item is a large tap target.

After tap:

```text
Logged Coffee
+95 mg caffeine
```

Or if caffeine value is unknown:

```text
Logged Coffee
```

### 5.3 Data source

The watch should use the existing caffeine quick-log slot configuration from the phone app.

Possible model:

```kotlin
data class CaffeineQuickLogSlot(
    val slotIndex: Int,
    val foodId: Long?,
    val label: String,
    val servingAmount: Double
)
```

The watch should not create its own slot configuration.

### 5.4 Logging behavior

When the user taps a quick-log item:

```text
Wear OS tap
→ Send quick-log command to phone
→ Phone calls normal AK log use case
→ Phone creates normal LogEntry with frozen nutrient snapshot
→ Phone sends updated caffeine total back to watch
```

Conceptual command:

```kotlin
sealed interface AkWearCommand {
    data class LogCaffeineSlot(val slotIndex: Int) : AkWearCommand
}
```

### 5.5 Error states

The watch should show clear errors:

```text
No caffeine slots configured
Open AK on phone to choose quick-log foods.
```

```text
Phone not connected
Open AK on phone.
```

```text
Food missing nutrition
Logged, but nutrition may be incomplete.
```

The last message should follow existing AK warning logic and should not invent a new warning policy.

---

## 6. Feature 2: Today’s Planned Meals

### 6.1 Goal

Show meals planned for the current day and allow the user to log them from the watch.

### 6.2 Expected watch screen

```text
Today’s Plan

Breakfast
Oatmeal + Coffee
420 kcal

Lunch
Chicken Rice
650 kcal

Dinner
Tamarind Soup
520 kcal
```

The screen should be scrollable and simple.

### 6.3 Meal detail screen

When the user taps a planned meal:

```text
Lunch

Chicken Rice
650 kcal

P 45g / C 70g / F 18g

[Log meal]
```

If a planned meal contains multiple items, the watch can show a compact summary:

```text
Lunch

Chicken Rice
Banana
Coffee

850 kcal
P 52g / C 105g / F 20g

[Log meal]
```

### 6.4 Logging behavior

The watch should log the planned meal exactly as the phone would.

The phone should resolve:

- Food ID
- Recipe ID
- Recipe variant if already selected in the plan
- Serving amount
- Meal slot
- Snapshot nutrients
- Date

The watch should not recalculate this.

Conceptual command:

```kotlin
sealed interface AkWearCommand {
    data class LogPlannedMeal(val plannedMealId: Long) : AkWearCommand
}
```

### 6.5 Planner restrictions

The watch should not edit the plan.

Allowed:

```text
View today’s planned meals
View planned meal summary
Log planned meal
```

Not allowed in MVP:

```text
Create planned meal
Edit planned meal
Delete planned meal
Move meal to another slot
Change serving amount
Change recipe variant
Create recurring meal
```

Those remain phone features.

---

## 7. Feature 3: Today Summary

### 7.1 Goal

Show a simple glanceable summary for the day.

Example:

```text
Today

1650 / 2100 kcal
Protein 132 / 160g
Caffeine 180 mg
```

This can be a screen in the Wear OS app and later become a tile or complication.

### 7.2 Recommended MVP metrics

Start with:

- Calories logged today
- Protein logged today
- Caffeine total today

Optional later:

- Carbs
- Fat
- Water if AK tracks it later
- Remaining calories
- Planned vs logged status

---

## 8. Possible Watch Navigation

Recommended simple navigation:

```text
AK Wear OS Home
├── Caffeine
├── Today’s Plan
└── Today Summary
```

Or a single scroll page:

```text
Today Summary
Caffeine Quick Log
Today’s Planned Meals
```

For MVP, a single scroll page may be simpler.

Example:

```text
AdobongKangkong

Today
1650 / 2100 kcal
Protein 132 / 160g

Caffeine
[Coffee]
[Soda]
[Monster]

Plan
Breakfast - 420 kcal
Lunch - 650 kcal
Dinner - 520 kcal
```

---

## 9. Communication Between Phone and Watch

Recommended MVP approach:

```text
Phone required
Watch connected to phone
Phone owns database and use cases
Watch sends commands
Phone returns updated display state
```

The Wear OS app can use the Wear OS Data Layer APIs to exchange messages and state.

Conceptual flow:

```text
Watch opens AK
→ Watch requests current AK Wear state
→ Phone sends caffeine slots, today plan, today summary
→ User taps caffeine or planned meal log
→ Watch sends command
→ Phone performs normal AK log operation
→ Phone sends updated state/result
→ Watch shows confirmation
```

---

## 10. Suggested Display State Model

The phone should send a compact display-ready state to the watch.

Example:

```kotlin
data class AkWearDisplayState(
    val dateEpochDay: Long,
    val todayCalories: Double?,
    val calorieGoal: Double?,
    val todayProteinGrams: Double?,
    val proteinGoalGrams: Double?,
    val todayCaffeineMg: Double?,
    val caffeineSlots: List<AkWearCaffeineSlot>,
    val plannedMeals: List<AkWearPlannedMealSummary>,
    val lastMessage: String?
)

data class AkWearCaffeineSlot(
    val slotIndex: Int,
    val label: String,
    val isConfigured: Boolean
)

data class AkWearPlannedMealSummary(
    val plannedMealId: Long,
    val mealSlotLabel: String,
    val title: String,
    val calories: Double?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val fatGrams: Double?,
    val isLogged: Boolean
)
```

The watch should not need full FoodEntity, RecipeEntity, FoodNutrientEntity, or LogEntry objects.

---

## 11. Suggested Command Model

Example:

```kotlin
sealed interface AkWearCommand {
    data class LogCaffeineSlot(
        val slotIndex: Int
    ) : AkWearCommand

    data class LogPlannedMeal(
        val plannedMealId: Long
    ) : AkWearCommand

    data object RefreshToday : AkWearCommand
}
```

Phone response:

```kotlin
sealed interface AkWearCommandResult {
    data class Success(
        val message: String,
        val updatedState: AkWearDisplayState
    ) : AkWearCommandResult

    data class Failure(
        val message: String,
        val updatedState: AkWearDisplayState?
    ) : AkWearCommandResult
}
```

---

## 12. Implementation Plan

### Step 1: Define AK Wear OS MVP scope in the phone project

Add a project note and possibly a feature flag.

Suggested feature flag:

```text
wear_os_support_enabled = false
```

This prevents half-built UI from leaking into normal app behavior.

### Step 2: Extract caffeine quick-log use case if needed

Make sure the phone widget and future Wear OS code can call the same logic.

The ideal shape:

```text
LogCaffeineQuickSlotUseCase(slotIndex, date)
```

This use case should resolve the configured food and call the normal logging path.

### Step 3: Create a display-state mapper

Create a mapper that converts existing AK data into a compact watch display model.

Inputs:

- Today’s logs
- Caffeine quick-log slot config
- Today’s planned meals
- User nutrition goals if available

Output:

```text
AkWearDisplayState
```

### Step 4: Create phone-side command handler

Create a phone-side handler for Wear OS commands.

It should support:

```text
RefreshToday
LogCaffeineSlot
LogPlannedMeal
```

This handler should call existing AK use cases.

### Step 5: Add Wear OS module

Later, when a Wear OS test watch is available, add a Wear OS app module.

MVP module contents:

```text
One Activity
Compose for Wear OS
AK home screen
Caffeine quick-log section
Today plan section
Today summary section
Message/connection state
```

### Step 6: Implement phone-watch Data Layer connection

Start with simple request/response.

No offline logging in MVP.

### Step 7: Add haptics and confirmation

Use haptics for:

- Log success
- Log failure
- Phone disconnected
- Button tap if desired

### Step 8: Add regression tests

Focus on command handling and snapshot correctness.

---

## 13. Testing Plan

### 13.1 Phone-side unit tests

Test:

- Caffeine slot logs the correct food.
- Caffeine slot creates normal nutrient snapshot.
- Planned meal logs correct foods.
- Planned meal uses correct serving amounts.
- Planned meal uses selected recipe variant if present.
- Missing macro warning behavior is preserved.
- Logged nutrition matches phone logging behavior.

### 13.2 Display-state tests

Test:

- Today calories/protein/caffeine render correctly.
- Empty caffeine slots show as unconfigured.
- No planned meals gives an empty plan message.
- Already logged planned meal shows logged state.
- Recipe planned meal summary is correct.
- Recipe variant planned meal summary is correct if supported.

### 13.3 Watch UI tests

Test:

- Caffeine buttons are large enough.
- Planned meals are readable.
- Long food/recipe names do not break layout.
- Error messages fit on small screens.
- Round and square watches render acceptably.

### 13.4 Real-device tests

Test:

- Watch logs caffeine while phone is nearby.
- Watch logs planned meal while phone is locked.
- Watch updates after phone log changes.
- Watch handles disconnected phone.
- Watch does not duplicate log on repeated tap unless user intentionally taps again.
- Haptic confirmation is noticeable but not annoying.

---

## 14. Important Constraints

### 14.1 No independent nutrition math on watch

The watch should not calculate per-gram/per-mL nutrition.

### 14.2 No independent snapshots on watch

The watch should not create its own nutrient snapshot structure.

### 14.3 No food search on watch for MVP

Food search is too complex for the first Wear OS version.

### 14.4 No planner editing on watch for MVP

Planner creation/editing stays on the phone.

### 14.5 No offline mode for MVP

Offline watch logging should be considered only after the phone-connected version is stable.

---

## 15. Future Enhancements

### 15.1 Wear OS tile

A tile could show:

```text
AK Today
1650 / 2100 kcal
Protein 132g
Caffeine 180mg
```

Tap opens the AK Wear app.

### 15.2 Caffeine complication

Possible complication:

```text
180 mg caffeine
```

or

```text
Coffee logged
```

### 15.3 Planned meal complication

Possible complication:

```text
Next meal: Lunch
650 kcal
```

### 15.4 More quick-log slots

Only consider if the 3-slot caffeine model proves too limited.

### 15.5 Offline queue

Possible later, but requires careful duplicate prevention and snapshot handling.

---

## 16. Final Design Summary

AK Wear OS should be a quick-action companion, not a full nutrition app.

The first version should support:

```text
Caffeine quick logging
Today planned meal viewing
Planned meal logging
Simple today totals
```

The phone remains the source of truth for all nutrition logic.

Recommended architecture:

```text
Wear OS tap
    ↓
AkWearCommand
    ↓
Phone command handler
    ↓
Existing AK use case
    ↓
Normal LogEntry with frozen nutrient snapshot
    ↓
Updated watch display state
```

This keeps AK consistent, safe, and useful on the watch without creating a second nutrition engine.
