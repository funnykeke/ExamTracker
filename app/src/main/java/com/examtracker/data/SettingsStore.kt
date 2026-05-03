package com.examtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsStore {

    private val KEY_API_KEY = stringPreferencesKey("siliconflow_api_key")

    fun getApiKey(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_API_KEY] ?: ""
        }
    }

    suspend fun getApiKeyOnce(context: Context): String {
        return context.dataStore.data.first()[KEY_API_KEY] ?: ""
    }

    suspend fun saveApiKey(context: Context, key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_KEY] = key.trim()
        }
    }

    suspend fun clearApiKey(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_API_KEY)
        }
    }
}
