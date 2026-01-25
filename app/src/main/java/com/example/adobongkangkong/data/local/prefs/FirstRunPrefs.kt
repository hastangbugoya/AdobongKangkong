package com.example.adobongkangkong.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FirstRunPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val KEY_IMPORT_DONE = booleanPreferencesKey("csv_import_done")
    }

    val importDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IMPORT_DONE] ?: false
    }

    suspend fun setImportDone(done: Boolean) {
        dataStore.edit { it[KEY_IMPORT_DONE] = done }
    }
}
