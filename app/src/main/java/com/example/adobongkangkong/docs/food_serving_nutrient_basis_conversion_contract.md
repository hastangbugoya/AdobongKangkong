# Food Serving & Nutrient Basis Conversion Contract

**Scope:** This contract defines the *one true set of rules* for interpreting and converting food nutrition data using only these three fields:

```kotlin
val servingSize: Double
val servingUnit: ServingUnit
val gramsPerServingUnit: Double?
```

It applies to:
- Food Editor saves (create/edit)
- CSV import saves
- Any future repair / emergency cleanup jobs
- Logging math (per-serving totals, grams-based totals)
- Display math (kcal per serving)

It is intentionally written to minimize unit-conversion bugs by:
- clearly defining meanings
- choosing a single canonical basis where possible
- forbidding silent “basis relabel without conversion”
- centralizing grounding logic

---

## 1) Definitions

### 1.1 Canonical basis (apples-to-apples)
- **PER_100G** is the canonical basis used for comparisons, sorting, and nutrient math whenever the food can be grounded in grams.
- **USDA_REPORTED_SERVING** is used when the food cannot be grounded in grams (no safe conversion).

> A food uses exactly **one basis row per nutrient**.

### 1.2 Serving definition (display reference)
The fields `servingSize`, `servingUnit`, and `gramsPerServingUnit` define the **serving reference** used by UI and logging. These may change over time. When canonical PER_100G exists, the serving definition is treated as a **display/logging reference**; it does not change the underlying food composition unless the user edits nutrient values.

### 1.3 Meaning of `gramsPerServingUnit`
**`gramsPerServingUnit` means: grams per *1 unit* of `servingUnit`.**

Examples:
- `servingUnit = CUP`, `gramsPerServingUnit = 250` → 1 cup = 250 g
- `servingUnit = BUNCH`, `gramsPerServingUnit = 125` → 1 bunch = 125 g
- `servingUnit = OZ`, `gramsPerServingUnit = 28.3495` → 1 oz = 28.3495 g

Then:
```kotlin
gramsPerServing = servingSize * gramsPerServingUnit
```

(For mass units, grams can be computed directly; see below.)

---

## 2) ServingUnit categories

### 2.1 Mass units (deterministic)
Examples: `G`, `OZ`, `LB`

- Grams can be computed from `servingSize + servingUnit` without extra info.

### 2.2 Volume units (require density)
Examples: `CUP`, `TBSP`, `TSP`, `FLOZ`, `ML`

- Volume → grams is unsafe without density.
- Conversion requires `gramsPerServingUnit`.
- **FLOZ is volume; OZ is mass. They are not interchangeable.**

### 2.3 Custom / count-like units
Examples: `BUNCH`, `BOX`, `CAN`, `PIECE`

- Not convertible to grams unless `gramsPerServingUnit` is provided.

---

## 3) Grounding rules

A food is **grounded** if we can compute a positive `gramsPerServing`.

### 3.1 Compute `gramsPerServing`

**Mass unit**
```kotlin
gramsPerServing = servingUnit.toGrams(servingSize)
```

**Volume or custom unit**
```kotlin
gramsPerServing = servingSize * gramsPerServingUnit
```
Valid only if `gramsPerServingUnit != null && > 0`.

**Not grounded**
- If neither case yields a positive value, the food is not grounded.

### 3.2 Lock-in rule for mass units (optional)
If `servingUnit` is mass and `gramsPerServingUnit` is null, on save we may set:
```kotlin
gramsPerServingUnit = servingUnit.toGrams(1.0)
```
- Never overwrite a user-provided value.

---

## 4) Basis selection rules

### 4.1 Grounded in grams
If `gramsPerServing != null`:
- basis = **PER_100G**
- nutrient values must be stored per 100 g

### 4.2 Not grounded
If `gramsPerServing == null`:
- basis = **USDA_REPORTED_SERVING**
- nutrient values are stored per serving

### 4.3 One basis row per nutrient
- Exactly one row per `nutrientId`.
- No mixed-basis duplicates.

---

## 5) Conversion rules (critical)

### 5.1 Forbidden: basis relabel without conversion
If UI values are per serving and basis changes to PER_100G, the numbers **must be converted**.

### 5.2 Per-serving → PER_100G
```kotlin
A_per100g = A_serving * (100 / gramsPerServing)
```

### 5.3 PER_100G → per-serving (display/logging)
```kotlin
A_serving = A_per100g * (gramsPerServing / 100)
```

### 5.4 Serving edits do not mutate PER_100G
When PER_100G exists, changing serving fields does **not** change canonical values unless the user edits nutrients.

---

## 6) Serving unit change behavior

### 6.1 Mass → mass
- PER_100G stays unchanged.
- Per-serving display/log totals update via new gramsPerServing.

### 6.2 Volume/custom → volume/custom
- If grounded: PER_100G stays unchanged.
- If grounding is removed: basis must fall back to USDA.

### 6.3 Trust-user relabeling
Changing custom labels (e.g., CAN → TBSP) without changing `gramsPerServingUnit` is accepted as intentional redefinition.

---

## 7) Save-time responsibilities

On save (CSV or Editor):
1. Determine grounding.
2. Choose basis.
3. Convert nutrient values to that basis.
4. Deduplicate to one row per nutrient.
5. Persist via `replaceForFood(foodId, rows)`.

---

## 8) Logging rules

- Use PER_100G when available.
- Compute totals with grams:
```kotlin
total = per100gAmount * (loggedGrams / 100)
```

---

## 9) UI rules

- Editor shows nutrients per serving.
- List rows show calories per serving.
- Sorting/comparison uses PER_100G.
- UI never performs basis conversion.

---

## 10) Common pitfalls

- Treating `gramsPerServingUnit` as grams for the whole serving.
- Forgetting `servingSize * gramsPerServingUnit`.
- Guessing density for volume units.
- Relabeling basis without converting values.
- Repeated round-trip scaling