package ir.alifaniani.hawklyx.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.alifaniani.hawklyx.data.repository.HawklyxRepository
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager task for periodic synchronization of the Brent Crude price.
 * Configured for the standard 15-minute interval aligned with the edge service cadence.
 *
 * NOTE on Android WorkManager execution:
 * PeriodicWorkRequest adheres to Android OS battery optimizations and Doze mode.
 * Execution may flex within the specified window rather than firing at exact millisecond boundaries.
 */
class HawklyxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = HawklyxRepository.getInstance(applicationContext)
            repository.refreshPrice(force = false)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "hawklyx_periodic_price_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Android WorkManager minimum periodic interval is 15 minutes
            val syncRequest = PeriodicWorkRequestBuilder<HawklyxSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }
    }
}
