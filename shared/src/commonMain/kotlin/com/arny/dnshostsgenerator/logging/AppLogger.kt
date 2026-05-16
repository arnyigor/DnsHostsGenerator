package com.arny.dnshostsgenerator.logging

expect object AppLogger {
    fun d(message: String)

    fun e(
        message: String,
        throwable: Throwable? = null,
    )
}
