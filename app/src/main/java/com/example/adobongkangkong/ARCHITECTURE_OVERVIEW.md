
# ARCHITECTURE_OVERVIEW.md
Project: AdobongKangkong

This document describes the high level architecture of the application
so that future development and AI assistance remain safe and predictable.

----------------------------------------------------------------

ARCHITECTURE STYLE

The project follows Clean Architecture.

Layer order:

UI
↓
ViewModel
↓
UseCase (Domain)
↓
Repository
↓
DAO
↓
Room Database

Responsibilities:

UI
Displays state and sends events.

ViewModel
Coordinates UI logic and calls use cases.

UseCase
Contains business logic.

Repository
Coordinates persistence and external data.

DAO
Executes database queries.

----------------------------------------------------------------

CORE FEATURE SYSTEMS

Planner System
Templates
Recipes
Food Database
Logging

----------------------------------------------------------------

PLANNER FLOW

PlannerDayScreen
↓
PlannerDayViewModel
↓
ObservePlannedDayUseCase
↓
PlannedMealRepository
↓
Room Database

Creating a meal:

User taps +Add
↓
CreatePlannedMealUseCase
↓
PlannedMealRepository
↓
Database

Then navigation:

NavigateToPlannedMealEditor(mealId)

----------------------------------------------------------------

PLANNED MEAL EDITOR

PlannedMealEditorScreen

Actions supported:

- add food
- add recipe
- edit quantities
- remove items
- save as template
- log meal

----------------------------------------------------------------

TEMPLATE SYSTEM

Template creation:

PlannedMeal
↓
SavePlannedMealAsTemplateUseCase
↓
MealTemplateRepository
↓
MealTemplateItemRepository

Template usage:

TemplatePickerScreen
↓
CreatePlannedMealFromTemplateUseCase
↓
PlannedMealRepository
↓
PlannedItemRepository
↓
NavigateToPlannedMealEditor

----------------------------------------------------------------

PLANNER SERIES

Recurring meals handled by domain use cases.

Examples:

CreatePlannedSeriesUseCase
CreateSeriesAndEnsureHorizonUseCase
PromoteMealToSeriesAndEnsureHorizonUseCase

Series rules define:

weekday
slot
end condition

----------------------------------------------------------------

LOGGING SYSTEM

Planned meals can be logged.

PlannerDayViewModel
↓
LogPlannedMealUseCase
↓
LogRepository
↓
LogEntry

----------------------------------------------------------------

PERFORMANCE RULES

Planner must remain responsive.

Avoid:

- database calls in composables
- heavy recompositions
- blocking operations in ViewModels

Use:

Flow
collectLatest
viewModelScope

----------------------------------------------------------------

SUMMARY

AdobongKangkong architecture centers around:

Planner
Templates
Recipes
Logging
Food database

Maintaining strict layer boundaries is critical for long term stability.
