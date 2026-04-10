# Store-Specific Food Pricing (Normalized Model) – Design Plan

## Overview
This document defines the design for adding **store-specific food pricing** to AdobongKangkong (AK).

The system will:
- Accept flexible user input (package, serving, grams, mL)
- Normalize all prices to a canonical form
- Store only normalized values for comparison and future features

---

## Core Design Philosophy

### 1. Input is flexible
User can enter:
- Price per package/container
- Price per serving
- Price per gram/mL (implicitly via serving or package)

### 2. Storage is strict
App stores ONLY:
- `pricePer100g` OR
- `pricePer100ml`

Never both.

### 3. No density guessing
- Mass ↔ volume conversion is NEVER inferred
- Requires explicit bridge

---

## Data Model

### Table: FoodStorePriceEntity

Fields:
- `id` (PK)
- `foodId` (FK)
- `storeId` (nullable FK)
- `pricePer100g` (nullable)
- `pricePer100ml` (nullable)
- `updatedAtEpochMs`

### Invariant:
Exactly ONE of:
- `pricePer100g`
- `pricePer100ml`

must be non-null.

---

## Entry Modes

### 1. Package-Based Entry

User inputs:
- Package price
- Total grams OR mL

Normalization:
pricePer100 = (price / totalQuantity) * 100

---

### 2. Serving-Based Entry

User inputs:
- Price per serving

Requires:
- Valid serving → grams OR mL bridge

Normalization:
pricePer100 = (pricePerServing / gramsPerServing) * 100
OR
pricePer100 = (pricePerServing / mlPerServing) * 100

---

### 3. Direct Entry (optional future)
- User enters price per 100g/mL directly

---

## Validation Rules

### Must have:
- Valid numeric price > 0

### Must resolve to:
- grams OR mL

### Reject if:
- No bridge available
- Invalid or zero quantity
- Both price columns would be set

---

## Repository Rules

### Save Behavior
- Compute normalized value
- Set only one column
- Clear the other column

### Update Behavior
- Replace existing (foodId, storeId) row
- Update timestamp

---

## UI Flow (Future)

### Bottom Sheet

Inputs:
- Store dropdown
- Price input
- Entry mode selector:
  - Package
  - Serving

Conditional inputs:
- Package mode → quantity + unit
- Serving mode → uses existing serving definition

Outputs:
- Show normalized result preview

---

## Migration Plan (Future)

### Current State
- estimatedPrice

### New State
Replace with:
- pricePer100g
- pricePer100ml
- updatedAtEpochMs

### Migration Strategy
- Add new nullable columns
- Leave old column unused or drop later

---

## Future Extensions

### 1. Price History
- Separate table with timestamps

### 2. Min/Max Tracking
- Aggregate queries

### 3. Shopping Optimization
- Cheapest store per food

### 4. Planner Cost Estimation
- Use normalized price

---

## Key Guardrails

- Never store raw package price long-term
- Never guess density
- Always normalize before persistence
- Exactly one canonical column populated

---

## Summary

This system:
- Keeps storage clean
- Supports flexible input
- Enables future comparison features
- Aligns with AK nutrition normalization philosophy
