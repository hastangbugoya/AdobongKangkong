# USDA Liquid Serving → Volume Bridge Rules (AdobongKangkong)

**Status:** Locked-in rules (as of 2026-02-06)  
**Scope:** Branded USDA `/foods/search` JSON → Draft/Import → Food model + nutrient canonicalization

---

## 0) Terminology

### “Truth”
The numeric basis used for **canonical math** and **basis selection**.  
For USDA liquids, the truth commonly comes from:

- `servingSizeUnit` + `servingSize` (e.g., `MLT` + `240.0`)

### “Display”
The human-facing serving description stored in the Food record (`servingSize` + `servingUnit`) and shown in UI (per-serving), e.g.:

- `1 CUP`
- `8 FLOZ`
- `1 CAN`

### “Bridge”
A first-class conversion bridge that lets us **display** in a human unit while still being **grounded** in milliliters:

- `mlPerServingUnit: Double?` meaning **milliliters per 1 unit of servingUnit**

(Analogous to `gramsPerServingUnit` for mass-grounded foods.)

---

## 1) Core Data Model Requirements

### Food fields (existing + new)
- `servingSize: Double`
- `servingUnit: ServingUnit`
- `gramsPerServingUnit: Double?` — grams per 1 unit of `servingUnit`
- `mlPerServingUnit: Double?` — milliliters per 1 unit of `servingUnit`

### Canonical nutrient row invariant
Each nutrient row must have **exactly one** basis:
- `PER_100G` **or**
- `PER_100ML` **or**
- `USDA_REPORTED_SERVING` (transient only; must not persist after canonicalization)

---

## 2) Grounding Rules (DO NOT VIOLATE)

Compute **at most one grounding per food**.

### Mass grounding
A food is **mass-grounded** if:
- `servingUnit` is a mass unit (G / OZ / LB …) **OR**
- `gramsPerServingUnit != null`

→ mass-grounded foods canonicalize to **PER_100G**.

### Volume grounding
A food is **volume-grounded** if:
- `servingUnit` is a volume unit convertible to mL (ML / FLOZ / CUP / TBSP / TSP / L / QT …) **OR**
- `mlPerServingUnit != null`

→ volume-grounded foods canonicalize to **PER_100ML**.

### Otherwise
Food remains **USDA_REPORTED_SERVING** (not grounded).

### Critical prohibition
**Never convert grams ↔ mL** unless a future explicit density field exists.  
**Never guess density.**

---

## 3) Basis Selection (per nutrient row)

Exactly one basis per nutrient row:

- if mass-grounded → `PER_100G`
- else if volume-grounded → `PER_100ML`
- else → `USDA_REPORTED_SERVING`

---

## 4) USDA Liquids: Interpretation Strategy

### Primary source of truth
When USDA returns:

- `servingSizeUnit == "MLT"` and `servingSize == X`

Then:
- The nutrient values in `foodNutrients[].value` are **per X mL** serving.

This is the **truth** for canonicalization.

### Secondary “display hint”
`householdServingFullText` is a label/display hint such as:
- `"1 cup"`
- `"8 fl oz"`
- `"1 bottle (473 mL)"` (in other items)

---

## 5) Locked Display-vs-Truth Rule (when both exist)

When both:
- `servingSizeUnit=MLT` (truth) **and**
- `householdServingFullText` exists (display hint)

### If householdServingFullText is parseable into a supported ServingUnit:
**Household wins for display**, USDA mL wins for truth.

Store:
- `servingSize = 1`
- `servingUnit = <parsed household unit>` (CUP / FLOZ / CAN / BOTTLE / TBSP / …)
- `mlPerServingUnit = X` (USDA servingSize mL, i.e., mL per 1 unit of the chosen servingUnit)

Canonicalize nutrient rows:
- compute `PER_100ML` using X mL as the per-serving truth

### If householdServingFullText is not parseable:
Fallback to USDA numeric display (volume unit path).

Store:
- `servingSize = X`
- `servingUnit = ML` (preferred simplest stable unit)
- `mlPerServingUnit = null` (bridge redundant for ML)

Canonicalize nutrient rows:
- compute `PER_100ML` using X mL

---

## 6) Container Units (CAN/BOTTLE/etc.)

Container units are only used when USDA explicitly provides them in a parseable household string.

### Example
Household:
- `"1 can (473 mL)"`

Truth:
- `servingSize = 473 mL` (either inside parentheses or via servingSize)

Store display + bridge:
- `servingSize = 1`
- `servingUnit = CAN`
- `mlPerServingUnit = 473`

Canonicalization:
- nutrients stored as `PER_100ML` (derived from per-serving values using 473 mL)

### If container is NOT explicitly provided
Do not invent CAN/BOTTLE.
Use CUP/FLOZ if parseable, else fall back to ML.

---

## 7) Canonicalization Math for USDA Liquid Servings

Given:
- USDA provides nutrient value `A` per serving
- Serving truth is `X mL` (from servingSize)

Store canonical row amount per 100 mL:
- `A_per_100ml = A * (100 / X)`

Store as:
- `basis = PER_100ML`
- `amount = A_per_100ml`

No grams involved.

---

## 8) Editor Behavior Expectations (Volume foods)

### Liquids do not require grams
If volume-grounded:
- user is not required to enter grams
- canonical basis is `PER_100ML`

### Serving edits must preserve truth unless user changes it
If user changes `servingUnit` (e.g., ML → TBSP) **without** changing the underlying serving volume:
- update `servingSize` and/or `mlPerServingUnit` so **mL per serving remains constant**

This ensures:
- per-serving UI remains correct
- canonical rows remain stable (PER_100ML)

---

## 9) Import Fallback: No household/container description

If there is no usable household/container text:
- It is acceptable to set `servingUnit = ML` and keep `servingSize = X`
- Alternatively (optional policy), set `servingUnit = SERVING` with `servingSize = 1` and `mlPerServingUnit = X`
  - This is a UI preference decision; canonicalization remains PER_100ML either way.

---

## 10) Safety / Consistency Checks

### Must hold after import/save
- A food must not be simultaneously mass- and volume-grounded
  - i.e., do not populate both `gramsPerServingUnit` and `mlPerServingUnit` unless the servingUnit semantics truly require it (generally avoid; no density).
- Nutrient rows must not remain `USDA_REPORTED_SERVING` after canonicalization when grounding exists.
- UI displays per-serving amounts only; canonical per-100 values must not leak into UI.

---

## 11) Spelling Note
Yes — **“encompassing”** is spelled correctly.

---

## 12) AI NOTE — READ BEFORE REFACTORING (2026-02-06)

I will forget this conversation. If I touch USDA import or Food canonicalization again:

- Household text is **display** only; `servingSizeUnit=MLT` + `servingSize` is the **truth** for math.
- If household text parses cleanly: store it as `servingUnit` with `servingSize=1`, and preserve truth with `mlPerServingUnit=X`.
- If household text doesn’t parse: keep `servingUnit=ML`, `servingSize=X`, and leave `mlPerServingUnit=null`.
- Never invent CAN/BOTTLE unless USDA explicitly provides it.
- Never convert grams ↔ mL. No density guessing.
- Canonical rows for liquids are `PER_100ML` computed from per-serving values using X mL.
