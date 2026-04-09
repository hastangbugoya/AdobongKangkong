# Shopping Recipe Computation – Overview

## Purpose
Define a deterministic, testable system for computing recipe-driven shopping requirements over a planning horizon (next N days).

## Core Principle
Recipes are **demand generators**, not shopping items.

The system computes:
- Total required finished recipe yield
- Exact fractional batch requirements (X.Y)
- Ingredient requirements scaled linearly from batch count

The app **does not**:
- Round batch counts
- Recommend batch counts
- Merge ingredients across recipes

## Key Outputs
- Per-recipe total demand (Totalled view)
- Per-occurrence demand (Not totalled view)
- Ingredient requirements scoped to each recipe
- Duplicate ingredient markers across recipes

## Separation of Concerns
1. Planner Data Collection (existing)
2. Recipe Shopping Computation (NEW USE CASE)
3. UI Mapping (existing / flexible)
