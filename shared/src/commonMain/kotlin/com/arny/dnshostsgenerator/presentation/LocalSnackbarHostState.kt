package com.arny.dnshostsgenerator.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/** Общий Snackbar приложения; null в Preview, где App() не используется. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }
