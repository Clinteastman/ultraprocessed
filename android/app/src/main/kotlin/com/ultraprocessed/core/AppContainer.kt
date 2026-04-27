package com.ultraprocessed.core

import android.content.Context

/**
 * Manual service locator. Created once in the Application and exposes
 * lazily-initialised app-wide singletons. Each subsystem (data, analyzer,
 * camera, sync) extends this container as it lands.
 */
class AppContainer(private val applicationContext: Context) {
    // Subsystems get added here as they're built:
    //   val database: AppDatabase by lazy { ... }
    //   val analyzerFactory: AnalyzerFactory by lazy { ... }
    //   val openFoodFactsClient: OpenFoodFactsClient by lazy { ... }
    //   val syncWorker: SyncWorker by lazy { ... }
}
