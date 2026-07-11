package com.seenslide.teacher.core.network.auth

import android.os.Build
import android.util.Log
import com.seenslide.teacher.core.network.api.IdentityApi
import com.seenslide.teacher.core.network.model.DeviceBootstrapRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anonymous-first auth bootstrap.
 *
 * Idempotent: if a token already exists in [TokenStore], does nothing. On first
 * launch (or after [TokenStore.clearAuth]), calls /api/auth/device-bootstrap
 * with a stable [DeviceIdProvider] id and persists the returned session token
 * so the existing [AuthInterceptor] starts attaching it as a Bearer.
 *
 * Designed to be fire-and-forget — UI must not block on it. Failures are
 * logged and retried on next launch; legacy magic-link / teacher-device flows
 * keep working independently.
 */
@Singleton
class IdentityBootstrapper @Inject constructor(
    private val identityApi: IdentityApi,
    private val deviceIdProvider: DeviceIdProvider,
    private val tokenStore: TokenStore,
) {
    suspend fun ensureBootstrap(): Boolean {
        if (tokenStore.getToken() != null) return true
        return try {
            val deviceId = deviceIdProvider.get()
            val resp = identityApi.deviceBootstrap(
                DeviceBootstrapRequest(
                    deviceId = deviceId,
                    deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                )
            )
            tokenStore.saveIdentity(resp.sessionToken, resp.user)
            Log.i(TAG, "device-bootstrap ok, anon=${resp.user.isAnonymous}, user=${resp.user.userId}")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "device-bootstrap failed (offline?), will retry next launch", t)
            false
        }
    }

    companion object {
        private const val TAG = "IdentityBootstrap"
    }
}
