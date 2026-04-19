package com.seenslide.teacher.core.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Try to read the SIM phone number. Returns null if permission not granted
     * or number not available (common on Android 10+).
     */
    @Suppress("MissingPermission")
    fun getSimPhoneNumber(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val number = if (Build.VERSION.SDK_INT >= 33) {
                // On Android 13+ getLine1Number is deprecated, but still works on many devices
                tm?.line1Number
            } else {
                tm?.line1Number
            }
            number?.takeIf { it.isNotBlank() && it.length >= 6 }
        } catch (e: Exception) {
            Log.w("SimDetector", "Could not read SIM number", e)
            null
        }
    }
}
