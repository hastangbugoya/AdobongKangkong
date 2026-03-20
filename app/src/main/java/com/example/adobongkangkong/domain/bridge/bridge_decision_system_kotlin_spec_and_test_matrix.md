# Bridge Decision System — Kotlin-Ready Spec and Test Matrix

## Purpose

This document converts the Bridge Decision System into a **strict implementation blueprint**:
- Kotlin-ready type design
- evaluator contract
- policy contract
- decision matrix
- test matrix
- invariants and anti-corruption rules

This is still **design/spec only**. It is intentionally implementation-oriented, but does **not** include production code.

---

# 1. Recommended Architectural Split

Use a **two-phase system**.

## Phase 1 — Capability Evaluation
Determines what kind of conversion path exists from food facts alone.

Outputs:
- conversion class
- bridge confidence
- bridge source
- whether fallback was used
- whether matched serving meaning was required and satisfied

## Phase 2 — Policy Evaluation
Determines what the current flow is allowed to do with that capability result.

Inputs:
- capability result
- flow
- action

Outputs:
- allowed / blocked
- warning level
- persistable / not persistable
- requires user action
- UI directives

This split keeps the logic:
- deterministic
- testable
- reusable
- flow-consistent

---

# 2. Kotlin-Ready Type Spec

## 2.1 Unit Basis

```kotlin
enum class UnitBasis {
    MASS,
    VOLUME,
    COUNT_OR_CONTAINER,
    UNKNOWN
}
```

### Meaning
- `MASS`: `mg`, `g`, `kg`, `oz`, `lb`
- `VOLUME`: `mL`, `L`, `tsp`, `tbsp`, `cup`, etc.
- `COUNT_OR_CONTAINER`: `serving`, `piece`, `slice`, `bottle`, `pack`, etc.
- `UNKNOWN`: ambiguous or unsupported basis

---

## 2.2 Conversion Class

```kotlin
enum class ConversionClass {
    IDENTITY,
    INTRA_BASIS_STANDARD,
    INTRA_FOOD_SERVING_MAPPING,
    CROSS_BASIS_BRIDGED,
    CROSS_BASIS_ESTIMATED,
    UNRESOLVABLE
}
```

### Meaning
- `IDENTITY`: same unit to same unit
- `INTRA_BASIS_STANDARD`: mass↔mass or volume↔volume standard conversion
- `INTRA_FOOD_SERVING_MAPPING`: food-specific serving/container mapping without estimated cross-basis fallback
- `CROSS_BASIS_BRIDGED`: mass↔volume through strong bridge
- `CROSS_BASIS_ESTIMATED`: mass↔volume only through fallback approximation
- `UNRESOLVABLE`: no safe path

---

## 2.3 Bridge Confidence

```kotlin
enum class BridgeConfidence {
    STRONG,
    ESTIMATED,
    NONE
}
```

### Interpretation
- `STRONG`: usable as real food-backed conversion capability
- `ESTIMATED`: temporary convenience only
- `NONE`: conversion unavailable

---

## 2.4 Bridge Source

```kotlin
enum class BridgeSource {
    IDENTITY,
    STANDARD_UNIT_CONVERSION,
    EXPLICIT_GRAMS_PER_SERVING_UNIT,
    EXPLICIT_ML_PER_SERVING_UNIT,
    EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME,
    DERIVED_DENSITY_FROM_MATCHED_SERVING_DATA,
    USER_CONFIRMED_BRIDGE,
    FALLBACK_1ML_EQ_1G,
    NO_BRIDGE
}
```

### Notes
- Keep source separate from confidence.
- Multiple sources may map to `STRONG`.
- UI copy and persistence rules depend on source.

---

## 2.5 Flow

```kotlin
enum class BridgeFlow {
    QUICK_ADD,
    FOOD_EDITOR_VIEW,
    FOOD_EDITOR_SAVE,
    RECIPE_BUILDER,
    FOOD_LOGGING_SAVED_FOOD,
    FOOD_LOGGING_AD_HOC,
    DISPLAY_ONLY
}
```

---

## 2.6 Action

```kotlin
enum class BridgeAction {
    DISPLAY_CONVERTED_AMOUNT,
    COMPUTE_LOG_GRAMS,
    SAVE_FOOD_METADATA,
    SET_PREFERRED_LOGGING_UNIT,
    COMPUTE_RECIPE_INGREDIENT_MASS,
    SHOW_UI_PREVIEW
}
```

---

## 2.7 Warning Level

```kotlin
enum class BridgeWarningLevel {
    NONE,
    INFO,
    SOFT_WARNING,
    HARD_BLOCK
}
```

---

## 2.8 Capability Input

```kotlin
data class BridgeCapabilityInput(
    val fromUnit: ServingUnit,
    val toUnit: ServingUnit,
    val fromBasis: UnitBasis,
    val toBasis: UnitBasis,

    val gramsPerServingUnit: Double?,
    val mlPerServingUnit: Double?,

    /**
     * True only when gramsPerServingUnit and mlPerServingUnit refer
     * to the same serving meaning / same semantic serving definition.
     */
    val hasMatchedServingMeaning: Boolean,

    /**
     * Optional future extension if you support direct user-confirmed bridge facts.
     */
    val hasUserConfirmedBridge: Boolean,

    /**
     * Pure capability phase should not decide policy,
     * but it may receive whether fallback may be considered.
     * If preferred, move this to policy phase only.
     */
    val mayConsiderFallbackEstimate: Boolean
)
```

### Note
If you want maximal purity, you can remove `mayConsiderFallbackEstimate` from capability evaluation and let capability output “fallback possible” while policy decides whether to allow it.

---

## 2.9 Capability Result

```kotlin
data class BridgeCapabilityResult(
    val conversionClass: ConversionClass,
    val confidence: BridgeConfidence,
    val source: BridgeSource,

    /**
     * True when resolution depended on 1 mL ≈ 1 g fallback.
     */
    val fallbackUsed: Boolean,

    /**
     * True when a strong bridge was derived only because
     * matched serving meaning was verified.
     */
    val requiredMatchedServingMeaning: Boolean,

    /**
     * Human/debug summary only.
     */
    val reason: String
)
```

---

## 2.10 Policy Input

```kotlin
data class BridgePolicyInput(
    val flow: BridgeFlow,
    val action: BridgeAction,
    val capability: BridgeCapabilityResult
)
```

---

## 2.11 Final Decision

```kotlin
data class BridgeDecision(
    val conversionClass: ConversionClass,
    val confidence: BridgeConfidence,
    val source: BridgeSource,

    val isAllowed: Boolean,
    val isPersistableAsFoodTruth: Boolean,
    val requiresUserAction: Boolean,

    val warningLevel: BridgeWarningLevel,

    val shouldShowEstimateBadge: Boolean,
    val shouldShowBlockingPrompt: Boolean,
    val shouldPromptForRealBridge: Boolean,
    val shouldFallbackToSafeUnit: Boolean,

    val fallbackUsed: Boolean,
    val reason: String
)
```

---

# 3. Evaluator Contracts

## 3.1 Capability Evaluator Contract

```kotlin
interface EvaluateBridgeCapability {
    operator fun invoke(input: BridgeCapabilityInput): BridgeCapabilityResult
}
```

## Responsibility
This evaluator must answer:

> What conversion path exists from the known food facts?

It must **not** decide:
- whether the editor may save
- whether recipe flow may proceed
- which warning style UI should use

It should only classify the capability.

---

## 3.2 Policy Evaluator Contract

```kotlin
interface EvaluateBridgePolicy {
    operator fun invoke(input: BridgePolicyInput): BridgeDecision
}
```

## Responsibility
This evaluator must answer:

> Given the conversion capability and the current flow/action, what is allowed?

It must decide:
- allow vs block
- persistable vs not persistable
- warning level
- whether user must enter real bridge data
- whether UI must show estimate badge

---

# 4. Capability Resolution Rules

Use these rules in order. The order must be fixed.

## Rule 1 — Identity
If `fromUnit == toUnit`:
- `conversionClass = IDENTITY`
- `confidence = STRONG`
- `source = IDENTITY`

---

## Rule 2 — Same-basis standard conversion
If `fromBasis == MASS && toBasis == MASS`:
- `conversionClass = INTRA_BASIS_STANDARD`
- `confidence = STRONG`
- `source = STANDARD_UNIT_CONVERSION`

If `fromBasis == VOLUME && toBasis == VOLUME`:
- same result

No bridge logic needed.

---

## Rule 3 — Direct food serving mapping
If request resolves directly via food-specific serving mapping without fallback:
- serving/container → g with explicit `gramsPerServingUnit`
- serving/container → mL with explicit `mlPerServingUnit`

Then:
- `conversionClass = INTRA_FOOD_SERVING_MAPPING`
- `confidence = STRONG`
- `source = EXPLICIT_GRAMS_PER_SERVING_UNIT` or `EXPLICIT_ML_PER_SERVING_UNIT`

---

## Rule 4 — Strong cross-basis via matched serving facts
If mass↔volume conversion is requested and:
- `gramsPerServingUnit != null`
- `mlPerServingUnit != null`
- `hasMatchedServingMeaning == true`

Then:
- `conversionClass = CROSS_BASIS_BRIDGED`
- `confidence = STRONG`
- `source = EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME`
  or `DERIVED_DENSITY_FROM_MATCHED_SERVING_DATA`

This is the main strong bridge path.

---

## Rule 5 — Strong cross-basis via user-confirmed bridge
If future supported:
- `hasUserConfirmedBridge == true`

Then:
- `conversionClass = CROSS_BASIS_BRIDGED`
- `confidence = STRONG`
- `source = USER_CONFIRMED_BRIDGE`

---

## Rule 6 — Estimated cross-basis fallback
If cross-basis conversion is requested, no strong bridge exists, and fallback is considered:
- `conversionClass = CROSS_BASIS_ESTIMATED`
- `confidence = ESTIMATED`
- `source = FALLBACK_1ML_EQ_1G`
- `fallbackUsed = true`

---

## Rule 7 — Unresolvable
If none of the above apply:
- `conversionClass = UNRESOLVABLE`
- `confidence = NONE`
- `source = NO_BRIDGE`

---

# 5. Policy Rules

## 5.1 STRONG
Default:
- allowed
- persistable if action intends to save food truth
- no estimate badge
- no blocking prompt

---

## 5.2 ESTIMATED
Default:
- allowed only in explicitly permitted flows
- never persistable as food truth
- estimate badge required
- may require prompt if user tries to save or reuse as truth

Recommended:
- allowed in `QUICK_ADD`
- optionally allowed in `FOOD_LOGGING_AD_HOC`
- disallowed in `FOOD_EDITOR_SAVE`
- disallowed in `RECIPE_BUILDER`
- disallowed for `SET_PREFERRED_LOGGING_UNIT` when that would imply real bridge truth

---

## 5.3 NONE
Default:
- not allowed when conversion is required
- hard block or fallback to safe unit
- prompt for real bridge if user intent needs the conversion

---

# 6. Canonical Policy Table

## 6.1 By Confidence and Flow

| Confidence | QuickAdd | FoodEditor View | FoodEditor Save | RecipeBuilder | Saved-Food Logging | Ad Hoc Logging | Display Only | Food Truth Persistence |
|---|---|---|---|---|---|---|---|---|
| STRONG | Allow | Allow | Allow | Allow | Allow | Allow | Allow | Yes |
| ESTIMATED | Allow with warning | Display as estimate only | Block | Block | Prefer block | Optional with warning | Allow if labeled | No |
| NONE | Block if required | Show unavailable | Block | Block | Block | Block | Do not fake numeric conversion | No |

---

## 6.2 By Action

| Action | STRONG | ESTIMATED | NONE |
|---|---|---|---|
| DISPLAY_CONVERTED_AMOUNT | Allow | Allow only if labeled | Block or show unavailable |
| COMPUTE_LOG_GRAMS | Allow | Allow only in permitted flows | Block |
| SAVE_FOOD_METADATA | Allow | Block | Block |
| SET_PREFERRED_LOGGING_UNIT | Allow | Block if it implies bridge truth | Block or fallback |
| COMPUTE_RECIPE_INGREDIENT_MASS | Allow | Block | Block |
| SHOW_UI_PREVIEW | Allow | Allow if labeled | Show unsupported |

---

# 7. Strict Invariants

These should be treated as system laws.

## Invariant 1
Fallback `1 mL ≈ 1 g` is never canonical food truth.

## Invariant 2
Only `STRONG` bridge capability may be persisted as food cross-basis truth.

## Invariant 3
Same-basis conversion is not a food bridge.

## Invariant 4
Preferred logging unit is a preference, not proof of executable bridge capability.

## Invariant 5
If `ESTIMATED` is used in any user-facing result, the UI must expose it.

## Invariant 6
Recipe ingredient mass resolution must not depend on fallback estimate.

## Invariant 7
FoodEditor save must not persist estimate-derived bridge fields.

## Invariant 8
Matched serving mass and volume must refer to the same serving meaning before deriving strong bridge.

## Invariant 9
No hydration path may reconstruct food truth from previously estimated UI state.

## Invariant 10
No migration/backfill may infer strong bridge data from historical estimate usage.

---

# 8. Anti-Corruption Rules

## Rule A
No repository/DAO save path may receive fallback-derived bridge values for canonical food fields.

## Rule B
No UI formatter may render estimated conversion in the same style as strong bridge conversion.

## Rule C
No recipe flow may silently use estimated cross-basis conversion.

## Rule D
No logging flow may omit estimate labeling if fallback was used.

## Rule E
No “preferred unit” application may force unsafe cross-basis execution.

## Rule F
No editor state restoration may treat estimate-backed preview values as confirmed bridge facts.

---

# 9. Suggested Reason Strings

To make tests and debugging easier, standardize reasons.

Recommended reason constants or canonical message families:

- `"identity"`
- `"same_basis_standard_conversion"`
- `"explicit_grams_per_serving_mapping"`
- `"explicit_ml_per_serving_mapping"`
- `"strong_bridge_from_matched_serving_mass_and_volume"`
- `"strong_bridge_from_user_confirmed_bridge"`
- `"estimated_cross_basis_via_fallback_1ml_eq_1g"`
- `"cross_basis_unresolvable_no_bridge"`
- `"blocked_estimated_not_persistable"`
- `"blocked_no_bridge"`
- `"fallback_to_safe_unit_due_to_missing_bridge"`

This helps snapshot tests and log clarity.

---

# 10. Test Strategy

Use two separate suites.

## 10.1 Capability Evaluator Tests
Pure tests of:
- conversion class
- confidence
- source
- fallback usage

## 10.2 Policy Evaluator Tests
Pure tests of:
- allow/block
- persistability
- warning level
- UI directives

This is the cleanest way to keep tests stable and readable.

---

# 11. Capability Test Matrix

Each row is a pure evaluator test.

## Legend
- GPSU = `gramsPerServingUnit`
- MPSU = `mlPerServingUnit`
- MSM = `hasMatchedServingMeaning`
- UCB = `hasUserConfirmedBridge`
- Fallback? = evaluator allowed to consider fallback

| Case | From | To | GPSU | MPSU | MSM | UCB | Fallback? | Expected Class | Expected Confidence | Expected Source |
|---|---|---:|---:|---:|---:|---:|---:|---|---|---|
| C1 | G | G | - | - | - | - | No | IDENTITY | STRONG | IDENTITY |
| C2 | G | OZ | - | - | - | - | No | INTRA_BASIS_STANDARD | STRONG | STANDARD_UNIT_CONVERSION |
| C3 | TBSP | ML | - | - | - | - | No | INTRA_BASIS_STANDARD | STRONG | STANDARD_UNIT_CONVERSION |
| C4 | SERVING | G | 100 | null | false | false | No | INTRA_FOOD_SERVING_MAPPING | STRONG | EXPLICIT_GRAMS_PER_SERVING_UNIT |
| C5 | SERVING | ML | null | 240 | false | false | No | INTRA_FOOD_SERVING_MAPPING | STRONG | EXPLICIT_ML_PER_SERVING_UNIT |
| C6 | CUP | G | 250 | 240 | true | false | No | CROSS_BASIS_BRIDGED | STRONG | EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME |
| C7 | ML | G | null | null | false | true | No | CROSS_BASIS_BRIDGED | STRONG | USER_CONFIRMED_BRIDGE |
| C8 | ML | G | null | null | false | false | Yes | CROSS_BASIS_ESTIMATED | ESTIMATED | FALLBACK_1ML_EQ_1G |
| C9 | ML | G | null | null | false | false | No | UNRESOLVABLE | NONE | NO_BRIDGE |
| C10 | CUP | G | 200 | 250 | false | false | No | UNRESOLVABLE | NONE | NO_BRIDGE |
| C11 | PIECE | G | 60 | null | false | false | No | INTRA_FOOD_SERVING_MAPPING | STRONG | EXPLICIT_GRAMS_PER_SERVING_UNIT |
| C12 | PIECE | ML | null | 30 | false | false | No | INTRA_FOOD_SERVING_MAPPING | STRONG | EXPLICIT_ML_PER_SERVING_UNIT |

## Critical Case: Mismatched Serving Meaning
Case `C10` is critical:
- both GPSU and MPSU exist
- but they do **not** refer to same serving meaning
- evaluator must **not** derive strong bridge

That is one of the most important regression guards in the whole system.

---

# 12. Policy Test Matrix

Each row is a pure policy test using a prepared capability result.

| Case | Capability Confidence | Capability Source | Flow | Action | Expected Allowed | Persistable | Warning | Estimate Badge | Blocking Prompt | Real Bridge Prompt |
|---|---|---|---|---|---:|---:|---|---:|---:|---:|
| P1 | STRONG | IDENTITY | QUICK_ADD | DISPLAY_CONVERTED_AMOUNT | Yes | No | NONE | No | No | No |
| P2 | STRONG | EXPLICIT_GRAMS_PER_SERVING_UNIT | FOOD_EDITOR_SAVE | SAVE_FOOD_METADATA | Yes | Yes | NONE | No | No | No |
| P3 | STRONG | EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME | RECIPE_BUILDER | COMPUTE_RECIPE_INGREDIENT_MASS | Yes | No | NONE | No | No | No |
| P4 | ESTIMATED | FALLBACK_1ML_EQ_1G | QUICK_ADD | COMPUTE_LOG_GRAMS | Yes | No | SOFT_WARNING | Yes | No | No |
| P5 | ESTIMATED | FALLBACK_1ML_EQ_1G | FOOD_EDITOR_SAVE | SAVE_FOOD_METADATA | No | No | HARD_BLOCK | Yes | Yes | Yes |
| P6 | ESTIMATED | FALLBACK_1ML_EQ_1G | RECIPE_BUILDER | COMPUTE_RECIPE_INGREDIENT_MASS | No | No | HARD_BLOCK | Yes | Yes | Yes |
| P7 | NONE | NO_BRIDGE | QUICK_ADD | COMPUTE_LOG_GRAMS | No | No | HARD_BLOCK | No | Yes | Yes |
| P8 | NONE | NO_BRIDGE | DISPLAY_ONLY | SHOW_UI_PREVIEW | No | No | INFO | No | No | No |
| P9 | ESTIMATED | FALLBACK_1ML_EQ_1G | FOOD_LOGGING_AD_HOC | COMPUTE_LOG_GRAMS | Yes | No | SOFT_WARNING | Yes | No | No |
| P10 | ESTIMATED | FALLBACK_1ML_EQ_1G | FOOD_LOGGING_SAVED_FOOD | COMPUTE_LOG_GRAMS | No | No | HARD_BLOCK | Yes | Yes | Yes |
| P11 | NONE | NO_BRIDGE | FOOD_EDITOR_SAVE | SET_PREFERRED_LOGGING_UNIT | No | No | HARD_BLOCK | No | Yes | Yes |
| P12 | STRONG | USER_CONFIRMED_BRIDGE | FOOD_EDITOR_SAVE | SET_PREFERRED_LOGGING_UNIT | Yes | Yes | NONE | No | No | No |

---

# 13. End-to-End Scenario Matrix

These are cross-feature behavioral tests to prevent drift.

## Scenario S1 — QuickAdd estimate stays estimate
### Setup
- food has no strong bridge
- QuickAdd uses fallback `1 mL ≈ 1 g`

### Expected
- QuickAdd decision = allowed with `ESTIMATED`
- estimate badge shown
- food save state remains unchanged
- later FoodEditor must still see no strong bridge

---

## Scenario S2 — Strong bridge works everywhere
### Setup
- food has matched `gramsPerServingUnit` and `mlPerServingUnit`

### Expected
- QuickAdd = allow
- FoodEditor save = allow
- RecipeBuilder = allow
- preferred volume unit = allowed
- no estimate badge anywhere

---

## Scenario S3 — Recipe rejects fallback
### Setup
- no strong bridge
- user tries volume ingredient in recipe

### Expected
- capability may say `ESTIMATED` if fallback possible in general
- policy for recipe = block
- prompt for grams or real bridge

---

## Scenario S4 — Preferred unit does not imply bridge
### Setup
- preferred logging unit is `CUP`
- no strong bridge

### Expected
- QuickAdd may allow with estimate
- saved-food logging may block or fallback to grams per policy
- FoodEditor save may not convert preference into food truth

---

## Scenario S5 — Mismatched serving meanings do not produce density
### Setup
- `gramsPerServingUnit` refers to one semantic serving
- `mlPerServingUnit` refers to another semantic serving

### Expected
- evaluator returns `UNRESOLVABLE` or at least not `STRONG`
- recipe/editor do not derive bridge
- no strong density saved

---

# 14. Snapshot-Friendly Expected Objects

To make tests more explicit, decide exact object expectations.

## Example Capability Result Snapshot

```kotlin
BridgeCapabilityResult(
    conversionClass = ConversionClass.CROSS_BASIS_ESTIMATED,
    confidence = BridgeConfidence.ESTIMATED,
    source = BridgeSource.FALLBACK_1ML_EQ_1G,
    fallbackUsed = true,
    requiredMatchedServingMeaning = false,
    reason = "estimated_cross_basis_via_fallback_1ml_eq_1g"
)
```

## Example Policy Result Snapshot

```kotlin
BridgeDecision(
    conversionClass = ConversionClass.CROSS_BASIS_ESTIMATED,
    confidence = BridgeConfidence.ESTIMATED,
    source = BridgeSource.FALLBACK_1ML_EQ_1G,
    isAllowed = true,
    isPersistableAsFoodTruth = false,
    requiresUserAction = false,
    warningLevel = BridgeWarningLevel.SOFT_WARNING,
    shouldShowEstimateBadge = true,
    shouldShowBlockingPrompt = false,
    shouldPromptForRealBridge = false,
    shouldFallbackToSafeUnit = false,
    fallbackUsed = true,
    reason = "estimated_cross_basis_via_fallback_1ml_eq_1g"
)
```

---

# 15. Recommended Minimal Shared API Surface

If you want a tight shared contract, this is enough:

```kotlin
interface BridgeBasisResolver {
    fun basisOf(unit: ServingUnit): UnitBasis
}

interface EvaluateBridgeCapability {
    operator fun invoke(input: BridgeCapabilityInput): BridgeCapabilityResult
}

interface EvaluateBridgePolicy {
    operator fun invoke(input: BridgePolicyInput): BridgeDecision
}
```

Optional façade:

```kotlin
interface MakeBridgeDecision {
    operator fun invoke(
        capabilityInput: BridgeCapabilityInput,
        flow: BridgeFlow,
        action: BridgeAction
    ): BridgeDecision
}
```

### Façade responsibility
- run capability evaluator
- run policy evaluator
- return final decision

This façade is convenient, but keep the two inner layers separately testable.

---

# 16. Recommended Default Policy Decisions

Use these as safe defaults.

## QuickAdd
- allow `STRONG`
- allow `ESTIMATED` with estimate badge
- block `NONE`

## FoodEditor View
- show `STRONG`
- show `ESTIMATED` as estimate only
- show `NONE` as unavailable

## FoodEditor Save
- allow `STRONG`
- block `ESTIMATED`
- block `NONE`

## RecipeBuilder
- allow `STRONG`
- block `ESTIMATED`
- block `NONE`

## Saved-Food Logging
- allow `STRONG`
- block `ESTIMATED` by default
- block `NONE`

## Ad Hoc Logging
- allow `STRONG`
- optionally allow `ESTIMATED` with warning
- block `NONE`

## Display Only
- show `STRONG`
- show `ESTIMATED` only if labeled
- do not fake numeric conversion for `NONE`

---

# 17. UX Contract

## STRONG
Allowed UI language:
- standard numeric conversion
- no estimate labeling

## ESTIMATED
Required UI language:
- "Estimated"
- "Approximate conversion"
- "Using 1 mL ≈ 1 g"

Not allowed:
- plain numeric display with no qualifier

## NONE
Required behavior:
- no silent fake number
- show unavailability or prompt for grams / real bridge

---

# 18. Future-Proofing Notes

This spec leaves room for future additions without breaking the model:
- explicit density field
- per-serving bridge provenance
- user-confirmed density workflow
- recipe-level estimate mode
- audit metadata on log entries

None of those require changing the central evaluator-policy architecture.

---

# 19. Final System Law

**Estimated conversion may help the user act quickly, but it must never silently become food truth.**
