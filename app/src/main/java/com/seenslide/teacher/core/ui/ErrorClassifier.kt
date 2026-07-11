package com.seenslide.teacher.core.ui

import android.content.Context
import com.seenslide.teacher.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Maps exceptions to user-friendly localized error messages.
 * Teachers in rural Bangladesh should never see English stack traces.
 */
@Singleton
class ErrorClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Returns a user-friendly localized message for the given exception.
     */
    fun classify(e: Throwable): String {
        return context.getString(classifyToResId(e))
    }

    /**
     * Convenience: get a localized string by resource ID.
     * Useful for non-exception error messages in ViewModels that don't hold Context.
     */
    fun getString(resId: Int): String {
        return context.getString(resId)
    }

    /**
     * Returns the string resource ID for the given exception.
     */
    fun classifyToResId(e: Throwable): Int {
        return when {
            // Network connectivity issues
            e is UnknownHostException -> R.string.error_no_internet
            e is ConnectException -> R.string.error_no_internet
            e is SocketTimeoutException -> R.string.error_timeout
            e is SSLException -> R.string.error_connection_problem

            // Retrofit HTTP errors
            e is retrofit2.HttpException -> classifyHttpError(e.code())

            // Check the cause chain
            e.cause != null && e.cause !== e -> classifyToResId(e.cause!!)

            // Generic fallback
            else -> R.string.error_unknown
        }
    }

    private fun classifyHttpError(code: Int): Int {
        return when (code) {
            401, 403 -> R.string.error_auth_expired
            404 -> R.string.error_not_found
            413 -> R.string.error_file_too_large
            in 500..599 -> R.string.error_server
            else -> R.string.error_unknown
        }
    }
}
