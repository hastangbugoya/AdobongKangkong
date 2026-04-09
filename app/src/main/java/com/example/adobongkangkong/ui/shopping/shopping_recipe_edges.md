# Edges & Corner Cases

## 1. Same Recipe Multiple Days
- Must aggregate correctly in Totalled
- Must remain separate in Not Totalled

## 2. Same Ingredient Within Same Recipe
- Merge if same identity + compatible unit

## 3. Same Ingredient Across Recipes
- DO NOT MERGE
- Mark as duplicate only

## 4. Fractional Precision
- Keep high precision internally
- UI rounds for display only

## 5. Missing Recipe Yield
- Cannot compute batches
- Must return explicit failure / unresolved state

## 6. Mixed Units
- Merge only if compatible
- Otherwise keep separate

## 7. Count-Based Ingredients
- Support non-gram/non-mL units (e.g., eggs)

## 8. Nested Recipes (Future)
- Do not recursively expand initially

## 9. Identity Collisions
- Aggregate only by stable identity (not display name)

## 10. User Uncertainty
- Do not assume all recipes will be executed
- Avoid global aggregation
