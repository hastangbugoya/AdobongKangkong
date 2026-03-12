---
title: Food Merge Rules
project: AdobongKangkong
status: draft
last_updated: 2026-03-12
---

# Food Merge Rules

## Purpose

Define the rules for merging one food into another while preserving historical validity, avoiding duplicate nutrition identities, and supporting future barcode/package consolidation.

## Terms

### Default food
The canonical food that survives as the preferred identity after merge.

### Override food
The food being merged into the default food. It is retained for historical integrity but retired from normal use.

### Merge
A forward-looking consolidation operation. It does **not** rewrite historical logs to point to a different food id.

## Core principles

1. **History must remain valid.** Existing logs, snapshots, and references must keep their original `foodId`.
2. **Future usage should converge.** Future barcode resolution and normal food selection should prefer the default food.
3. **Nutrition identity wins over packaging identity.** Packaging differences alone are not sufficient reason to keep separate foods long-term.
4. **Silent nutrition corruption is forbidden.** Merge is allowed only when nutrient differences are negligible or manually accepted.
5. **Do not physically delete a referenced food as part of merge.** The override food should be retired, hidden, or marked merged rather than destroyed.

## Merge eligibility rules

A merge may proceed only when:
- the user explicitly selects the default and override food
- both foods are active rows
- the foods are different rows
- the override food is not already merged into another food

Block or warn strongly when:
- calories/macros differ materially
- sodium/sugars differ materially
- serving basis conflicts materially
- the foods are clearly different formulations
- one is recipe-like and the other is packaged food

## Nutrient merge rules

Comparison basis:
1. compare by `nutrientId`
2. if needed, compare by nutrient `code`

Default rule:
- if default already has a nutrient row, keep the default row
- if default does not have that nutrient row, copy it from override

Difference tolerance rule:
- negligible difference -> keep default row unchanged
- non-negligible difference -> block or require explicit confirmation

Recommended negligible thresholds:
- calories: within 1 kcal
- grams nutrients: within 0.1 g
- mg nutrients: within 5 mg
- mcg nutrients: within 10 mcg
- percent difference under 5%

Zero vs missing:
- missing nutrient row != explicit zero
- if default is missing and override has explicit zero, copy it
- never remove a nutrient row from the default food during merge

## Barcode merge rules

General rule:
All barcode mappings from the override food should move to the default food unless collision handling says otherwise.

For each `FoodBarcodeEntity` linked to the override food:
- set `foodId = defaultFoodId`
- preserve:
  - `barcode`
  - `source`
  - USDA metadata
  - package override fields
  - timestamps

Package override retention:
Keep barcode-specific package metadata:
- `overrideServingsPerPackage`
- `overrideHouseholdServingText`
- `overrideServingSize`
- `overrideServingUnit`

Barcode collision rule:
- if the barcode already exists on the default food, keep one row
- prefer the richer row
- if both rows contain distinct useful package metadata, require explicit conflict resolution

## Servings-per-package rules

Canonical meaning:
- `Food.servingsPerPackage` = default/fallback
- `FoodBarcodeEntity.overrideServingsPerPackage` = package-specific override

After merge:
- keep `defaultFood.servingsPerPackage` unchanged unless user explicitly updates it
- transfer barcode-level overrides with the barcode rows
- do not average multiple package counts into one food-level value

Resolution priority:
1. `FoodBarcodeEntity.overrideServingsPerPackage`
2. `Food.servingsPerPackage`

## Serving and household metadata rules

Keep on `Food` as canonical defaults:
- `servingSize`
- `servingUnit`
- `gramsPerServingUnit`
- `mlPerServingUnit`

Keep on barcode rows as package context:
- `overrideHouseholdServingText`
- `overrideServingSize`
- `overrideServingUnit`

Do not automatically rewrite food-level bridges during merge.

## Historical validity rules

Logs:
- existing logs must keep their original `foodId`
- do not rewrite old log rows to point to the default food

Snapshots:
- historical nutrition snapshots remain untouched

Planner / future scheduling:
- future unresolved references may later be redirected if desired
- past completed records remain unchanged

Recipes / batch references:
- do not mass-rewrite automatically in the initial merge implementation

## Override food post-merge rules

The override food must be retained if referenced.

Recommended post-merge state:
- hidden or retired
- optionally `mergedIntoFoodId = defaultFoodId`

Read behavior:
- opening the retired food should show it as merged/retired and point to the default food

Edit behavior:
- prefer preventing edits on the retired override food
- direct editing to the default food instead

## Search and picker rules after merge

- normal food lists/search should exclude retired merged foods by default
- debug/recovery mode may optionally show them
- barcode scan should resolve to the default food after barcode transfer

## Merge transaction rules

A merge must be transactional.

A single transaction should cover:
1. load and validate both foods
2. compare nutrients
3. merge missing nutrients into default food
4. move barcode rows to default food
5. retire or mark override food as merged
6. commit only if all steps succeed

If any step fails, rollback everything.

## Suggested user flow

1. User selects two foods
2. User chooses default and override
3. App shows preview:
   - nutrient differences
   - nutrients that will be copied
   - barcode rows that will be reassigned
   - conflicts
4. User confirms
5. Merge runs transactionally
6. App reports counts for nutrients copied, barcodes moved, and conflicts

## Merge preview rules

Show:
- overlapping nutrients
- missing nutrients that will be copied
- conflicting nutrients beyond tolerance
- barcodes moving from override to default
- any barcode collisions
- a reminder that old logs remain on the old food id

## Non-goals for initial implementation

Do not try to:
- rewrite old logs
- rewrite historical snapshots
- rewrite every recipe reference automatically
- rewrite planner history automatically
- average nutrient rows
- auto-delete referenced foods

## Recommended schema support

Strongly recommended:
- `foods.mergedIntoFoodId: Long?`
or equivalent retired/merged markers.

## Final merge contract

Inputs:
- `defaultFoodId`
- `overrideFoodId`
- optional tolerance config
- optional conflict resolution flags

Outputs:
- default food id
- override food id retained but retired
- counts for nutrients copied, barcodes moved, and conflicts

Guaranteed outcomes:
- historical rows remain valid
- future barcode resolution converges on default food
- default food gains missing nutrients from override
- barcode package metadata is preserved

## Policy summary

Keep:
- old logs on old food id
- override food row for history
- package overrides on barcode rows

Move:
- barcode mappings to default food
- missing nutrient rows to default food

Do not:
- rewrite history
- auto-delete referenced food
- silently merge materially different nutrients
