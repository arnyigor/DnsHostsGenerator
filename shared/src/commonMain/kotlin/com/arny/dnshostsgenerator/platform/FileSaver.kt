package com.arny.dnshostsgenerator.platform

data class SaveFileResult(
    val success: Boolean,
    val message: String,
)

expect suspend fun saveTextFile(
    fileName: String,
    content: String,
): SaveFileResult
