package com.seenslide.teacher.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pinDataStore by preferencesDataStore(name = "pin_store")

@Singleton
class PinStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PHONE = stringPreferencesKey("phone_number")
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun savePin(pin: String) {
        context.pinDataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hashPin(pin)
        }
    }

    suspend fun savePhone(phone: String) {
        context.pinDataStore.edit { prefs ->
            prefs[KEY_PHONE] = phone
        }
    }

    fun getStoredPinHash(): String? = runBlocking {
        context.pinDataStore.data.first()[KEY_PIN_HASH]
    }

    fun getStoredPhone(): String? = runBlocking {
        context.pinDataStore.data.first()[KEY_PHONE]
    }

    fun verifyPin(pin: String): Boolean {
        val stored = getStoredPinHash() ?: return false
        return hashPin(pin) == stored
    }

    fun isRegistered(): Boolean = getStoredPinHash() != null

    suspend fun clear() {
        context.pinDataStore.edit { it.clear() }
    }
}
