
# Bridge Confidence System Design

## 1. Core idea

Treat every cross-basis conversion as one of three states:

- **STRONG**  
  Conversion is backed by explicit food-specific data or a trustworthy derivation.
- **ESTIMATED**  
  Conversion is allowed for temporary UX/math convenience, but is not trustworthy enough to silently become durable food truth.
- **NONE**  
  Conversion is not available and must not be performed.

This system should answer one question everywhere:

**“Can this food safely convert between mass and volume for this use case, and how trustworthy is that conversion?”**

---

## 2. Confidence levels

### STRONG
Use when the app has an actual bridge between mass and volume.

Examples:
- `gramsPerServingUnit` exists for the chosen serving unit
- `mlPerServingUnit` exists for the chosen serving unit
- both are tied to the same serving unit, so density can be derived
- a food has an explicit density-like relationship from reliable imported/editor-confirmed data

Meaning:
- conversion is allowed
- can be used in editor, recipe math, logging, defaults
- can be persisted as food truth
- can drive preferred/default logging unit safely

### ESTIMATED
Use when the app is only approximating mass↔volume.

Primary example:
- QuickAdd fallback `1 mL ≈ 1 g`

Meaning:
- conversion may be allowed only in explicitly limited contexts
- should be visibly labeled as estimated
- must not silently harden into durable food metadata
- should not be treated as true density
- should not silently power recipe/editor persistence

### NONE
Use when there is no valid bridge.

Examples:
- food has gram basis only, no volume info
- food has volume unit label but no numeric mass/volume bridge
- count/container units with no reliable mapping

Meaning:
- no cross-basis conversion
- user must stay in available basis or provide data

---

## 3. Source-of-truth hierarchy

### Tier A — Explicit food-specific bridge
Strongest sources:
- `gramsPerServingUnit`
- `mlPerServingUnit`

Confidence:
- **STRONG**

### Tier B — Explicit user-confirmed or imported density-equivalent bridge
Confidence:
- **STRONG**

### Tier C — Fallback approximation
- `1 mL ≈ 1 g`

Confidence:
- **ESTIMATED**

### Tier D — No bridge
Confidence:
- **NONE**

---

## 4. Important distinction

A food can have:
- mass basis
- preferred volume unit
- but no bridge

This must not be conflated.

---

## 5. Proposed conceptual model

### A. BridgeConfidence
- STRONG
- ESTIMATED
- NONE

### B. BridgeSource
- EXPLICIT_SERVING_BRIDGE
- DERIVED_FROM_SERVING
- USER_CONFIRMED
- FALLBACK_EQUIV_1ML_1G
- NO_BRIDGE

### C. ConversionCapability (evaluation result)

Fields:
- confidence
- source
- allowed
- persistable
- warning level
- requires confirmation

---

## 6. Rules

### Rule 1: Same-basis is safe

### Rule 2: Cross-basis requires evaluation

### Rule 3: ESTIMATED must not act as STRONG

### Rule 4: Nutrient math anchored to mass

---

## 7. Fallback rules

### Allowed
- QuickAdd only

### Forbidden
- FoodEditor persistence
- RecipeBuilder math
- Default unit assumptions

---

## 8. Persistence rules

### Persistable
- STRONG only

### Not persistable
- ESTIMATED fallback

---

## 9. Flow rules

### QuickAdd
- STRONG: allow
- ESTIMATED: allow with label
- NONE: block

### FoodEditor
- STRONG: save
- ESTIMATED: show only, no save
- NONE: block

### RecipeBuilder
- STRONG: allow
- ESTIMATED: forbid
- NONE: block

### Logging
- STRONG: allow
- ESTIMATED: optional with warning
- NONE: block

---

## 10. Preferred unit

Preference != capability

---

## 11. UX

- STRONG: no warning
- ESTIMATED: soft warning
- NONE: blocking

---

## 12. Invariants

1. Fallback is never truth
2. Only STRONG persists
3. Math must not depend on ESTIMATED
4. Preference != bridge
5. Confidence is contextual

---

## 13. Data model additions

- BridgeConfidence
- BridgeSource

---

## 14. Strictness

QuickAdd: flexible  
Editor: strict  
Recipe: strictest  

---

## 15. Compatibility

Additive, non-breaking, evaluation-based.

---

## 16. Next step

Define decision matrix.
