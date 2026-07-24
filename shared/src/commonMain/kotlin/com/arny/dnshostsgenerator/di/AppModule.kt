package com.arny.dnshostsgenerator.di

import com.arny.dnshostsgenerator.data.db.AppDatabase
import com.arny.dnshostsgenerator.data.db.createAppDatabase
import com.arny.dnshostsgenerator.generator.HostsGenerator
import com.arny.dnshostsgenerator.presentation.HostsGeneratorViewModel
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Singletons (живут весь цикл приложения)
    single { createDnsResolver() }
    single<AppDatabase>(createdAtStart = true) { createAppDatabase() }
    single { get<AppDatabase>().groupDao() }

    // get() автоматически подставит DnsResolver, который мы зарегистрировали выше
    single { HostsGenerator(get()) }

    // Регистрация ViewModel (специфичный DSL для поддержки жизненного цикла)
    viewModel { HostsGeneratorViewModel(get(), get()) }
}