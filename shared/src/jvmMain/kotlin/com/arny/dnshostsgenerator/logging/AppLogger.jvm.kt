package com.arny.dnshostsgenerator.logging

actual object AppLogger {
    actual fun d(message: String) {
        println("DnsHostsGenerator: $message")
    }

    actual fun e(
        message: String,
        throwable: Throwable?,
    ) {
        println("DnsHostsGenerator ERROR: $message")
        throwable?.printStackTrace()
    }
}
