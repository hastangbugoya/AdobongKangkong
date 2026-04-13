# AdobongKangkong iOS (SwiftUI)

This folder contains a native iOS clone of the Android app, built with Swift and SwiftUI.

## Scope

The iOS app mirrors the core modules from Android:
- Dashboard nutrition overview
- Foods catalog and editor
- Recipes builder
- Day log
- Meal planner
- Shopping aggregation
- Settings and local reset

The data model keeps deterministic nutrition behavior:
- Canonical nutrient basis (`per100g`, `per100ml`, `usdaReportedServing`)
- Explicit serving bridges (`gramsPerServingUnit`, `mlPerServingUnit`)
- No implicit density guessing for g <-> ml conversions

## Project Generation

This app uses XcodeGen for project scaffolding.

1. Install XcodeGen (if needed):
   - `brew install xcodegen`
2. Generate Xcode project from this folder:
   - `cd ios`
   - `xcodegen generate`
3. Open generated project:
   - `open AdobongKangkongIOS.xcodeproj`

## Minimum Requirements

- Xcode 15+
- iOS 17+
- Swift 5.9+
