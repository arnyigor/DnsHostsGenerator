package com.arny.dnshostsgenerator.logging

import android.util.Log

actual object AppLogger {
    private const val Tag = "DnsHostsGenerator"

    actual fun d(message: String) {
        Log.d(Tag, message)
    }

    actual fun e(
        message: String,
        throwable: Throwable?,
    ) {
        if (throwable == null) {
            Log.e(Tag, message)
        } else {
            Log.e(Tag, message, throwable)
        }
    }
}
