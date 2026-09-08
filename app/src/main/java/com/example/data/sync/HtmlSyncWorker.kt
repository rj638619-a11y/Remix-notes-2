package com.example.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.db.AppDatabase
import com.example.data.repository.NoteRepository
import java.util.concurrent.TimeUnit

class HtmlSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = NoteRepository(db.noteDao(), applicationContext)
            val result = repository.syncAllDeviceHtmlFiles()
            if (result.error != null) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_TAG = "html_notes_periodic_sync"

        fun schedulePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()

                val syncRequest = PeriodicWorkRequestBuilder<HtmlSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
            } catch (_: Exception) {
                // Fallback gracefully
            }
        }

        fun triggerImmediateSync(context: Context) {
            try {
                val immediateWork = OneTimeWorkRequestBuilder<HtmlSyncWorker>().build()
                WorkManager.getInstance(context).enqueue(immediateWork)
            } catch (_: Exception) {
                // Fallback gracefully
            }
        }

        fun cancelPeriodicSync(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_TAG)
            } catch (_: Exception) {
                // Fallback gracefully
            }
        }
    }
}
