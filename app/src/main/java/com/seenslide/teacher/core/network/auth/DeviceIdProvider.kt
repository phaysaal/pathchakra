package com.seenslide.teacher.core.network.auth

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
) {
    suspend fun get(): String {
        tokenStore.deviceId.first()?.let { return it }
        val seed = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID,
        ) ?: UUID.randomUUID().toString()
        val deviceId = "${seed}-${context.packageName}".take(64)
        tokenStore.setDeviceId(deviceId)
        return deviceId
    }
}
