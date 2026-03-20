# Bridge Decision System

## 1. Purpose
The Bridge Decision System determines whether a unit conversion is:
- Allowed
- Trustworthy
- Persistable
- Requires user intervention

It prevents:
- Silent inaccurate math
- Estimated values becoming food truth
- Inconsistent behavior across features
- UI implying a bridge exists when it does not

---

## 2. Core Separation

### A. Source Facts
Persisted truths:
- gramsPerServingUnit
- mlPerServingUnit
- serving definitions

### B. Evaluated Capability
Contextual interpretation of conversion:
- STRONG / ESTIMATED / NONE

### C. Policy Decision
Final result per flow:
- allowed / blocked / warn / persistable

---

## 3. Unit Basis Classes
- MASS
- VOLUME
- COUNT_OR_CONTAINER
- UNKNOWN

---

## 4. Conversion Classes
- IDENTITY
- INTRA_BASIS_STANDARD
- INTRA_FOOD_SERVING_MAPPING
- CROSS_BASIS_BRIDGED
- CROSS_BASIS_ESTIMATED
- UNRESOLVABLE

---

## 5. Confidence Levels

### STRONG
- Explicit, food-backed conversion

### ESTIMATED
- Fallback (1 mL ≈ 1 g)

### NONE
- No valid conversion

---

## 6. Bridge Sources
- IDENTITY
- STANDARD_UNIT_CONVERSION
- EXPLICIT_GRAMS_PER_SERVING_UNIT
- EXPLICIT_ML_PER_SERVING_UNIT
- MATCHED_SERVING_DATA
- USER_CONFIRMED_BRIDGE
- FALLBACK_1ML_EQ_1G
- NO_BRIDGE

---

## 7. Inputs

### Food Facts
- gramsPerServingUnit
- mlPerServingUnit
- serving definitions
- unit types

### Flow
- QUICK_ADD
- FOOD_EDITOR_VIEW
- FOOD_EDITOR_SAVE
- RECIPE_BUILDER
- LOGGING
- DISPLAY_ONLY

### Action
- DISPLAY
- COMPUTE
- SAVE
- SET_PREFERRED_UNIT

---

## 8. Output (BridgeDecision)
- conversionClass
- confidence
- source
- isAllowed
- isPersistable
- warningLevel
- requiresUserAction

---

## 9. Resolution Algorithm

1. Identity → STRONG
2. Same basis → STRONG
3. Serving mapping → STRONG
4. Matched serving mass+volume → STRONG
5. User-confirmed → STRONG
6. Fallback allowed → ESTIMATED
7. Else → NONE

---

## 10. Policy by Confidence

### STRONG
- Allowed everywhere
- Persistable
- No warnings

### ESTIMATED
- QuickAdd only
- Not persistable
- Must show warning

### NONE
- Blocked
- Requires user input

---

## 11. Policy by Flow

### QuickAdd
- STRONG: yes
- ESTIMATED: yes (with label)
- NONE: no

### FoodEditor Save
- STRONG: yes
- ESTIMATED: no
- NONE: no

### RecipeBuilder
- STRONG: yes
- ESTIMATED: no
- NONE: no

---

## 12. Persistence Rules

### Allowed
- Only STRONG data

### Forbidden
- Any fallback-derived values

---

## 13. Preferred Unit Rules
- Preference != capability
- Must validate at runtime

---

## 14. Invariants
1. Fallback is never truth
2. Only STRONG persists
3. Same-basis != bridge
4. Confidence is contextual
5. UI must expose estimates
6. Recipe math cannot use fallback

---

## 15. Decision Table

| Condition | Confidence | QuickAdd | Editor Save | Recipe | Persist |
|----------|-----------|----------|-------------|--------|---------|
| Same unit | STRONG | Yes | Yes | Yes | N/A |
| Mass↔mass | STRONG | Yes | Yes | Yes | N/A |
| Volume↔volume | STRONG | Yes | Yes | Yes | N/A |
| Serving→g | STRONG | Yes | Yes | Yes | Yes |
| Serving→mL | STRONG | Yes | Yes | Yes | Yes |
| Cross-basis strong | STRONG | Yes | Yes | Yes | Yes |
| Fallback | ESTIMATED | Yes | No | No | No |
| None | NONE | No | No | No | No |

---

## 16. Test Coverage

### Categories
- Same basis
- Serving mapping
- Strong bridge
- Fallback
- None
- Mismatch data
- Persistence protection

---

## 17. Example Test

### Input
- CUP → G
- No bridge
- QuickAdd

### Output
- ESTIMATED
- Allowed
- Warning shown
- Not persistable

---

## 18. Architecture

### Phase 1: Capability
- Determine confidence + source

### Phase 2: Policy
- Apply flow rules

---

## 19. System Law

**Estimated conversions may assist the user but must never become food truth.**
