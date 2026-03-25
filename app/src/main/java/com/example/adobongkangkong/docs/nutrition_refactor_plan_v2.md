# 🧩 AdobongKangkong --- Nutrition Editing Refactor Plan

## 🎯 Goal

Decouple **serving definition** from **nutrient editing** to: -
eliminate reactive UI bugs - improve user control and predictability -
centralize nutrition computation logic

------------------------------------------------------------------------

## 🧠 Core Concept

Two independent phases:

### 1. Serving Definition Phase

User edits: - serving amount - serving unit - grams per serving unit
(optional) - mL per serving unit (optional)

Action: ➡️ **Recompute Displayed Nutrients**

------------------------------------------------------------------------

### 2. Nutrient Editing Phase

User edits: - nutrient values (manual input)

Action: ➡️ **Apply Nutrient Edits**

------------------------------------------------------------------------

## 🔁 Data Flow Rules

### Recompute (Canonical → UI)

-   Input: canonical nutrients (PER_100G / PER_100ML)
-   Output: UI nutrient fields

### Apply Nutrients (UI → Canonical)

-   Input: UI nutrient fields
-   Output: canonical storage

🚫 These must NEVER be combined.

------------------------------------------------------------------------

## 🏗️ Implementation Phases

------------------------------------------------------------------------

### ✅ Phase 1 --- Serving Resolution Layer

Create: `ServingResolution`

Responsibilities: - resolve grams per serving - resolve mL per serving -
determine compatibility with basis - NO UI logic

Inputs: - servingSize - servingUnit - gramsPerServingUnit -
mlPerServingUnit

Outputs: - gramsPerServing: Double? - mlPerServing: Double? -
supportsPer100G: Boolean - supportsPer100ML: Boolean

------------------------------------------------------------------------

### ✅ Phase 2 --- Recompute Use Case

Create: `RecomputeNutrientsFromCanonicalUseCase`

Input: - canonical nutrients - ServingResolution

Output: - UI nutrient values

------------------------------------------------------------------------

### ✅ Phase 3 --- Apply Nutrients Use Case

Create: `ApplyEditedNutrientsUseCase`

Input: - edited UI nutrient values - ServingResolution

Output: - canonical nutrients (PER_100G or PER_100ML)

------------------------------------------------------------------------

### ✅ Phase 4 --- FoodEditorViewModel Changes

Add state:

``` kotlin
val isServingDirty: Boolean
val isNutrientsDirty: Boolean
```

Add events:

``` kotlin
onServingChanged(...)
onRecomputeClicked()
onNutrientChanged(...)
onApplyNutrientsClicked()
```

------------------------------------------------------------------------

### ✅ Phase 5 --- UI Changes

#### Serving Section

-   inputs (size, unit, bridges)
-   button: **Recompute Displayed Nutrients**

Show warning: - "Nutrient values are out of date"

------------------------------------------------------------------------

#### Nutrient Section

-   editable nutrient fields
-   button: **Apply Nutrient Edits**

Show warning: - "Nutrient edits not applied"

------------------------------------------------------------------------

### ⚠️ Phase 6 --- Conflict Handling

If: - nutrients dirty - user taps recompute

➡️ Show dialog: "Discard manual edits and recompute?"

------------------------------------------------------------------------

### ✅ Phase 7 --- Save Behavior

Save should: - persist serving fields - persist canonical nutrients

Block or warn if: - serving not recomputed - nutrients not applied

------------------------------------------------------------------------

## 🚫 Anti-Patterns to Avoid

-   ❌ Live recalculation on every keystroke
-   ❌ Mixing UI display math with storage math
-   ❌ Multiple scattered conversion logic
-   ❌ Implicit conversions without user awareness

------------------------------------------------------------------------

## 🧠 Future Extensions

-   Bridge confidence system integration
-   External app snapshot compatibility
-   Meal planner / recipe alignment
-   Nutrition audit/debug mode

------------------------------------------------------------------------

## 🏁 Summary

This refactor introduces: - deterministic computation pipeline -
explicit user actions - separation of concerns

Result: ✔ predictable UI\
✔ fewer bugs\
✔ scalable architecture


---

# 📎 Addendum — Robust Bulk-Math Revamp Strategy

## 🎯 Revamp Goal

This refactor is not just a UI cleanup. It is a **robustness revamp** intended to prevent future bug dominoes across:
- food editor
- food list previews
- quick add
- logging
- recipe ingredient handling
- future external snapshot/export consumers

### Core principle
Keep nutrition storage **normalized to PER_100G / PER_100ML**.

But instead of doing nutrition math:
- reactively
- screen-by-screen
- branch-by-branch
- piece-meal during typing

the app should do nutrition math in **bulk through one deterministic pipeline**.

Because users edit **one food at a time**, the editor can afford to be slower and stricter if that makes the math safer and more understandable.

### Desired outcome
- fewer hidden conversions
- fewer per-screen math branches
- fewer edge-case regressions
- less state desync
- easier debugging
- safer future extensibility

---

## 🧠 Architectural Direction

### Old pattern
- UI field changes trigger partial recalculations
- individual screens build their own conversion logic
- serving conversion rules are reinterpreted in multiple places
- display state and canonical math are too tightly coupled

### New pattern
- collect all serving definition inputs first
- resolve them once through a shared resolver
- perform full recompute/apply operations in one pass
- return a fully-resolved result object
- only then update UI/editor state

This means:
- **canonical math is centralized**
- **UI becomes a consumer of resolved results**
- **all edge/corner handling happens in one place**

---

## ✅ Robustness Rules

### Rule 1 — Canonical nutrition remains the source of truth
Stored nutrients remain normalized to:
- `PER_100G`
- `PER_100ML`

Do **not** move canonical truth to per-serving storage.

---

### Rule 2 — Serving definition is resolved before nutrient math
Never compute nutrient values directly from partial field checks like:
- `if unit == G`
- `if gramsPerServingUnit != null`
- `if mlPerServingUnit != null`

Instead:
1. gather serving inputs
2. resolve serving capabilities once
3. run nutrient math from the resolved output

---

### Rule 3 — Recompute and Apply are bulk operations
Do not mutate nutrient UI fields incrementally while the user types.

Use explicit actions:
- **Recompute Displayed Nutrients**
- **Apply Nutrient Edits**

Each action should process:
- all serving inputs
- basis compatibility
- all affected nutrient values
- warnings/errors
- result metadata

in one bulk pass.

---

### Rule 4 — Resolver decides capability, not screens
Only the shared resolver/use case should decide things like:
- can resolve grams
- can resolve mL
- compatible with PER_100G
- compatible with PER_100ML
- bridge source
- whether result is deterministic
- why computation is blocked

Screens should not reinvent these rules.

---

### Rule 5 — Prefer correctness over instant feedback
Because the user edits one food at a time, the editor may:
- pause before recompute
- validate in bulk
- show blocking warnings
- require explicit confirmation

That is acceptable and preferred over silent math drift.

---

### Rule 6 — Every conversion path must be explainable
For debugging and future maintainability, each recompute/apply result should be traceable:
- what basis was used
- what serving resolution was used
- whether grams or mL path was used
- whether bridge fields were used
- why a value could not be computed

---

## 🧱 Implementation Addendum — Step-by-Step Robust Refactor

## Step A — Introduce ServingResolution as the only conversion gateway

### Goal
Create one shared domain object that fully resolves serving definition before any nutrient math happens.

### Responsibilities
- interpret `servingSize`
- interpret `servingUnit`
- use `ServingUnit.asG`
- use `ServingUnit.asMl`
- use `gramsPerServingUnit`
- use `mlPerServingUnit`
- determine exact grams/mL capability
- determine basis compatibility
- expose block reasons

### Suggested files involved
- `domain/model/ServingUnit.kt`
- **new** `domain/nutrition/ServingResolution.kt`
- **new** `domain/nutrition/ResolveServingDefinitionUseCase.kt`

### Pitfalls
- duplicating conversion rules in VM/UI anyway
- treating only `G` or `ML` as direct-convertible while ignoring other deterministic units
- allowing multiple slightly different “resolver” helpers to appear

---

## Step B — Add bulk recompute result model

### Goal
Recompute displayed nutrient fields from canonical nutrition in one pass.

### Responsibilities
- input canonical nutrients
- input serving resolution
- return a full result object for all nutrient UI fields
- return blocked/warning state if basis is incompatible
- avoid partial field updates

### Suggested files involved
- **new** `domain/nutrition/RecomputeDisplayedNutrientsUseCase.kt`
- **new** `domain/nutrition/RecomputeDisplayedNutrientsResult.kt`
- existing food editor nutrient UI mapping file(s)

### Pitfalls
- recomputing only the changed nutrient
- mixing string formatting with domain math
- partially refreshing some UI fields but not others

---

## Step C — Add bulk apply result model

### Goal
Convert manually edited nutrient UI values back into canonical normalized nutrient storage in one pass.

### Responsibilities
- input edited nutrient UI values
- input serving resolution
- input canonical basis
- validate compatibility
- generate a full canonical nutrient map/result
- return precise blocked/error states

### Suggested files involved
- **new** `domain/nutrition/ApplyEditedNutrientsUseCase.kt`
- **new** `domain/nutrition/ApplyEditedNutrientsResult.kt`
- existing nutrient save/update pipeline files

### Pitfalls
- saving per-serving values directly as canonical
- converting some nutrients but leaving stale values for others
- basis mismatch hidden behind fallback logic

---

## Step D — Refactor FoodEditorState into explicit tracks

### Goal
Stop treating serving edits and nutrient edits as one reactive blur.

### Add explicit editor state groups
#### Serving draft
- serving size
- serving unit
- grams per serving unit
- mL per serving unit

#### Nutrient draft
- editable nutrient text fields

#### Dirty / status flags
- `isServingDirty`
- `isNutrientsDirty`
- `hasPendingRecompute`
- `hasPendingApply`
- `showDiscardNutrientEditsDialog`
- `lastResolutionError` / `lastResolutionWarning`

### Suggested files involved
- `ui/food/editor/FoodEditorState.kt`
- `ui/food/editor/FoodEditorEvent.kt` or equivalent event model
- `ui/food/editor/FoodEditorViewModel.kt`

### Pitfalls
- hidden coupling where serving edits still auto-mutate nutrient fields
- forgetting to distinguish “dirty” from “applied”
- not tracking stale nutrient display after serving edits

---

## Step E — Refactor FoodEditorViewModel around bulk actions

### Goal
Move editor behavior from reactive auto-math to explicit domain actions.

### New/updated events
- `onServingSizeChanged`
- `onServingUnitChanged`
- `onGramsPerServingUnitChanged`
- `onMlPerServingUnitChanged`
- `onRecomputeDisplayedNutrientsClicked`
- `onNutrientChanged`
- `onApplyNutrientEditsClicked`

### ViewModel responsibilities
- maintain serving draft separately
- maintain nutrient draft separately
- call resolver before recompute/apply
- block or warn on invalid transitions
- protect against overwriting dirty nutrient edits

### Suggested files involved
- `ui/food/editor/FoodEditorViewModel.kt`
- any editor mapper/helper files currently doing live scaling

### Pitfalls
- leaving old live-scaling code paths in place
- calling recompute from every serving edit anyway
- saving unresolved drafts without warning

---

## Step F — Refactor FoodEditor UI to show explicit workflow

### Goal
Make the editor visually reflect the two-phase model.

### UI structure
#### Serving Definition section
- serving inputs
- recompute button
- stale warning / resolution message

#### Nutrient Values section
- nutrient inputs
- apply button
- unapplied-edit warning

### Save behavior cues
- save disabled or guarded if recompute/apply pending
- confirmation dialog when recompute would discard manual nutrient edits

### Suggested files involved
- `ui/food/editor/FoodEditorScreen.kt`
- supporting composables in the same feature package

### Pitfalls
- unclear wording that hides whether action is preview-only vs storage-affecting
- letting stale displayed nutrients look “current”
- overwriting user edits without confirmation

---

## Step G — Centralize display scaling consumers

### Goal
Prevent future bug dominoes by making other screens consume shared resolved math instead of custom ad hoc logic.

### Candidate consumers to audit after editor revamp
- `ui/food/FoodsListViewModel.kt`
- quick add calculation flow
- logging preview flow
- recipe ingredient entry rules
- food detail previews
- snapshot/export builders if they depend on serving display math

### Suggested files involved
- `ui/food/FoodsListViewModel.kt`
- quick add ViewModel/use case files
- logging use cases
- recipe editor/use case files

### Pitfalls
- editor becomes correct but list/logging still use old branch logic
- future screens bypass resolver for “quick” math
- maintaining two competing conversion systems

---

## Step H — Harden save pipeline

### Goal
Ensure final save persists only consistent state.

### Save should persist
- serving definition fields
- canonical normalized nutrients
- only after explicit recompute/apply rules are satisfied

### Save should block or warn when
- serving definition changed but nutrient display was not recomputed
- nutrient edits changed but were not applied
- serving resolution is blocked
- basis compatibility is invalid

### Suggested files involved
- `ui/food/editor/FoodEditorViewModel.kt`
- food save/update use case(s)
- repository/update pipeline files touched by food editor save

### Pitfalls
- save silently applying hidden recompute logic
- save persisting stale display state
- save path bypassing the new domain pipeline

---

## Step I — Add targeted tests before broad cleanup

### Goal
Lock down edge/corner behavior so the refactor actually reduces future regressions.

### Minimum high-value tests
#### Serving resolution tests
- `LB`, `OZ`, `KG`, `G`
- `ML`, `L`, `CUP_US`, etc.
- container units with explicit bridges
- incompatible units without bridges
- both grams and mL bridges present
- neither bridge present

#### Recompute tests
- PER_100G → serving display
- PER_100ML → serving display
- blocked incompatible cases
- stable round-trip expectations

#### Apply tests
- per-serving edited values back to canonical PER_100G
- per-serving edited values back to canonical PER_100ML
- invalid/blocked cases
- stale-field protection logic

#### Editor state tests
- serving dirty marks nutrients stale
- recompute discards nutrient edits only with confirmation
- apply clears nutrient dirty state
- save blocked on pending recompute/apply

### Suggested files involved
- **new** domain unit test files
- existing ViewModel tests
- any instrumented tests if editor flow is already covered there

### Pitfalls
- relying on manual UI testing only
- testing just happy paths
- missing mass/volume/container edge combinations

---

## 🧨 Bug-Domino Prevention Checklist

Before marking the refactor done, verify:

- [ ] No screen performs its own serving-to-nutrient math outside the shared pipeline
- [ ] No save path silently applies unresolved serving edits
- [ ] No recompute path overwrites manual nutrient edits without warning
- [ ] No canonical nutrient update happens incrementally during typing
- [ ] No mass-unit edge case (`lb`, `oz`, `kg`) is handled differently from `g`
- [ ] No volume-unit edge case (`cup`, `tbsp`, `fl oz`, `L`) is handled differently from `mL`
- [ ] Container/count units remain blocked unless bridged deterministically
- [ ] Editor UI clearly indicates stale vs applied vs blocked states
- [ ] Round-trip canonical/display/canonical math is deterministic enough for trusted editing

---

## ⏱️ Timetable / Relative Difficulty Addendum

### Highest effort
#### `FoodEditorViewModel` + editor state orchestration
**Difficulty:** High  
**Why:** this is where the interaction model changes most.

Likely work:
- introduce draft separation
- explicit actions
- dirty-state management
- save gating
- overwrite protection

---

### High-medium effort
#### `FoodEditorScreen` and supporting UI composables
**Difficulty:** High-medium  
**Why:** workflow must become visually explicit and robust.

---

### Medium effort
#### New domain pipeline
**Difficulty:** Medium  
**Why:** math is contained, but must be designed carefully and tested well.

Includes:
- `ServingResolution`
- recompute use case
- apply use case

---

### Medium effort
#### Downstream consumer audit
**Difficulty:** Medium  
**Why:** editor may be correct while other screens still use ad hoc math.

---

### Lower initial effort but critical follow-through
#### Tests
**Difficulty:** Medium  
**Why:** not conceptually hard, but essential to prevent future domino bugs.

---

## 🏁 Final Addendum Summary

This revamp should be treated as a **robustness architecture change**, not just an editor tweak.

### The target state is:
- canonical nutrients stay normalized to per 100g / per 100mL
- serving definition is resolved once
- nutrient math happens in bulk
- recompute/apply are explicit
- save only persists coherent state
- all screens eventually consume the same math pipeline

This is the right direction if the priority is:
- correctness
- predictability
- maintainability
- edge/corner safety
- fewer future bug cascades
