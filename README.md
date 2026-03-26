🥬 AdobongKangkong
A modern Android nutrition, meal planning, and food tracking app built with a strong focus on architecture, data correctness, and extensibility.
This project is intentionally designed as a technical showcase of real-world mobile engineering practices:
clean architecture
complex domain modeling
deterministic data handling (nutrition scaling, unit conversions)
robust offline-first persistence
testable business logic
---
🚀 Tech Stack
🧱 Core
Kotlin
Jetpack Compose (UI)
Room (SQLite persistence with migrations)
Hilt (Dependency Injection)
WorkManager (background tasks)
📦 Data & APIs
USDA FoodData Central API
Local-first architecture with synchronized enrichment flows
Barcode ingestion + mapping system
🧪 Testing
JUnit (domain + pure logic)
Instrumented tests (I/O, DB, Android components)
---
🧠 Architecture
🧩 Clean Architecture (Strict Separation)
```
UI (Compose)
↓
ViewModel
↓
Domain (Use Cases)
↓
Repositories (Interfaces)
↓
Data Layer (Room / API / Mappers)
```
Key Principles
Single Source of Truth → canonical nutrition stored as:
`PER_100G` or `PER_100ML`
No implicit conversions (no density guessing)
Explicit bridges only:
`gramsPerServingUnit`
`mlPerServingUnit`
Deterministic transformations
Test-first domain logic
---
📊 Domain Modeling Highlights
🥗 Food Model (Unified)
Foods and recipes share the same model:
```kotlin
Food(
  servingSize,
  servingUnit,
  gramsPerServingUnit?,
  mlPerServingUnit?,
  nutrients (canonical basis)
)
```
---
⚖️ Nutrient Basis System
Basis Type	Meaning
PER_100G	Canonical mass-based
PER_100ML	Canonical volume-based
USDA_REPORTED_SERVING	Raw imported
➡️ All UI editing is scaled from canonical → per serving → back to canonical
---
🔁 Nutrient Scaling Engine
`NutrientBasisScaler`
Lossless round-trip:
canonical → UI → canonical
Avoids:
double-scaling
drift
rounding corruption
---
🔗 Bridge Confidence System
Classifies conversion reliability:
`STRONG`
`ESTIMATED`
`NONE`
Used across:
logging
recipes
quick add
UI warnings
---
🧾 CSV Import System (Advanced)
Custom importer with:
Stable hash-based IDs (idempotent imports)
Automatic:
nutrient detection
basis normalization
Duplicate handling (e.g., Copper column conflicts)
Warning system instead of hard failures
Key design:
```
1 nutrient → 1 basis only
(no dual-basis storage)
```
---
📦 Barcode System
Dedicated `FoodBarcodeEntity`
Supports:
multiple barcodes per food
packaging overrides
USDA mapping + user overrides
Collision-safe flows:
remap
adopt
merge
---
🔀 Merge System (Non-trivial)
Food deduplication system:
Soft-delete overrides
Reassign all barcodes
Merge nutrients:
canonical wins
missing values filled
Track lineage:
```kotlin
mergedIntoFoodId
mergeChildCount
```
UI reflects merge state (layered icon)
---
🍳 Recipe System
Recipes are foods
Ingredient expansion system
Supports:
per-serving scaling
batch scaling
Future-ready:
“finished food logging” mode
---
🛒 Planner + Shopping Engine
Planning
Multi-day expansion
Recurrence-aware
Override handling
Shopping Aggregation
Expands recipes → ingredients
Aggregates:
grams
ml
servings (separately)
Deterministic conversion only (no guessing)
---
⚡ UI/UX Engineering (Compose)
Patterns Used
State hoisting
Unidirectional data flow
Single source of truth (ViewModel state)
Notable UI Systems
Dynamic food editor:
basis-aware scaling
serving conversion logic
Quick Add:
synchronized inputs (grams, ml, servings)
user intent preservation
Planner:
slot-based UX
template reuse
Alphabet index scrolling (custom implementation)
---
🧪 Testing Strategy
Domain (JUnit)
Conversion correctness
Bridge capability evaluation
Nutrient scaling invariants
Instrumented
Database backup/restore
File system interactions
Content resolver flows
---
🧠 Engineering Decisions (Highlights)
❌ What is intentionally avoided
Density guessing (g ↔ mL)
Silent unit conversion
Hidden normalization
multi-basis nutrient storage
✅ What is enforced
Explicit user-provided bridges
deterministic math
reversible transformations
testable domain logic
---
🧰 Tooling & Libraries
Kotlin Coroutines / Flow
Jetpack Compose Material3
Room (with TypeConverters)
Hilt DI
WorkManager
kotlinx.serialization
---
📌 Why This Project Exists
This is not just a calorie tracker.
It is a systems-heavy mobile app designed to demonstrate:
handling messy real-world data (USDA, barcodes, user edits)
designing for correctness over convenience
building scalable domain models
writing production-grade Android architecture
---
🔮 Future Work
iOS via Kotlin Multiplatform
advanced nutrient analytics
smarter bridge estimation (optional, user-controlled)
cloud sync layer
performance tuning for large datasets
---
👤 Author
Built by an Android developer focused on:
correctness-first systems
clean architecture
long-term maintainability
