
# Adding a New Nutrient to AdobongKangkong

Developer / AI reference guide

Last updated: 2026-03-08

This document describes the **standard procedure for adding a new nutrient** to the AdobongKangkong app.

The goal is to ensure that new nutrients can be introduced **safely, consistently, and without breaking existing user data**.

---

# Overview

A nutrient addition touches multiple layers of the app:

1. Nutrient catalog (domain)
2. Import mapping (USDA / branded data)
3. Database schema (if needed)
4. User preference system (pin / critical / targets)
5. UI exposure
6. Testing and verification

Not all steps are required for every nutrient, but this checklist ensures completeness.

---

# Step 1 — Add Nutrient Key

Add the nutrient to the canonical nutrient key list.

Example:

```
enum class NutrientKey {
    CALORIES,
    PROTEIN,
    CARBS,
    FAT,
    ...
    MINERAL_X
}
```

Rules:

- Keys must be **stable**
- Never rename existing keys once released
- If a name must change, only update display metadata

---

# Step 2 — Add Catalog Metadata

Define metadata for the nutrient.

Typical fields:

- display name
- unit
- category (vitamin, mineral, macro, etc.)
- default visibility
- sort order

Example conceptual structure:

```
NutrientDefinition(
    key = NutrientKey.MINERAL_X,
    displayName = "Mineral X",
    unit = "mg",
    category = NutrientCategory.MINERAL
)
```

---

# Step 3 — Import Mapping

Map external nutrition sources to the new nutrient.

Possible sources:

- USDA FDC nutrients
- branded nutrition labels
- manual food entries
- recipe nutrient aggregation

Example mapping:

```
USDA nutrient ID → NutrientKey.MINERAL_X
```

Notes:

- Some nutrients may be **missing for many foods**
- The app must tolerate `null` values

---

# Step 4 — Database Changes (if needed)

Most nutrients **do not require database schema changes** if the app stores nutrients in a flexible table.

However if a schema update is needed:

1. Add new column or table
2. Add Room migration
3. Use safe defaults

Example:

```
ALTER TABLE nutrient_preferences
ADD COLUMN isCritical INTEGER NOT NULL DEFAULT 0
```

Rules:

- Never delete existing columns in migrations
- Always provide a default value

---

# Step 5 — User Preference Integration

Ensure the new nutrient participates in the preference system.

Users should be able to:

- pin it
- mark it critical
- set min / target / max values

Verify compatibility with:

```
UserNutrientPreference
```

Fields:

- isPinned
- isCritical
- position

---

# Step 6 — UI Exposure

Expose the nutrient where appropriate.

Possible locations:

Dashboard settings

```
[Pin]   [Critical]
```

Nutrient target editor

Macro / nutrient graphs

Food detail screen

Do NOT automatically surface new nutrients in dashboards by default.

Users should opt in.

---

# Step 7 — Data Handling

Ensure the system handles:

- foods with no value for the nutrient
- recipes missing the nutrient
- imports that omit the nutrient

Never crash or show incorrect values.

Use:

```
null-safe calculations
```

---

# Step 8 — Testing

Test the following scenarios:

1. Food contains the nutrient
2. Food does not contain the nutrient
3. Recipe aggregation includes nutrient
4. Recipe aggregation missing nutrient
5. Pinning / unpinning
6. Critical flag toggle
7. Target values set / unset

---

# Step 9 — Migration Verification

Test upgrade path from older app versions.

Verify:

- old users do not lose data
- migrations run successfully
- default values behave correctly

---

# Step 10 — UI Regression Check

Ensure adding a nutrient does not break:

- graph layouts
- dashboard cards
- food editor
- recipe calculations

---

# Recommended Design Philosophy

Nutrients should be treated as **extensible metadata**, not hardcoded UI logic.

Avoid:

- nutrient-specific UI branching
- fixed nutrient lists in UI code
- graph assumptions tied to specific nutrients

Prefer:

- catalog-driven rendering
- user preference filtering
- dynamic nutrient lists

---

# Common Pitfalls

Do NOT:

- rename released nutrient keys
- delete columns from existing tables
- assume every food has every nutrient
- automatically surface new nutrients in dashboards

---

# When to Add a New Nutrient

Examples:

- New research trend (e.g., choline, taurine)
- User demand
- USDA dataset expansion
- Branded food coverage improvement

---

# Future AI Notes

When modifying nutrient systems:

1. Never alter existing nutrient keys
2. Maintain backward compatibility with saved data
3. Avoid schema changes unless necessary
4. Follow the layered architecture used in this project

---

End of document.
