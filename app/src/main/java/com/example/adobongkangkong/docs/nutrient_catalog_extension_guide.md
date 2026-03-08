
# Extending the Nutrient Catalog Safely (USDA + App Integration)

Developer / AI reference guide for AdobongKangkong

Purpose:
This document explains how to extend the nutrient catalog **without breaking USDA imports,
existing foods, recipes, or stored user data**.

This guide complements:
add_new_nutrient_procedure.md

---

# Overview

When adding a nutrient to the app, the biggest risk is **data mapping errors** or
**inconsistent nutrient identifiers across sources**.

The nutrient system must remain:

• backward compatible  
• tolerant of missing data  
• consistent across imports, recipes, and user‑entered foods  

---

# Nutrient Data Sources

Your app receives nutrient values from several places:

1. USDA FDC database
2. Branded food labels
3. User‑entered foods
4. Recipe aggregation (derived values)

Each source may use different identifiers.

Example:

USDA may represent nutrients using numeric IDs.

Example:

Energy → 1008  
Protein → 1003  

---

# Step 1 — Identify the Source Nutrient

Before adding a nutrient, determine:

• Does USDA already support it?
• What is the USDA nutrient ID?
• What unit does USDA use?

Example:

Choline → ID 1180 → mg

If USDA does not support the nutrient, the app can still support it,
but imports will rarely populate it.

---

# Step 2 — Add a Stable Internal Nutrient Key

The app must use a **stable internal key**.

Example:

NutrientKey.CHOLESTEROL  
NutrientKey.CHOLINE  
NutrientKey.MOLYBDENUM

Rules:

• Keys must never change once released  
• Keys must be unique  
• Keys should not depend on USDA naming  

---

# Step 3 — Create a Mapping Table

Map external nutrient identifiers to internal keys.

Example:

USDA_ID_TO_KEY =

1008 → CALORIES  
1003 → PROTEIN  
1180 → CHOLINE

This mapping layer isolates your app from source changes.

---

# Step 4 — Handle Missing Data

Most foods **will not contain every nutrient**.

Your system must tolerate:

null values  
missing rows  
partial nutrient sets  

Never assume:

food.nutrient[X] exists

Instead use null‑safe logic.

Example:

value ?: 0

---

# Step 5 — Recipe Aggregation

Recipes compute nutrients by summing ingredients.

When a new nutrient is introduced:

Recipes must support:

ingredient nutrient present  
ingredient nutrient missing

Missing nutrients must not crash the calculation.

Example rule:

missing nutrient = 0 contribution

---

# Step 6 — Food Editor Behavior

User‑entered foods may define any nutrient manually.

The editor should:

• allow entry of the new nutrient  
• allow blank values  
• store the value using the internal nutrient key

---

# Step 7 — Database Storage

Avoid schema changes whenever possible.

Prefer designs like:

food_nutrients table

foodId  
nutrientKey  
value

This allows new nutrients to exist without altering tables.

If a schema change is unavoidable:

• add a migration
• use default values
• never delete old columns

---

# Step 8 — UI Safety

UI components must never assume fixed nutrient lists.

Avoid:

hardcoded nutrient arrays

Prefer:

catalog‑driven lists.

Example:

for nutrient in NutrientCatalog

---

# Step 9 — Import Validation

After adding a nutrient:

Verify imports for:

USDA foods  
branded foods  
existing stored foods  

Ensure:

• no crashes
• no missing key exceptions
• old foods still load

---

# Step 10 — Backward Compatibility

Existing foods may lack the nutrient entirely.

The system must:

display missing nutrients safely  
compute totals safely  
avoid recomputation errors

---

# Testing Checklist

Test these scenarios:

USDA food contains nutrient  
USDA food missing nutrient  
Branded food contains nutrient  
Recipe aggregation includes nutrient  
Recipe aggregation missing nutrient  
User manually enters nutrient  
User leaves nutrient blank

---

# Future AI Rules

When modifying nutrient import logic:

• Never rename existing nutrient keys  
• Never remove nutrient mappings  
• Do not assume all foods contain all nutrients  
• Maintain null‑safe calculations

---

# Philosophy

Treat nutrients as **extensible metadata**.

The system should support new nutrients without:

schema redesign  
UI redesign  
data loss  

---

End of document.
