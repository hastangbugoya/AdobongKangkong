package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.LogRepository
import javax.inject.Inject

/**
 * DeleteLogEntryUseCase
 *
 * ## Purpose
 * Deletes a single persisted log entry by its id.
 *
 * ## Rationale
 * Log entries represent immutable snapshot records of user consumption events.
 * While logs are immutable in terms of nutrient math and historical correctness,
 * users must still be able to remove an entry entirely (e.g., accidental log, duplicate, correction).
 *
 * This use case provides a single, UI-agnostic entry point for deleting a log row.
 *
 * ## Behavior
 * - Delegates directly to [LogRepository.deleteById].
 * - Performs no validation or existence checks at this layer.
 *
 * ## Parameters
 * @param logId The primary key of the log entry to delete.
 *
 * ## Return
 * - Unit.
 *
 * ## Ordering and edges
 * - If the id does not exist, repository behavior determines the outcome
 *   (expected: no-op without crashing).
 * - This use case does not:
 *   - Recalculate totals explicitly (observers react via Flow).
 *   - Adjust planned meal state.
 *   - Emit UI events.
 *
 * Totals, day views, and summaries are expected to update reactively
 * through repository Flow observers.
 */
class DeleteLogEntryUseCase @Inject constructor(
    private val logRepository: LogRepository
) {

    suspend operator fun invoke(logId: Long) {
        logRepository.deleteById(logId)
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard use case documentation pattern:
 *   - Top KDoc: dev-facing purpose, rationale, parameters, behavior.
 *   - Bottom KDoc: guardrails for automated edits.
 *
 * - Keep this use case thin and deterministic:
 *   - No UI logic.
 *   - No side-effects beyond repository deletion.
 *   - No cascade business rules added here unless explicitly required.
 *
 * - If future requirements introduce:
 *   - Undo support → implement at ViewModel/UI layer (do not embed here).
 *   - Audit logging → wrap repository layer, not this use case.
 *   - Planned-meal relinking → implement in a higher-level orchestration use case.
 *
 * - This use case must remain safe for KMP migration:
 *   - No Android imports.
 *   - No Room references.
 */