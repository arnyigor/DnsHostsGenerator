package com.arny.dnshostsgenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arny.dnshostsgenerator.navigation.AppNavigation
import com.arny.dnshostsgenerator.nextdns.NextDnsImportViewModel
import com.arny.dnshostsgenerator.platform.Notify
import com.arny.dnshostsgenerator.presentation.HostsGeneratorViewModel
import com.arny.dnshostsgenerator.presentation.UiEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
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

    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        hostsGeneratorViewModel.effect.collect { effect ->
            when (effect) {
                is UiEffect.ShowToast -> toastMessage = effect.message
            }
        }
    }

    toastMessage?.let { message ->
        Notify(message)
        LaunchedEffect(message) {
            toastMessage = null
        }
    }

    AppNavigation(
        hostsGeneratorState = hostsGeneratorState,
        onHostsGeneratorEvent = hostsGeneratorViewModel::onEvent,
        nextDnsImportState = nextDnsImportState,
        onNextDnsImportEvent = nextDnsImportViewModel::onEvent,
    )
}
