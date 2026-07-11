package com.seenslide.teacher.core.network.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seenslide.teacher.core.network.model.IdentityUserPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_PHONE = stringPreferencesKey("user_phone")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_IS_ANONYMOUS = booleanPreferencesKey("is_anonymous")
    }

    val isLoggedIn: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[KEY_AUTH_TOKEN] != null
    }

    val userEmail: Flow<String?> = context.authDataStore.data.map { prefs ->
        prefs[KEY_USER_EMAIL]
    }

    val userName: Flow<String?> = context.authDataStore.data.map { prefs ->
        prefs[KEY_USER_NAME]
    }

    // Synchronous read for interceptor — DataStore caches in memory after first read
    fun getToken(): String? = runBlocking {
        context.authDataStore.data.first()[KEY_AUTH_TOKEN]
    }

    suspend fun saveAuth(token: String, email: String, userId: String, name: String?) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_USER_ID] = userId
            if (name != null) prefs[KEY_USER_NAME] = name
        }
    }

    suspend fun clearAuth() {
        context.authDataStore.edit { it.clear() }
    }

    // ── Unified identity (migration 035) ──────────────────────────────

    val deviceId: Flow<String?> = context.authDataStore.data.map { it[KEY_DEVICE_ID] }

    val isAnonymous: Flow<Boolean> = context.authDataStore.data.map { it[KEY_IS_ANONYMOUS] ?: true }

    val userPhone: Flow<String?> = context.authDataStore.data.map { it[KEY_USER_PHONE] }

    suspend fun setDeviceId(value: String) {
        context.authDataStore.edit { it[KEY_DEVICE_ID] = value }
    }

    suspend fun saveIdentity(token: String, user: IdentityUserPayload) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
            prefs[KEY_USER_ID] = user.userId
            if (user.email != null) prefs[KEY_USER_EMAIL] = user.email else prefs.remove(KEY_USER_EMAIL)
            if (user.phoneNumber != null) prefs[KEY_USER_PHONE] = user.phoneNumber else prefs.remove(KEY_USER_PHONE)
            if (user.fullName != null) prefs[KEY_USER_NAME] = user.fullName else prefs.remove(KEY_USER_NAME)
            prefs[KEY_IS_ANONYMOUS] = user.isAnonymous
        }
    }
}
