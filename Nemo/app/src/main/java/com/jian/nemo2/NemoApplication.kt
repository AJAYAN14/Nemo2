package com.jian.nemo2

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Nemo 2.0
 * Enables Hilt dependency injection across the app
 * Configures WorkManager with HiltWorkerFactory
 */
@HiltAndroidApp
class NemoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    override fun onCreate() {
        super.onCreate()
        // Supabase is initialized via Hilt (SupabaseModule); no SDK init here.
    }
}
