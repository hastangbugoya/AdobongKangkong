# Food Editor Save-Safety Revamp Plan
_Last updated: 2026-03-21_

## Goal

Fix the architectural leak where ordinary food edits can accidentally rewrite canonical nutrient rows.

This revamp must make the following true:

1. **Canonical nutrients are protected**
   - Once nutrients are normalized to `PER_100G` or `PER_100ML`, they must not be casually rewritten.
   - Ordinary food edits must not mutate `food_nutrients`.

2. **Serving metadata is safe**
   - User must be able to edit:
     - serving size
     - serving unit
     - gramsPerServingUnit
     - mlPerServingUnit
     - household/default serving text
   - without altering canonical nutrient rows.

3. **Save behavior is split by intent**
   - Food metadata save
   - Nutrient save
   - Basis reinterpretation / bulk recanonicalization save

4. **Dirty state is typed**
   - Not just “dirty”
   - Must distinguish metadata vs nutrients vs basis reinterpretation

5. **Correctness > speed**
   - Mid-stream compilation is not required
   - Avoid partial hacks that preserve current bug paths
   - Better to finish the architectural split cleanly, then compile/fix integration issues

---

## Confirmed Findings

### What is already correct
- USDA import path can correctly normalize source values to canonical `PER_100G`.
- Example confirmed:
  - imported value from `50 g` source
  - user chose “treat as per 100g”
  - calories stored as canonical `360 kcal PER_100G`
- Therefore import canonicalization is not the root bug.

### What is broken
- `SaveFoodWithNutrientsUseCase` is being used in contexts where user only changed safe food metadata.
- That use case canonicalizes incoming rows and replaces all nutrient rows.
- If caller passes stale/display/preview values instead of true canonical/source nutrient values, canonical storage is corrupted.

### Root bug
- **Serving/default measurement edits are reaching the nutrient persistence lane.**

---

## Architectural Target

### Lane 1 — Safe Food Metadata Save
Use when user changes only:
- name
- brand
- serving size
- serving unit
- gramsPerServingUnit
- mlPerServingUnit
- household/default serving text
- similar food-row-only metadata

This lane:
- saves only the `foods` row
- must never touch `food_nutrients`

### Lane 2 — Canonical Nutrient Save
Use when user explicitly edits nutrient values:
- calories
- protein
- carbs
- fat
- sodium
- etc.

This lane:
- updates canonical nutrient storage
- should be explicit and deliberate
- should not be the default editor save path

### Lane 3 — Basis Reinterpretation / Bulk Recanonicalization
Use only for high-risk operations:
- “Treat this imported data as per 100g”
- “Treat this imported data as per serving”
- replace or reinterpret nutrient basis across the food
- import-time canonicalization / re-canonicalization

This lane:
- is dangerous by design
- should require explicit flow / confirmation
- should not piggyback on ordinary save

---

## Policy Decisions

### Canonical nutrient policy
Once a food’s nutrients are normalized to a canonical basis:
- treat canonical nutrient rows as **protected**
- they are not literally immutable in storage
- but they should be logically hard to mutate

Canonical nutrient rows may change only through:
- explicit nutrient edit flow
- explicit basis reinterpretation flow
- explicit import/replacement flow

### Serving metadata policy
Serving-related edits are **food metadata**, not nutrient edits.

Changing:
- `50 g` to `1 rc cup`
- `gramsPerServingUnit = 180`
- `servingUnit = RCCUP`

must:
- update food metadata
- update display/logging defaults
- **not** rewrite canonical nutrient rows

### Preview policy
Displayed serving nutrients are previews/derived values.

They must never be treated as source-of-truth persistence inputs.

---

## New Dirty-State Model

Replace vague “dirty” with typed dirty tracking.

### Required dirty flags
- `isFoodMetadataDirty`
- `areNutrientsDirty`
- `isBasisInterpretationDirty`

### Rules
- serving size/unit changes -> `isFoodMetadataDirty = true`
- gramsPerServingUnit/mlPerServingUnit changes -> `isFoodMetadataDirty = true`
- nutrient amount changes -> `areNutrientsDirty = true`
- changing basis interpretation -> `isBasisInterpretationDirty = true`

### Critical rule
Serving edits must **not** mark nutrients dirty.

---

## Use Case Plan

### 1) Add `SaveFoodMetadataUseCase`
**Purpose**
- Save only safe food-row metadata
- Must not touch nutrients

**Behavior**
- accept `Food`
- persist via `FoodRepository.upsert(food)`
- do not call nutrient repository
- do not canonicalize nutrient rows
- do not interpret current serving preview as persistence source

**Notes**
- This does not require changing `FoodRepository` interface first
- Current `FoodRepository.upsert(food)` is sufficient for the first safe version

**Checklist**
- [ ] Create `SaveFoodMetadataUseCase`
- [ ] Inject `FoodRepository`
- [ ] `invoke(food: Food): Long`
- [ ] Delegate to `foodRepository.upsert(food)`
- [ ] Add KDoc warning that this does not save nutrients

---

### 2) Keep `SaveFoodWithNutrientsUseCase` as dangerous explicit path
**Purpose**
- bulk canonical nutrient replacement
- import/recanonicalization/explicit nutrient save path only

**Do not**
- fold safe metadata save into this use case
- add hidden split logic here
- let this use case silently decide whether it is “safe enough”

**Checklist**
- [ ] Leave use case as nutrient/bulk path
- [ ] Add top-level warning comment/KDoc:
  - not for ordinary food edits
  - not for serving metadata edits
- [ ] Audit all call sites and remove ordinary editor save usage

---

### 3) Add `UpsertSingleFoodNutrientUseCase`
**Purpose**
- pinpoint canonical nutrient mutation
- one food
- one nutrient
- one value

**Intended use**
- deliberate nutrient edits
- future per-row nutrient edit UI

**Input shape**
- `foodId`
- `nutrientId`
- `amount`
- explicit basis context
- optional source/reason later if desired

**Behavior**
- validate food exists
- validate nutrient exists
- validate amount
- update exactly one nutrient row
- do not rebuild whole nutrient set

**Checklist**
- [ ] Design input contract
- [ ] Add use case
- [ ] Add repository/DAO support if needed
- [ ] Decide whether upsert should reject basis mismatch or require explicit matching basis
- [ ] Keep this path explicit and annoying to reach from UI

---

### 4) Add dedicated basis reinterpretation flow
**Purpose**
- explicit high-friction path for changing nutrient meaning

**Examples**
- treat imported values as per 100g
- treat imported values as per serving
- recanonicalize from serving basis into per-100 basis

**Checklist**
- [ ] Separate this from ordinary save
- [ ] Require explicit confirmation
- [ ] Do not piggyback on normal Save
- [ ] Reuse bulk nutrient save lane only here

---

## Food Editor Revamp Plan

### Phase A — Save-path safety split
Goal: stop ordinary save from touching nutrients.

**Checklist**
- [ ] Find current FoodEditor save caller(s)
- [ ] Replace ordinary `SaveFoodWithNutrientsUseCase(...)` usage
- [ ] Route metadata-only edits to `SaveFoodMetadataUseCase`
- [ ] Only route to nutrient save path if nutrients dirty
- [ ] Only route to basis-reinterpretation flow if basis dirty
- [ ] Ensure serving changes no longer rewrite `food_nutrients`

### Phase B — Dirty-state classification
Goal: typed dirty instead of one generic dirty boolean.

**Checklist**
- [ ] Add `isFoodMetadataDirty`
- [ ] Add `areNutrientsDirty`
- [ ] Add `isBasisInterpretationDirty`
- [ ] Update all editor edit events to mark correct dirty lane
- [ ] Verify serving edits mark metadata-only dirty
- [ ] Verify nutrient edits do not implicitly mark basis dirty unless basis changed

### Phase C — Nutrient-edit UX hardening
Goal: make canonical nutrient edits deliberate.

**Checklist**
- [ ] Remove casual inline canonical nutrient mutation if present
- [ ] Add explicit nutrient edit affordance
- [ ] Require user to enter nutrient edit mode or tap nutrient row
- [ ] Show current basis clearly
- [ ] Show old value -> new value before save
- [ ] Require explicit confirm

### Phase D — Basis reinterpretation UX hardening
Goal: make basis changes even more deliberate.

**Checklist**
- [ ] Separate “Edit serving/default measurement” from “Reinterpret nutrition basis”
- [ ] Add warning copy for basis changes
- [ ] Require second confirm
- [ ] Make it clear this affects canonical storage, logging, planner, recipes

---

## Import Flow Improvement Plan

### Problem to solve
For USDA/mass-based imports:
- canonical nutrients may be correct as `PER_100G`
- user may still think in household/volume units
- app should help user define measurement bridge without touching canonical nutrients

### UX direction
After user chooses how to interpret imported nutrition:
- optionally ask how they usually measure the food

Example concept:
- “How do you usually measure this food?”
  - I use grams
  - I use volume / household units

If user chooses volume/household:
- ask for serving unit
- ask for grams per unit
- example:
  - `1 rice cooker cup = 180 g`

### Important rule
This import follow-up step:
- writes food metadata only
- does **not** rewrite nutrients

### Checklist
- [ ] Design import follow-up step
- [ ] Add optional household measurement question
- [ ] Save via `SaveFoodMetadataUseCase`
- [ ] Confirm no nutrient rewrite occurs
- [ ] Allow skip for now
- [ ] Reuse later from Food Editor if user skipped during import

---

## DAO / Repository Considerations

### Current safe path
Current `FoodRepository.upsert(food)` is enough to support metadata-only save for first implementation.

### Do not do yet
- Do not over-optimize into partial DAO update methods unless needed
- Do not try to wedge new safe behavior into existing bulk nutrient save use case

### Later possible refinements
- add more targeted food metadata update DAO methods only if performance/clarity requires it
- add targeted nutrient row upsert DAO methods for pinpoint nutrient editing

**Checklist**
- [ ] Use repository upsert first for metadata save
- [ ] Avoid new DAO complexity until behavior is correct
- [ ] Add pinpoint nutrient DAO support only when implementing single nutrient upsert

---

## Validation / Invariants Checklist

These must remain true after revamp.

### Persistence invariants
- [ ] Ordinary food metadata save never touches `food_nutrients`
- [ ] Canonical nutrient save never uses preview values as source-of-truth
- [ ] Basis reinterpretation path is explicit
- [ ] `PER_100G` rows store per-100g values
- [ ] `PER_100ML` rows store per-100ml values
- [ ] Serving preview values are never persisted as canonical nutrient rows

### Editor invariants
- [ ] Serving edits mark metadata dirty only
- [ ] Nutrient edits mark nutrient dirty
- [ ] Basis reinterpretation marks basis dirty
- [ ] Save routing respects dirty classification

### UX invariants
- [ ] User can change default serving without corrupting nutrient rows
- [ ] Canonical nutrient editing feels deliberate, not casual
- [ ] Basis reinterpretation feels even more deliberate
- [ ] Import can collect household measurement bridge without touching nutrients

---

## Test Plan

### Critical regression tests
- [ ] Import USDA serving-based nutrition as `PER_100G`
- [ ] Verify canonical nutrient row stored correctly
- [ ] Edit serving metadata only
- [ ] Save
- [ ] Verify nutrient rows unchanged
- [ ] Verify food metadata updated

### Rice case regression
Given:
- imported source serving = `50 g`
- canonical stored = `360 kcal PER_100G`

Then:
- change serving to `1 RCCUP`
- set `gramsPerServingUnit = 180`
- save metadata only

Expected:
- `food_nutrients` still `360 kcal PER_100G`
- `foods.servingUnit = RCCUP`
- `foods.servingSize = 1`
- `foods.gramsPerServingUnit = 180`

### Dirty routing tests
- [ ] serving edit only -> metadata lane only
- [ ] nutrient edit only -> nutrient lane only
- [ ] basis reinterpretation -> basis lane
- [ ] mixed edit -> correct combined routing or explicit handling

### Single nutrient edit tests
- [ ] upsert one nutrient only
- [ ] other nutrient rows unchanged
- [ ] basis mismatch handled safely
- [ ] negative/invalid values rejected appropriately

### Import bridge tests
- [ ] import canonical nutrients
- [ ] add household unit bridge
- [ ] verify nutrients unchanged
- [ ] verify serving metadata saved

---

## Suggested Implementation Order

### Phase 1 — Safety first
- [ ] Add `SaveFoodMetadataUseCase`
- [ ] Change Food Editor ordinary save to use it
- [ ] Stop ordinary editor save from calling `SaveFoodWithNutrientsUseCase`

### Phase 2 — Dirty-state split
- [ ] Add typed dirty flags
- [ ] Route save by dirty type

### Phase 3 — Harden dangerous lanes
- [ ] Mark `SaveFoodWithNutrientsUseCase` as dangerous explicit path
- [ ] Add warning KDoc/comments
- [ ] Audit remaining call sites

### Phase 4 — Pinpoint nutrient editing
- [ ] Add `UpsertSingleFoodNutrientUseCase`
- [ ] Add DAO/repo support
- [ ] Add deliberate nutrient edit UI

### Phase 5 — Import measurement onboarding
- [ ] Add optional “How do you usually measure this?” step
- [ ] Save measurement bridge as metadata only

### Phase 6 — Basis reinterpretation UX
- [ ] Add explicit scary/high-friction flow
- [ ] Confirm before recanonicalization

---

## Anti-Goals / Do Not Do

- [ ] Do not split safe-vs-dangerous behavior inside `SaveFoodWithNutrientsUseCase`
- [ ] Do not keep using bulk nutrient replacement for ordinary metadata edits
- [ ] Do not persist displayed serving-preview nutrient values as canonical rows
- [ ] Do not let serving changes implicitly rewrite nutrients
- [ ] Do not auto-convert grams <-> mL without explicit valid bridge/density
- [ ] Do not optimize prematurely around DAO partial updates before behavior is correct

---

## Definition of Done

This revamp is done when all of the following are true:

- [ ] Food Editor can save serving/default measurement changes without touching nutrient rows
- [ ] Canonical nutrient rows remain stable after safe metadata edits
- [ ] Food Editor save path is routed by typed dirty state
- [ ] `SaveFoodMetadataUseCase` exists and is the default safe save path
- [ ] `SaveFoodWithNutrientsUseCase` is no longer the default ordinary editor save path
- [ ] deliberate nutrient editing has its own explicit lane
- [ ] basis reinterpretation has its own explicit lane
- [ ] import can optionally collect household measurement bridge without mutating canonical nutrients
- [ ] rice regression case passes end-to-end

---

## Short Version

### The one rule that drives the whole revamp:
**Changing how the user measures a food must not change what the food nutritionally is.**
