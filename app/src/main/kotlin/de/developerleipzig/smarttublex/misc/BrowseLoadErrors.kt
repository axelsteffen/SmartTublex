package de.developerleipzig.smarttublex.misc

import android.util.Log
import de.developerleipzig.smarttublex.SmartTublexApplication
import java.io.InterruptedIOException

/** Soft-fail helpers for browse row loads cancelled by section switches. */
object BrowseLoadErrors {
    fun isCancellation(error: Throwable?): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is InterruptedIOException || current is InterruptedException) {
                return true
            }
            val message = current.message
            if (message != null && message.contains("interrupted", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun logSoftFail(message: String, error: Throwable) {
        if (isCancellation(error)) {
            Log.d(SmartTublexApplication.TAG, "$message (cancelled)")
        } else {
            Log.e(SmartTublexApplication.TAG, message, error)
        }
    }
}
