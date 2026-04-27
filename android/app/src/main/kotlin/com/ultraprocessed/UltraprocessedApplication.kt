package com.ultraprocessed

import android.app.Application
import com.ultraprocessed.core.AppContainer

class UltraprocessedApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
