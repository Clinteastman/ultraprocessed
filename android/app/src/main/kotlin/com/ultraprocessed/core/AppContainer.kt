package com.ultraprocessed.core

import android.content.Context
import com.ultraprocessed.analyzer.AnalyzerFactory
import com.ultraprocessed.data.AppDatabase
import com.ultraprocessed.openfoodfacts.OpenFoodFactsClient
import com.ultraprocessed.data.repository.ConsumptionRepository
import com.ultraprocessed.data.repository.FastingRepository
import com.ultraprocessed.data.repository.FoodRepository
import com.ultraprocessed.data.settings.SecretStore
import com.ultraprocessed.data.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual service locator. Created once in the Application and exposes
 * lazily-initialised app-wide singletons. Avoids the boilerplate of a DI
 * framework while keeping wiring explicit.
 */
class AppContainer(applicationContext: Context) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient: HttpClient by lazy { Http.create() }

    val database: AppDatabase by lazy { AppDatabase.build(applicationContext) }

    val foodRepository: FoodRepository by lazy { FoodRepository(database.foodEntryDao()) }
    val consumptionRepository: ConsumptionRepository by lazy {
        ConsumptionRepository(database.consumptionLogDao())
    }
    val fastingRepository: FastingRepository by lazy {
        FastingRepository(database.fastingProfileDao())
    }

    val settings: Settings by lazy { Settings(applicationContext) }
    val secrets: SecretStore by lazy { SecretStore(applicationContext) }

    val analyzerFactory: AnalyzerFactory by lazy {
        AnalyzerFactory(settings, secrets, httpClient)
    }

    val openFoodFactsClient: OpenFoodFactsClient by lazy {
        OpenFoodFactsClient(client = httpClient)
    }

    val syncCoordinator: com.ultraprocessed.sync.SyncCoordinator by lazy {
        com.ultraprocessed.sync.SyncCoordinator(
            settings = settings,
            secrets = secrets,
            httpClient = httpClient,
            database = database,
            foodRepository = foodRepository,
            consumptionRepository = consumptionRepository,
            scope = applicationScope
        )
    }
}
