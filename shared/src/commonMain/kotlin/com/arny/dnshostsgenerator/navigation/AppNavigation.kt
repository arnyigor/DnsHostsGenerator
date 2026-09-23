package com.arny.dnshostsgenerator.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arny.dnshostsgenerator.nextdns.NextDnsImportEvent
import com.arny.dnshostsgenerator.nextdns.NextDnsImportScreen
import com.arny.dnshostsgenerator.nextdns.NextDnsImportState
import com.arny.dnshostsgenerator.nextdns.NextDnsQuickImportCard
import com.arny.dnshostsgenerator.nextdns.quickImportSource
import com.arny.dnshostsgenerator.platform.isMobilePlatform
import com.arny.dnshostsgenerator.presentation.HostsGeneratorEvent
import com.arny.dnshostsgenerator.presentation.HostsGeneratorScreen
import com.arny.dnshostsgenerator.presentation.HostsGeneratorState

private object AppRoutes {
    const val HOSTS_GENERATOR = "hosts_generator"
    const val NEXT_DNS_IMPORT = "next_dns_import"
}

@Composable
fun AppNavigation(
    hostsGeneratorState: HostsGeneratorState,
    onHostsGeneratorEvent: (HostsGeneratorEvent) -> Unit,
    nextDnsImportState: NextDnsImportState,
    onNextDnsImportEvent: (NextDnsImportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOSTS_GENERATOR,
        modifier = modifier,
    ) {
        composable(AppRoutes.HOSTS_GENERATOR) {
            HostsGeneratorScreen(
                state = hostsGeneratorState,
                onEvent = onHostsGeneratorEvent,
                // На телефоне отдельный экран NextDNS не нужен: импорт встроен в главный экран.
                nextDnsQuickImport = if (isMobilePlatform) {
                    {
                        NextDnsQuickImportCard(
                            state = nextDnsImportState,
                            source = quickImportSource(hostsGeneratorState),
                            enabled = !hostsGeneratorState.isGenerating,
                            onEvent = onNextDnsImportEvent,
                        )
                    }
                } else {
                    null
                },
            )
        }

        composable(AppRoutes.NEXT_DNS_IMPORT) {
            NextDnsImportScreen(
                state = nextDnsImportState,
                onEvent = onNextDnsImportEvent,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
