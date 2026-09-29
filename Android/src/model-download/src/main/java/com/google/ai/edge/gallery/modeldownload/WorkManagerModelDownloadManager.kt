package com.google.ai.edge.gallery.modeldownload

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Persists model downloads as unique WorkManager jobs so progress survives page and process recreation. */
class WorkManagerModelDownloadManager(context: Context) : ModelDownloadManager {
  /** Application context used for durable model paths without retaining an Activity. */
  private val appContext = context.applicationContext

  /** WorkManager instance that owns the unique background download jobs. */
  private val workManager = WorkManager.getInstance(appContext)

  override fun enqueue(artifact: ModelArtifact, accessToken: String?) {
    if (downloadedFile(artifact) != null) return
    val input =
      Data.Builder()
        .putString(Keys.ID, artifact.id)
        .putString(Keys.NAME, artifact.displayName)
        .putString(Keys.URL, artifact.url)
        .putString(Keys.FILE_NAME, artifact.fileName)
        .putString(Keys.VERSION, artifact.version)
        .putLong(Keys.TOTAL_BYTES, artifact.sizeInBytes)
        .putString(Keys.ACCESS_TOKEN, accessToken)
        .build()
    val request =
      OneTimeWorkRequestBuilder<ModelDownloadWorker>()
        .setInputData(input)
        .setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
        .addTag(workName(artifact))
        .build()
    workManager.enqueueUniqueWork(workName(artifact), ExistingWorkPolicy.KEEP, request)
  }

  override suspend fun currentState(artifact: ModelArtifact): ModelDownloadState =
    withContext(Dispatchers.IO) {
      downloadedFile(artifact)?.let { return@withContext ModelDownloadState.Completed(it) }
      val info =
        selectCurrentWorkInfo(
          workManager.getWorkInfosForUniqueWork(workName(artifact)).get()
        ) ?: return@withContext ModelDownloadState.NotDownloaded
      info.toDownloadState(artifact).let { state ->
        if (state is ModelDownloadState.Completed && !state.file.isFile) {
          ModelDownloadState.NotDownloaded
        } else {
          state
        }
      }
    }

  override fun observe(artifact: ModelArtifact): Flow<ModelDownloadState> =
    flow {
        downloadedFile(artifact)?.let {
          emit(ModelDownloadState.Completed(it))
          return@flow
        }
        while (true) {
          val info =
            selectCurrentWorkInfo(workManager.getWorkInfosForUniqueWork(workName(artifact)).get())
          val state = info?.toDownloadState(artifact) ?: ModelDownloadState.NotDownloaded
          emit(state)
          if (
            state is ModelDownloadState.Completed ||
              state is ModelDownloadState.Failed ||
              state is ModelDownloadState.Cancelled
          ) {
            return@flow
          }
          delay(250)
        }
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.IO)

  override fun cancel(artifact: ModelArtifact) {
    workManager.cancelUniqueWork(workName(artifact))
  }

  override fun downloadedFile(artifact: ModelArtifact): File? =
    destinationFile(appContext, artifact).takeIf { it.isFile }

  override fun delete(artifact: ModelArtifact): Boolean {
    cancel(artifact)
    val destination = destinationFile(appContext, artifact)
    val temporary = File(destination.parentFile, "${destination.name}.gallerytmp")
    val destinationDeleted = !destination.exists() || destination.delete()
    val temporaryDeleted = !temporary.exists() || temporary.delete()
    return destinationDeleted && temporaryDeleted
  }

  private fun WorkInfo.toDownloadState(artifact: ModelArtifact): ModelDownloadState =
    when (state) {
      WorkInfo.State.ENQUEUED,
      WorkInfo.State.BLOCKED -> ModelDownloadState.Queued
      WorkInfo.State.RUNNING ->
        ModelDownloadState.Downloading(
          receivedBytes = progress.getLong(Keys.RECEIVED_BYTES, 0L),
          totalBytes = progress.getLong(Keys.TOTAL_BYTES, artifact.sizeInBytes),
          bytesPerSecond = progress.getLong(Keys.BYTES_PER_SECOND, 0L),
        )
      WorkInfo.State.SUCCEEDED ->
        ModelDownloadState.Completed(
          outputData.getString(Keys.OUTPUT_PATH)?.let(::File)
            ?: destinationFile(appContext, artifact)
        )
      WorkInfo.State.FAILED ->
        ModelDownloadState.Failed(outputData.getString(Keys.ERROR) ?: "Model download failed.")
      WorkInfo.State.CANCELLED -> ModelDownloadState.Cancelled
    }
}

/**
 * Selects the currently meaningful job from WorkManager's history for one unique work name.
 *
 * Active work wins over terminal history. When every job is terminal, the newest list entry is
 * used; WorkManager returns unique-work generations in creation order.
 */
private fun selectCurrentWorkInfo(workInfos: List<WorkInfo>): WorkInfo? =
  workInfos.firstOrNull { !it.state.isFinished } ?: workInfos.lastOrNull()

internal fun destinationFile(context: Context, artifact: ModelArtifact): File {
  val root = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
  return File(File(File(root, safeSegment(artifact.id)), safeSegment(artifact.version)), artifact.fileName)
}

internal fun workName(artifact: ModelArtifact): String =
  "model-download:${safeSegment(artifact.id)}:${safeSegment(artifact.version)}"

internal fun safeSegment(value: String): String =
  value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "model" }

internal object Keys {
  const val ID = "model_id"
  const val NAME = "model_name"
  const val URL = "model_url"
  const val FILE_NAME = "model_file_name"
  const val VERSION = "model_version"
  const val TOTAL_BYTES = "total_bytes"
  const val ACCESS_TOKEN = "access_token"
  const val RECEIVED_BYTES = "received_bytes"
  const val BYTES_PER_SECOND = "bytes_per_second"
  const val OUTPUT_PATH = "output_path"
  const val ERROR = "error"
}
