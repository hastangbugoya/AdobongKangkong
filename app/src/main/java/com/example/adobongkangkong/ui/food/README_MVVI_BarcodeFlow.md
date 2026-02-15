# FoodEditor MVVI Contract (Barcode + USDA Resolver)

**Source of truth:** 2026-02-15 (project baseline).  
This document defines the MVVI (Model–View–ViewModel–Intent) contract for the **FoodEditor** feature, focusing on the **barcode scan → USDA → mapping** flow.

> Goal: Keep UI state-driven, keep navigation as one-shot **effects**, and keep barcode mapping as a single-source-of-truth in `food_barcodes`.

---

## Architecture roles

### View (Compose Screen)
- Renders **only** from `FoodEditorState`
- Sends user actions as `FoodEditorIntent` to `FoodEditorViewModel.onIntent(...)`
- Collects `FoodEditorEffect` and performs routing/snackbar/toast actions

### ViewModel (FoodEditorViewModel)
- Owns `StateFlow<FoodEditorState>`
- Exposes `SharedFlow<FoodEditorEffect>`
- Coordinates use cases and repositories
- Does **not** perform navigation directly (emits effects)

### Domain use case (Resolver)
- A pure decision layer:
  - reads existing mapping from `FoodBarcodeRepository`
  - compares USDA freshness rules (publishedDate)
  - returns a sealed decision result
- No UI state mutations and no navigation

### Repository layer
- `FoodBarcodeRepository` is persistence-only for mappings
- `FoodRepository` persists foods
- Mapping table remains the authority: `barcode -> foodId`

---

## Invariants (must stay true)

1. **Barcode mapping is single-source-of-truth**
   - `FoodBarcodeEntity.barcode` is unique (PK)
2. **Manual foods are preserved**
   - If an existing mapping is `USER_ASSIGNED`, USDA never auto-overwrites it
3. **Prevent unnecessary USDA overwrites**
   - If existing mapping is USDA and `incoming.publishedDate <= existing.publishedDate`, skip import
4. **Navigation is not state**
   - Routing happens via `FoodEditorEffect`, not inside `FoodEditorState`

---

## State additions (minimal)

Add a 3-button collision dialog state to `FoodEditorState`:

```kotlin
data class BarcodeCollisionDialogState(
    val barcode: String,
    val existingFoodId: Long,
    val existingSource: BarcodeMappingSource,
    val incomingFdcId: Long?,          // candidate the scan implies
    val incomingLabel: String?,        // e.g. "Coke Zero (Coca-Cola)"
    val reason: CollisionReason
)

enum class CollisionReason {
    ExistingUserAssignedMapping,
    ExistingUsdaFdcIdMismatch
}
```

Add field:

```kotlin
val barcodeCollisionDialog: BarcodeCollisionDialogState? = null
```

> Keep existing `BarcodeRemapDialogState` unchanged. It is a YES/NO confirmation used for explicit user-driven remaps (e.g., assigning barcodes manually).

---

## Effects (one-shot outputs)

```kotlin
sealed interface FoodEditorEffect {
    data class NavigateToFood(val foodId: Long) : FoodEditorEffect
    data class NavigateToBarcodeAssignPicker(val barcode: String) : FoodEditorEffect
    data class Toast(val message: String) : FoodEditorEffect // optional
}
```

**Routing layer responsibility:** collect `effects` and call `navController.navigate(...)`.

---

## Intents (single input surface)

### Minimal intent set for barcode flow

```kotlin
sealed interface FoodEditorIntent {

    // Lifecycle / load
    data class Load(val foodId: Long?, val initialName: String?, val force: Boolean = false) : FoodEditorIntent

    // Scanner
    data object OpenBarcodeScanner : FoodEditorIntent
    data object CloseBarcodeScanner : FoodEditorIntent
    data class BarcodeScanned(val rawBarcode: String) : FoodEditorIntent

    // USDA picker
    data class PickUsdaCandidate(val fdcId: Long) : FoodEditorIntent
    data object DismissUsdaPicker : FoodEditorIntent

    // Collision prompt (3-button)
    data object CollisionCancel : FoodEditorIntent
    data object CollisionOpenExisting : FoodEditorIntent
    data object CollisionRemap : FoodEditorIntent

    // Fallback dialog (USDA lookup failed / blocked)
    data object DismissBarcodeFallback : FoodEditorIntent
    data class BarcodeFallbackCreateNameChanged(val name: String) : FoodEditorIntent
    data object BarcodeFallbackCreateMinimalFood : FoodEditorIntent
    data object BarcodeFallbackAssignExisting : FoodEditorIntent

    // Existing YES/NO remap confirmation
    data class ConfirmBarcodeRemap(val confirm: Boolean) : FoodEditorIntent

    // Manual barcode mapping actions
    data class AssignBarcodeToCurrentFood(val barcode: String) : FoodEditorIntent
    data class UnassignBarcode(val barcode: String) : FoodEditorIntent

    // (Optional future migration)
    // data class NameChanged(val value: String) : FoodEditorIntent
    // data class BrandChanged(val value: String) : FoodEditorIntent
}
```

---

## Resolver contract (domain decision layer)

The resolver should accept either:
- **USDA candidates** (post-search), or
- a **chosen candidate** (post-picker)

### Candidate metadata

```kotlin
data class UsdaBarcodeCandidateMeta(
    val fdcId: Long,
    val gtinUpc: String?,
    val publishedDateIso: String?, // yyyy-MM-dd
    val modifiedDateIso: String?,
    val description: String? = null,
    val brand: String? = null
)
```

### Requests

```kotlin
sealed class BarcodeResolutionRequest {
    data class ScanWithUsdaCandidates(
        val barcode: String,
        val candidates: List<UsdaBarcodeCandidateMeta>
    ) : BarcodeResolutionRequest()

    data class CandidateChosen(
        val barcode: String,
        val chosen: UsdaBarcodeCandidateMeta
    ) : BarcodeResolutionRequest()
}
```

### Results

```kotlin
sealed class ResolveBarcodeWithUsdaResult {
    data class OpenExisting(
        val barcode: String,
        val foodId: Long,
        val reason: OpenReason
    ) : ResolveBarcodeWithUsdaResult()

    data class ShowPicker(
        val barcode: String,
        val candidates: List<UsdaBarcodeCandidateMeta>,
        val reason: PickerReason
    ) : ResolveBarcodeWithUsdaResult()

    data class NeedsCollisionPrompt(
        val barcode: String,
        val existingFoodId: Long,
        val existingSource: BarcodeMappingSource,
        val incoming: UsdaBarcodeCandidateMeta?,
        val reason: CollisionReason
    ) : ResolveBarcodeWithUsdaResult()

    data class ProceedToImport(
        val barcode: String,
        val chosen: UsdaBarcodeCandidateMeta
    ) : ResolveBarcodeWithUsdaResult()

    data class Blocked(
        val barcode: String,
        val reason: String
    ) : ResolveBarcodeWithUsdaResult()
}

enum class OpenReason {
    ExistingUserAssigned,
    ExistingUsdaUpToDate,
    ExistingUsdaNoDateConservative,
    ExistingChosenByUser
}

enum class PickerReason { MultipleCandidates }

enum class CollisionReason {
    ExistingUserAssignedMapping,
    ExistingUsdaFdcIdMismatch
}
```

---

## Locked decision rules (barcode + USDA)

Given `existing = FoodBarcodeRepository.getByBarcode(barcode)`:

### 1) Existing mapping = USER_ASSIGNED
- Always return `NeedsCollisionPrompt(reason=ExistingUserAssignedMapping)`
- UI shows: **Remap / Open Existing / Cancel**
- Never auto-import or auto-remap

### 2) Existing mapping = USDA
- If candidate `fdcId` differs from `existing.usdaFdcId`:
  - return `NeedsCollisionPrompt(reason=ExistingUsdaFdcIdMismatch)`
- Else compare dates (yyyy-MM-dd):
  - If both parse and `incoming <= existing` → `OpenExisting(ExistingUsdaUpToDate)`
  - If both parse and `incoming > existing` → `ProceedToImport`
  - If either date missing/unparseable → `OpenExisting(ExistingUsdaNoDateConservative)` *(conservative policy)*

### 3) No existing mapping
- If candidates size > 1 → `ShowPicker`
- If candidates size == 1 → `ProceedToImport`
- If candidates empty → `Blocked`

---

## VM mapping: Resolver result → State/Effects

### `OpenExisting(barcode, foodId, reason)`
- `touchLastSeen(barcode)`
- emit `FoodEditorEffect.NavigateToFood(foodId)`
- clear transient USDA state (`pendingUsdaSearchJson`, `barcodePickItems`)
- close dialogs

### `ShowPicker(barcode, candidates, ...)`
- set `state.scannedBarcode = barcode`
- populate picker list UI (existing `barcodePickItems`)
- keep `pendingUsdaSearchJson` in state (VM-owned)
- no effects

### `NeedsCollisionPrompt(...)`
- set `state.barcodeCollisionDialog = ...`
- no effects

#### Collision dialog actions:
- **Cancel** → close dialog
- **Open Existing** → `touchLastSeen` + `NavigateToFood(existingFoodId)`
- **Remap** → rerun resolver with stored incoming candidate; likely yields `ProceedToImport`

### `ProceedToImport(barcode, chosen)`
- call `ImportUsdaFoodFromSearchJsonUseCase(searchJson, selectedFdcId=chosen.fdcId)`
- on success, `FoodBarcodeRepository.upsertAndTouch(source=USDA, usdaFdcId, usdaPublishedDateIso, ...)`
- emit `NavigateToFood(importedFoodId)`
- clear transient USDA state

### `Blocked(barcode, reason)`
- open fallback dialog (or set errorMessage)
- no effects

---

## Where this README lives

Recommended project placement:

```
app/src/main/java/com/example/adobongkangkong/docs/food_editor/README_MVVI_BarcodeFlow.md
```

Or if you prefer feature-local docs:

```
app/src/main/java/com/example/adobongkangkong/ui/food/editor/README_MVVI_BarcodeFlow.md
```

---

## Future migration notes

- Start by routing **only** barcode-related UI actions through `onIntent`.
- Keep other VM methods (save, nutrient edits) method-driven until you choose to migrate.
- If/when you later remove `assignBarcodeToExistingFlow`, replace it with `FoodEditorEffect.NavigateToBarcodeAssignPicker`.
