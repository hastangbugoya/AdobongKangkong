# Requirements

## Functional Requirements

### R1: Horizon Aggregation
- Collect all planned recipe occurrences within next N days

### R2: Yield Normalization
- Convert each occurrence into required finished yield (grams or mL)

### R3: Batch Computation
- batchesRequired = totalRequiredYield / recipeBatchYield
- Must support fractional batches (e.g., 1.37)

### R4: Ingredient Scaling
- ingredientAmount = baseAmount * batchesRequired
- No rounding in computation layer

### R5: Per-Recipe Grouping
- Ingredients remain scoped to their recipe
- No cross-recipe merging

### R6: Duplicate Ingredient Detection
- If same ingredient appears in multiple recipes:
  - Mark with duplicate indicator (layered icon in UI)

### R7: Per-Occurrence Output
- Each planned occurrence retains:
  - required yield
  - fractional batch

### R8: Per-Recipe Total Output
- Combine occurrences into:
  - total yield
  - total batches

---

## Non-Functional Requirements

- Deterministic
- Pure or near-pure (minimal side effects)
- Fully unit testable
- No UI dependencies
