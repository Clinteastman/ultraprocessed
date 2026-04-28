package com.ultraprocessed

import android.app.Application
import com.ultraprocessed.core.AppContainer
import org.osmdroid.config.Configuration
import java.io.File

class UltraprocessedApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)

        // osmdroid setup must happen before any MapView instantiates. The
        // default cache dir is on external storage, which scoped storage
        // blocks on Android 10+; redirect both to the app's internal cache
        // so tiles can actually be written and read.
        val osmConfig = Configuration.getInstance()
        @Suppress("DEPRECATION")
        osmConfig.load(
            applicationContext,
            android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        // OSM tile servers reject empty / generic UA strings, and our debug
        // applicationId ends in ".debug" which some operators flag. Use a
        // human-readable UA so tiles always load.
        osmConfig.userAgentValue = "Ultraprocessed/${BuildConfig.VERSION_NAME} ($packageName)"
        osmConfig.osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
        osmConfig.osmdroidTileCache = File(cacheDir, "osmdroid/tiles").apply { mkdirs() }
        osmConfig.tileFileSystemCacheMaxBytes = 50L * 1024 * 1024
        osmConfig.tileFileSystemCacheTrimBytes = 40L * 1024 * 1024

        // Best-effort startup sync; no-op if backend isn't configured yet.
        container.syncCoordinator.trigger()
    }
}
