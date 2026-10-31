package com.arny.dnshostsgenerator.di

import com.arny.dnshostsgenerator.controld.ControlDClient
import com.arny.dnshostsgenerator.controld.ControlDImportViewModel
import com.arny.dnshostsgenerator.data.db.AppDatabase
import com.arny.dnshostsgenerator.domain.DnsChecker
import com.arny.dnshostsgenerator.domain.ProfileChecker
import com.arny.dnshostsgenerator.data.db.createAppDatabase
import com.arny.dnshostsgenerator.generator.HostsGenerator
import com.arny.dnshostsgenerator.nextdns.NextDnsClient
import com.arny.dnshostsgenerator.nextdns.NextDnsImportViewModel
import com.arny.dnshostsgenerator.presentation.HostsGeneratorViewModel
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createHttpProber
import com.arny.dnshostsgenerator.resolver.createIpValidator
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Singletons (живут весь цикл приложения)
    single { createDnsResolver() }
    single<AppDatabase>(createdAtStart = true) { createAppDatabase() }
    single { get<AppDatabase>().groupDao() }

    // get() автоматически подставит DnsResolver, который мы зарегистрировали выше
    single { HostsGenerator(get(), createIpValidator(), createHttpProber()) }
    single { DnsChecker(get(), createIpValidator()) }
    single { ProfileChecker(get(), createIpValidator(), createHttpProber()) }

    // NextDNS integration
    single { NextDnsClient() }
    single { ControlDClient() }

    // Регистрация ViewModel (специфичный DSL для поддержки жизненного цикла)
    viewModel { HostsGeneratorViewModel(get(), get(), get(), get()) }
    viewModel { NextDnsImportViewModel(get()) }
    viewModel { ControlDImportViewModel(get()) }
}