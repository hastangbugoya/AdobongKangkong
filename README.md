
# AdobongKangkong

AdobongKangkong is a **nutrition tracking and meal planning Android application** designed with a strong emphasis on:

- accurate nutrition data
- extensible nutrient modeling
- local‑first architecture
- clean architecture patterns
- long‑term maintainability

The project is built to support **food logging, recipe creation, meal planning, barcode scanning, and nutrient analysis** while remaining highly extensible for future features.

---

# Core Goals

The app is designed around several guiding principles:

• Accurate nutrient tracking  
• Local‑first data storage (offline capable)  
• Extensible nutrient catalog  
• Clean architecture separation  
• Long‑term schema stability  
• Developer and AI collaboration friendliness  

---

# Tech Stack

**Language**
- Kotlin

**UI**
- Jetpack Compose

**Architecture**
- Clean Architecture

**Dependency Injection**
- Hilt

**Database**
- Room

**Background Work**
- WorkManager

**External Data**
- USDA FoodData Central API

**Barcode Scanning**
- CameraX

---

# Architecture Overview

The project follows a layered architecture:

```
domain/
data/
ui/
```

## Domain Layer

Contains:

- core models
- business rules
- use cases
- repository interfaces

Examples:

- Food models
- Recipe aggregation logic
- Planner calculations
- Nutrient computation

The domain layer **does not depend on Android APIs**.

---

## Data Layer

Responsible for:

- Room database
- DAO implementations
- repository implementations
- USDA import logic
- barcode mapping

Key responsibilities:

- persist foods
- store recipes
- maintain planner entries
- maintain nutrient preferences

---

## UI Layer

Implemented entirely using **Jetpack Compose**.

Major screens include:

- Dashboard
- Food Editor
- Planner
- Calendar
- Recipe Editor
- Shopping List
- Quick Log

ViewModels connect the UI to domain use cases.

---

# Major Features

## Food Database

Users can create foods manually or import foods from the USDA database.

Stored information includes:

- name
- serving units
- nutrient values
- barcode mappings
- optional banner images

---

## Barcode Scanning

Foods can be added quickly using barcode scanning.

Workflow:

1. Scan barcode
2. Search USDA database
3. Import food if found
4. If not found, user may create food manually

Barcode mappings are stored locally for future scans.

---

## Recipe System

Users can construct recipes composed of multiple foods.

Recipes support:

- ingredient scaling
- nutrient aggregation
- batch calculations

Recipe nutrients are computed by summing ingredient nutrients.

---

## Meal Planner

The planner allows scheduling foods and recipes across days.

Supported features:

- recurring meal series
- template meals
- meal slot organization
- shopping list generation

---

## Nutrient Tracking

The app tracks a wide range of nutrients including:

- macronutrients
- vitamins
- minerals

Users can:

- pin nutrients
- mark nutrients as critical
- set minimum / target / maximum values

---

## Dashboard

The dashboard provides a daily overview of:

- calories
- macronutrient distribution
- selected nutrient targets

The UI prioritizes nutrients that are **pinned or marked critical**.

---

# Nutrient System Design

The nutrient system is designed to be **extensible**.

Key ideas:

• nutrients are defined in a central catalog  
• foods store nutrient values dynamically  
• UI renders nutrients based on catalog metadata  
• users choose which nutrients matter to them  

This allows the app to support new nutrients without redesigning the UI.

See documentation:

```
docs/add_new_nutrient_procedure.md
docs/nutrient_catalog_extension_guide.md
```

---

# Database Design

The app uses **Room** as the persistence layer.

Important characteristics:

- migration‑safe schema changes
- stable primary keys
- separation between entities and domain models

All schema updates must include **explicit migrations**.

---

# User Preferences

Users may configure nutrient monitoring using:

- pinned nutrients
- critical nutrients
- target values

These preferences control dashboard behavior and nutrient visibility.

---

# Offline‑First Design

The app is built to function **without network access**.

Key principles:

- all foods stored locally
- recipes computed locally
- planner stored locally
- USDA imports cached locally

Network access is only required for:

- initial food import
- optional future cloud features

---

# Documentation

Detailed development documentation can be found in the `/docs` directory.

Examples:

```
docs/ai_patch_delivery_protocol.md
docs/add_new_nutrient_procedure.md
docs/nutrient_catalog_extension_guide.md
```

These guides help developers and AI assistants safely extend the project.

---

# Development Guidelines

When modifying the codebase:

• Avoid refactoring unrelated systems  
• Preserve schema compatibility  
• Follow existing repository patterns  
• Maintain null‑safe nutrient handling  

Always test migrations before release.

---

# Building the Project

Requirements:

- Android Studio
- Android SDK
- Gradle

Steps:

1. Clone the repository
2. Open the project in Android Studio
3. Allow Gradle to sync
4. Run the app on an emulator or device

---

# Future Development

Potential future features include:

- cloud backup
- multi‑device sync
- advanced nutrient analytics
- expanded food database
- improved recipe tooling

The architecture is intentionally designed to support these features.

---

# Project Philosophy

AdobongKangkong aims to balance:

- powerful nutrition tracking
- flexible data modeling
- maintainable architecture

The system is built to evolve as nutritional science and user needs change.

---

# License

Specify your project license here.

Example:

MIT License
