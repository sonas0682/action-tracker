package com.sonas.actiontracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Series
import com.sonas.actiontracker.widget.DueWidget
import java.util.concurrent.TimeUnit

/** Keeps repeating actions filled three months ahead even when the app isn't opened for a while. */
class SeriesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Series.topUpAll(Db.get(applicationContext)) }
        runCatching { DueWidget.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SeriesWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("series", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
