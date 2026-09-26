package com.vancartier.vvcmobileagentcore.modelruntime

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ModelUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val endpoint = inputData.getString(KEY_ENDPOINT) ?: return Result.failure()
        return runCatching {
            ModelRuntime(applicationContext).synchronize(HttpModelRegistry(endpoint))
        }.fold(
            onSuccess = { result -> if (result.failed.isEmpty()) Result.success() else Result.retry() },
            onFailure = { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() }
        )
    }
    companion object { const val KEY_ENDPOINT = "model_registry_endpoint"; private const val MAX_RETRIES = 3; const val WORK_NAME = "vvc-model-registry-sync" }
}

object ModelUpdateScheduler {
    fun schedule(context: Context, endpoint: String, repeatHours: Long = 24L) {
        require(endpoint.startsWith("https://")) { "El registro debe usar HTTPS" }
        val request = PeriodicWorkRequestBuilder<ModelUpdateWorker>(repeatHours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(androidx.work.Data.Builder().putString(ModelUpdateWorker.KEY_ENDPOINT, endpoint).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            ModelUpdateWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}
