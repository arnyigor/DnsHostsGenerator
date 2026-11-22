package com.arny.dnshostsgenerator.platform

actual fun nowEpochMillis(): Long = System.currentTimeMillis()

actual fun formatTimeOfDay(epochMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
