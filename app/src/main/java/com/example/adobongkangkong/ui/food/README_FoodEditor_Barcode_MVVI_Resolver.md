# FoodEditor Barcode Flow -- MVVI + Resolver Specification

**Locked Architecture Baseline -- 2026-02-15**

This document defines the complete, authoritative design for the
FoodEditor barcode + USDA import flow using MVVI
(Model--View--ViewModel--Intent).

This README is intended for onboarding future developers.\
It explains:

1.  Resolver decision table (exhaustive for CandidateChosen)
2.  Where the resolver is called (Option A -- locked)
3.  MVVI plumbing checklist
4.  VM intent execution sequences (exact order of operations)

This document is the canonical reference for barcode behavior.

------------------------------------------------------------------------

# 1. Architectural Overview

## MVVI Roles

### Model (Domain + Data)

-   FoodRepository
-   FoodBarcodeRepository
-   ImportUsdaFoodFromSearchJsonUseCase
-   SearchUsdaFoodsByBarcodeUseCase
-   ResolveBarcodeWithUsdaUseCase (new decision layer)

Responsibilities: - Persistence - USDA parsing/import - Mapping
resolution rules - No UI state changes - No navigation

------------------------------------------------------------------------

### View (Compose Screen)

-   Renders from FoodEditorState
-   Dispatches FoodEditorIntent
-   Collects FoodEditorEffect for navigation/snackbar

The View never performs logic.

------------------------------------------------------------------------

### ViewModel (FoodEditorViewModel)

-   Owns StateFlow`<FoodEditorState>`{=html}
-   Emits SharedFlow`<FoodEditorEffect>`{=html}
-   Accepts FoodEditorIntent
-   Coordinates resolver + import + repositories
-   Does NOT perform navigation directly

------------------------------------------------------------------------

### Intent

Represents user intention: - BarcodeScanned - PickUsdaCandidate -
CollisionRemap - etc.

------------------------------------------------------------------------

### Effect

One-shot outputs: - NavigateToFood(foodId) -
NavigateToBarcodeAssignPicker(barcode) - Toast(message)

Navigation must never be stored in state.

------------------------------------------------------------------------

# 2. Invariants (Non-Negotiable Rules)

1.  food_barcodes.barcode is the single source of truth.
2.  Manual foods (USER_ASSIGNED) are never auto-overwritten.
3.  USDA foods are refreshed only when incoming.publishedDate \>
    existing.publishedDate.
4.  If either published date is missing/unparseable → conservative skip
    overwrite.
5.  Navigation is handled via FoodEditorEffect only.

------------------------------------------------------------------------

# 3. Candidate Metadata (Extended)

The candidate list stored in state MUST include publishedDateIso.

``` kotlin
data class UsdaBarcodeCandidateMeta(
    val fdcId: Long,
    val gtinUpc: String?,
    val publishedDateIso: String?,  // yyyy-MM-dd
    val modifiedDateIso: String?,
    val description: String?,
    val brand: String?
)
```

This is required for freshness comparison in resolver logic.

------------------------------------------------------------------------

# 4. Resolver Decision Table (Exhaustive -- CandidateChosen)

Inputs: - existing = FoodBarcodeRepository.getByBarcode(barcode) -
incoming = UsdaBarcodeCandidateMeta

Helper values: - existing.source - existing.usdaFdcId -
existing.usdaPublishedDateIso - incoming.fdcId -
incoming.publishedDateIso

Date policy: Conservative - If either date missing/unparseable → do NOT
auto-overwrite.

------------------------------------------------------------------------

## Decision Matrix

  ------------------------------------------------------------------------------------------------------------
  Case   Existing Mapping  Source          FDC Match  Date Parsed Comparison   Result
  ------ ----------------- --------------- ---------- ----------- ------------ -------------------------------
  0      None              ---             ---        ---         ---          ProceedToImport

  1      Yes               USER_ASSIGNED   ---        ---         ---          NeedsCollisionPrompt
                                                                               (ExistingUserAssignedMapping)

  2      Yes               USDA            Mismatch   ---         ---          NeedsCollisionPrompt
                                                                               (ExistingUsdaFdcIdMismatch)

  3      Yes               USDA            Match      Both parsed incoming \<= OpenExisting (UpToDate)
                                                                  existing     

  4      Yes               USDA            Match      Both parsed incoming \>  ProceedToImport
                                                                  existing     

  5      Yes               USDA            Match      Either      ---          OpenExisting (ConservativeSkip)
                                                      missing                  
  ------------------------------------------------------------------------------------------------------------

Blocked only occurs for invalid input (blank barcode or null candidate).

------------------------------------------------------------------------

# 5. Resolver Call Location (Option A -- Locked)

Resolver is called ONLY after a candidate is chosen.

Flow:

scan → local mapping check → USDA search → user picks candidate →
resolver(CandidateChosen)

This keeps resolver deterministic and avoids implicit "best candidate"
logic.

------------------------------------------------------------------------

# 6. MVVI Plumbing Checklist

## ViewModel Must Provide

-   StateFlow`<FoodEditorState>`{=html}
-   SharedFlow`<FoodEditorEffect>`{=html}
-   fun onIntent(intent: FoodEditorIntent)

## FoodEditorState Additions

``` kotlin
val barcodeCollisionDialog: BarcodeCollisionDialogState? = null
```

## Collision Dialog Model

``` kotlin
data class BarcodeCollisionDialogState(
    val barcode: String,
    val existingFoodId: Long,
    val existingSource: BarcodeMappingSource,
    val incomingFdcId: Long?,
    val incomingLabel: String?,
    val reason: CollisionReason
)
```

------------------------------------------------------------------------

## Effects

``` kotlin
sealed interface FoodEditorEffect {
    data class NavigateToFood(val foodId: Long) : FoodEditorEffect
    data class NavigateToBarcodeAssignPicker(val barcode: String) : FoodEditorEffect
    data class Toast(val message: String) : FoodEditorEffect
}
```

------------------------------------------------------------------------

# 7. Intent Execution Sequences (Exact Order)

## Intent: BarcodeScanned(rawBarcode)

1.  Normalize barcode
2.  Close scanner
3.  Clear transient USDA state
4.  Query local mapping

If mapping exists: - USER_ASSIGNED → open collision dialog - USDA →
touchLastSeen + NavigateToFood

If no mapping: - Call SearchUsdaFoodsByBarcodeUseCase - Store
searchJson + candidate list - If one candidate → dispatch
PickUsdaCandidate

------------------------------------------------------------------------

## Intent: PickUsdaCandidate(fdcId)

1.  Read pending searchJson
2.  Build UsdaBarcodeCandidateMeta (with publishedDateIso)
3.  Call resolver(CandidateChosen)
4.  Branch on result

### ProceedToImport

-   Call ImportUsdaFoodFromSearchJsonUseCase
-   Upsert USDA mapping
-   Clear transient state
-   Emit NavigateToFood

### OpenExisting

-   touchLastSeen
-   Clear transient state
-   Emit NavigateToFood

### NeedsCollisionPrompt

-   Set barcodeCollisionDialog
-   Preserve searchJson for potential remap

### Blocked

-   Open fallback dialog

------------------------------------------------------------------------

## Intent: CollisionOpenExisting

1.  Close dialog
2.  touchLastSeen
3.  Emit NavigateToFood

------------------------------------------------------------------------

## Intent: CollisionRemap

1.  Close dialog
2.  Re-run resolver(CandidateChosen)
3.  Handle result as normal

------------------------------------------------------------------------

## Intent: CollisionCancel

1.  Close dialog
2.  No side effects

------------------------------------------------------------------------

# 8. Final Notes

This system ensures:

-   Deterministic barcode behavior
-   No accidental USDA overwrites
-   Clear separation of responsibilities
-   Fully unidirectional data flow
-   Navigation isolated from state

This document must be updated if any resolver rules change.
