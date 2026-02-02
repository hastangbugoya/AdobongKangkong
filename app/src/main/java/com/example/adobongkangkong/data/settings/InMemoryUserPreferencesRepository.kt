package com.example.adobongkangkong.data.settings

import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation used during development.
 *
 * Later this will be replaced with a DataStore-backed version
 * without changing the rest of the app.
 */
@Singleton
class InMemoryUserPreferencesRepository @Inject constructor() :
    UserPreferencesRepository {

    private val _lockPortrait = MutableStateFlow(true) // default ON for now
    override val lockPortrait: StateFlow<Boolean> = _lockPortrait
}