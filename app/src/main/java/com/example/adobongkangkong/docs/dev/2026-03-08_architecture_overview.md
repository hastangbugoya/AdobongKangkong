
# AdobongKangkong Architecture Overview
Developer / AI Reference Document

Timestamp: 2026-03-08

Purpose:
This document provides a **system-level architecture overview** of the AdobongKangkong project.
It is intended for **developers and future AI sessions** working on the codebase.

This document intentionally focuses on **data models, domain logic, and extensibility**
rather than UI details, since the UI layer is expected to evolve.

---

# Core Philosophy

The project is built around several guiding principles:

• Local‑first architecture  
• Accurate nutrition modeling  
• Extensible nutrient catalog  
• Clean architecture separation  
• Migration-safe database evolution  
• Long-term maintainability  

The goal is to ensure the app can evolve **without breaking existing user data or logic**.

---

# High-Level Architecture

The application follows a layered architecture.

```
UI Layer (Compose)
        ↓
ViewModels
        ↓
Domain Layer (Use Cases)
        ↓
Repository Interfaces
        ↓
Data Layer (Room / External Sources)
```

UI is intentionally **thin** and mostly orchestrates state.

Most important logic lives in the **Domain layer**.

---

# Domain Layer

The domain layer defines:

• core models  
• business rules  
• calculation logic  
• use cases  

It must remain **independent of Android framework APIs**.

Examples of domain responsibilities:

Food nutrition computation  
Recipe nutrient aggregation  
Planner calculations  
Nutrient target evaluation  
Trend / dashboard summaries  

Domain classes should be stable over time.

---

# Data Layer

The data layer is responsible for:

• database persistence (Room)  
• DAO implementations  
• repository implementations  
• import pipelines (USDA)  
• barcode mapping  

This layer converts between:

Database entities ↔ domain models

It is also responsible for **schema migrations**.

---

# Database Philosophy

The database must support **long-lived user data**.

Rules:

• Never delete columns in migrations  
• Prefer additive schema changes  
• Always provide default values  
• Test migrations against older versions  

Example safe migration:

```
ALTER TABLE nutrient_preferences
ADD COLUMN isCritical INTEGER NOT NULL DEFAULT 0
```

---

# Nutrient System

The nutrient system is intentionally **catalog-driven**.

Conceptually:

```
Nutrient Catalog
        ↓
Foods store nutrient values
        ↓
Recipes aggregate nutrients
        ↓
User preferences determine visibility
```

Key properties:

• nutrients are defined centrally  
• foods store nutrient values dynamically  
• UI renders nutrients from catalog metadata  

This allows new nutrients to be added **without redesigning the system**.

---

# Nutrient Preferences

Users control nutrient importance through preferences.

Key flags include:

• pinned nutrients  
• critical nutrients  
• target ranges  

These preferences determine:

Dashboard visibility  
Warning priority  
Trend monitoring

The preference system must remain **separate from nutrient definitions**.

---

# Food Model

Foods are the atomic nutrition source.

A food typically contains:

name  
serving units  
nutrient values  
optional barcode  
optional image/banner  

Foods may originate from:

• USDA import  
• barcode lookup  
• manual entry  

Food nutrient values should be stored in a **flexible structure**
that allows adding nutrients without schema redesign.

---

# Recipe System

Recipes aggregate nutrients from ingredients.

Conceptually:

```
Recipe
   ├─ Ingredient (Food + quantity)
   ├─ Ingredient (Food + quantity)
   └─ Ingredient (Food + quantity)
```

Recipe nutrients are computed by:

```
sum(ingredient_nutrients × quantity)
```

Important properties:

• missing nutrients must be tolerated  
• aggregation must remain null-safe  
• scaling must remain deterministic  

---

# Planner System

The planner schedules foods and recipes across time.

Core concepts:

PlannedDay  
PlannedMeal  
PlannedItem  

The planner supports:

• daily meal scheduling  
• recurring series  
• meal templates  
• shopping list generation  

Planner data must remain stable because users may store
large amounts of historical planning data.

---

# Barcode System

Barcode scanning allows rapid food entry.

Typical flow:

1. Scan barcode
2. Lookup mapping
3. Query USDA database
4. Import food if available
5. Store mapping locally

Barcode mappings are cached locally to improve repeat scans.

Future analytics may track **unknown barcodes** to improve coverage.

---

# Import System

External food sources must be mapped into internal models.

Example pipeline:

```
USDA JSON
   ↓
Import Mapper
   ↓
Domain Food Model
   ↓
Database Entity
```

Mapping layers isolate the app from upstream changes.

---

# Offline-First Strategy

The system is designed to function **without network connectivity**.

Key rules:

• all foods stored locally  
• recipes computed locally  
• planner stored locally  
• nutrient calculations performed locally  

Network access is only required for:

USDA imports  
future cloud features

---

# Extensibility Strategy

The architecture intentionally allows expansion in several areas:

New nutrients  
New food data sources  
Additional analytics  
Cloud synchronization  
Advanced trend analysis

The key strategy is to keep **domain models stable** while
allowing metadata-driven expansion.

---

# Future AI Notes

When modifying the project:

• avoid altering domain invariants  
• preserve database migration safety  
• prefer additive system changes  
• do not couple logic to specific nutrients  

The system should remain **data-driven and extensible**.

---

End of document.
