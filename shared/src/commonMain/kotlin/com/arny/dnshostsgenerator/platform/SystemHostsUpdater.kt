package com.arny.dnshostsgenerator.platform

/**
 * True on platforms where the app can try to update the OS hosts file directly.
 * The actual write may still fail if the process is not elevated.
 */
expect val canUpdateSystemHosts: Boolean

expect suspend fun updateSystemHosts(
    content: String,
): SaveFileResult
