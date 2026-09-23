package com.arny.dnshostsgenerator

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arny.dnshostsgenerator.navigation.AppNavigation
import com.arny.dnshostsgenerator.nextdns.NextDnsImportViewModel
import com.arny.dnshostsgenerator.presentation.HostsGeneratorViewModel
import com.arny.dnshostsgenerator.presentation.UiEffect
import com.arny.dnshostsgenerator.presentation.theme.AppTheme
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppContent()
        }
    }
}

@Composable
fun AppContent() {
    val hostsGeneratorViewModel: HostsGeneratorViewModel = koinViewModel()
    val hostsGeneratorState by hostsGeneratorViewModel.state.collectAsState()

    val nextDnsImportViewModel: NextDnsImportViewModel = koinViewModel()
    val nextDnsImportState by nextDnsImportViewModel.state.collectAsState()

    // Snackbar вместо платформенных уведомлений: на desktop они открывали модальное окно
    // или добавляли новую иконку в системный трей на каждое сообщение.
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        hostsGeneratorViewModel.effect.collect { effect ->
            when (effect) {
                is UiEffect.ShowToast -> launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.safeContentPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AppNavigation(
            hostsGeneratorState = hostsGeneratorState,
            onHostsGeneratorEvent = hostsGeneratorViewModel::onEvent,
            nextDnsImportState = nextDnsImportState,
            onNextDnsImportEvent = nextDnsImportViewModel::onEvent,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
