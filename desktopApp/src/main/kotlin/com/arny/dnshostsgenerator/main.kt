package com.arny.dnshostsgenerator

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arny.dnshostsgenerator.di.initKoin

fun main() = application {
    initKoin()
    Window(
        onCloseRequest = ::exitApplication,
        title = "DnsHostsGenerator",
    ) {
        App()
    }
}