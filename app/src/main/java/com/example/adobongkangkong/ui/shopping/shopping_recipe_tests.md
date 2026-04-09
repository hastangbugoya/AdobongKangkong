# Planned Regression Tests

## Test Group A – Basic Math

### A1: Single Occurrence
- Input: 1 recipe, 500g needed, batch yield 500g
- Expect: 1.0 batch

### A2: Fractional Batch
- Input: 750g needed, yield 500g
- Expect: 1.5 batches

---

## Test Group B – Multiple Occurrences

### B1: Same Recipe Multiple Days
- Inputs: 250g + 500g
- Expect:
  - Totalled: 750g, 1.5 batches
  - Not totalled: 0.5 + 1.0

---

## Test Group C – Ingredient Scaling

### C1: Linear Scaling
- 1.5 batches scales all ingredients by 1.5

### C2: Precision Preservation
- Ensure no truncation before output

---

## Test Group D – Ingredient Identity

### D1: Same Ingredient Within Recipe
- Expect merge

### D2: Same Ingredient Across Recipes
- Expect NOT merged
- Expect duplicate flag true

---

## Test Group E – Edge Conditions

### E1: Missing Yield
- Expect failure or unresolved state

### E2: Mixed Units
- Compatible → merge
- Incompatible → separate

---

## Test Group F – Stability

### F1: Determinism
- Same input → same output

### F2: No UI Leakage
- No formatting logic in output

---

## Test Group G – Future Guards

### G1: Nested Recipes (if ignored)
- Ensure no accidental recursion

### G2: Identity Mismatch
- Similar names but different IDs do not merge
