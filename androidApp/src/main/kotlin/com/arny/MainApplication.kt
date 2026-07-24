package com.arny

import android.app.Application
import com.arny.dnshostsgenerator.data.db.setAndroidDatabaseContext
import com.arny.dnshostsgenerator.di.initKoin
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.platform.setAndroidAppContext
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setAndroidAppContext(this)
        setAndroidDatabaseContext(this)

        initKoin {
            // Передаем Android контекст в Koin
            androidContext(this@MainApplication)
        }

        AppLogger.d("MainApplication created, Koin initialized")
    }
}