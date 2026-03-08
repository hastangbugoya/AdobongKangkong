
# Nutrient System Architecture
Developer / AI Reference Document

Timestamp: 2026-03-08

Purpose:
This document explains the **nutrient system architecture** used in the AdobongKangkong application.
It is intended for **developers and future AI sessions** working on the codebase.

The nutrient system is one of the most central and complex subsystems in the application.  
It must remain **extensible, migration-safe, and tolerant of incomplete data**.

---

# Core Design Goals

The nutrient system must support:

• Accurate nutrition calculations  
• Flexible nutrient expansion  
• Safe database migrations  
• Null‑tolerant food data  
• User‑configurable nutrient importance  

The system must allow **new nutrients to be added without redesigning the app**.

---

# Conceptual Model

The nutrient system consists of four primary layers:

```
Nutrient Catalog
        ↓
Food Nutrient Values
        ↓
Recipe Aggregation
        ↓
User Nutrient Preferences
```

Each layer serves a different responsibility.

---

# Nutrient Catalog

The nutrient catalog defines the **complete list of nutrients supported by the app**.

Examples:

Calories  
Protein  
Carbohydrates  
Fat  
Fiber  
Sodium  
Iron  
Magnesium  
Vitamin C  

Each nutrient must have a **stable internal key**.

Example conceptual structure:

```
NutrientDefinition
    key
    displayName
    unit
    category
```

Rules:

• Nutrient keys must never change once released  
• Nutrient metadata may evolve  
• New nutrients should be additive

---

# Nutrient Keys

Internal keys provide a **stable identifier** for nutrients.

Example:

```
NutrientKey.PROTEIN
NutrientKey.SODIUM
NutrientKey.IRON
```

Keys are used by:

• food nutrient values  
• recipe calculations  
• user preferences  
• analytics and trend systems

Keys must remain **stable across database versions**.

---

# Nutrient Units

Each nutrient must specify a unit.

Typical units:

g (grams)  
mg (milligrams)  
µg (micrograms)  
kcal (calories)

Units are defined in catalog metadata and must remain consistent.

---

# Food Nutrient Storage

Foods store nutrient values dynamically rather than using fixed columns.

Conceptually:

```
FoodNutrient
    foodId
    nutrientKey
    value
```

Advantages:

• allows adding new nutrients without schema changes  
• supports sparse nutrient sets  
• supports flexible import sources

Foods may contain **partial nutrient sets**.

Example:

Food A:

Protein  
Carbs  
Fat  

Food B:

Protein  
Carbs  
Fat  
Iron  

The system must tolerate missing nutrients.

---

# Missing Nutrient Data

Many foods lack complete nutrient data.

The system must handle:

• null values  
• missing nutrients  
• partial imports

Example safe calculation rule:

```
missing nutrient → treated as 0 contribution
```

Never assume:

```
food.nutrient[X] exists
```

---

# Recipe Aggregation

Recipes aggregate nutrients from ingredients.

Conceptual formula:

```
recipe nutrient = Σ(ingredient nutrient × quantity)
```

Example:

Recipe contains:

Chicken  
Rice  
Broccoli

Each ingredient contributes nutrients proportionally.

Aggregation must be:

• deterministic  
• null‑safe  
• unit‑consistent

---

# Energy Conversion Rules

Macros convert to calories using standard factors:

Protein → 4 kcal per gram  
Carbohydrates → 4 kcal per gram  
Fat → 9 kcal per gram  

These factors are used in macro distribution graphs.

---

# User Nutrient Preferences

Users control which nutrients matter to them.

Key preferences include:

Pinned nutrients  
Critical nutrients  
Target ranges (min / target / max)

Conceptual model:

```
UserNutrientPreference
    nutrientKey
    isPinned
    isCritical
    position
```

These preferences influence:

Dashboard display  
Alerts  
Trend analysis

The preference system must remain **separate from the nutrient catalog**.

---

# Target Values

Users may set target ranges for nutrients.

Example:

Protein

Minimum: 80 g  
Target: 120 g  
Maximum: 160 g

These values are used for:

Daily summaries  
Alerts  
Trend graphs

Targets must remain **independent of whether a nutrient is pinned**.

---

# Import Mapping

External food databases use their own identifiers.

Example (USDA):

Protein → ID 1003  
Energy → ID 1008

The import system maps external identifiers to internal nutrient keys.

Example:

```
USDA 1003 → NutrientKey.PROTEIN
USDA 1008 → NutrientKey.CALORIES
```

This mapping layer isolates the app from upstream changes.

---

# Nutrient Expansion

When new nutrients are added:

1. Add new NutrientKey
2. Add catalog metadata
3. Add import mapping (if available)
4. Ensure UI lists remain catalog‑driven

The UI must not assume fixed nutrient lists.

---

# Graph and Dashboard Behavior

Graphs and dashboards must render nutrients dynamically.

Display order is influenced by:

Pinned nutrients  
Critical nutrients  
User targets

The system should avoid hardcoding nutrient lists.

---

# Database Safety

The nutrient system must remain compatible with existing user data.

Rules:

• Avoid schema redesign when adding nutrients  
• Prefer metadata expansion  
• Ensure migrations are additive

---

# Testing Checklist

When modifying the nutrient system test:

Food with full nutrient set  
Food with partial nutrient set  
Food with no nutrient data  
Recipe aggregation with missing nutrients  
Pinned nutrient behavior  
Critical nutrient behavior  
Target calculations

---

# Common Pitfalls

Do NOT:

• rename nutrient keys  
• assume nutrients always exist  
• tie UI logic to specific nutrients  
• remove mappings for existing nutrients

---

# Future AI Guidance

When modifying nutrient systems:

• Maintain backward compatibility  
• Prefer catalog‑driven logic  
• Avoid schema changes when possible  
• Ensure all calculations remain null‑safe

---

End of document.
