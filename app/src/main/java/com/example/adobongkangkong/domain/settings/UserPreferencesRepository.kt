package com.example.adobongkangkong.domain.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * User preferences that affect global app behavior.
 *
 * This is intentionally abstract so the backing implementation
 * can later move to DataStore without changing consumers.
 */
interface UserPreferencesRepository {

    /** Whether the app should be locked to portrait orientation. */
    val lockPortrait: StateFlow<Boolean>
}
