package com.google.ai.edge.gallery.modeldownload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ModelDownloadWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      try {
        val artifact = readArtifact()
        val destination = destinationFile(applicationContext, artifact)
        destination.parentFile?.mkdirs()
        if (destination.isFile) {
          return@withContext success(destination)
        }

        setForeground(foregroundInfo(artifact.displayName, 0))
        val temporary = File(destination.parentFile, "${destination.name}.gallerytmp")
        download(artifact, temporary)

        if (destination.exists() && !destination.delete()) {
          throw IOException("Unable to replace ${destination.absolutePath}")
        }
        if (!temporary.renameTo(destination)) {
          temporary.copyTo(destination, overwrite = true)
          if (!temporary.delete()) temporary.deleteOnExit()
        }
        success(destination)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        Result.failure(Data.Builder().putString(Keys.ERROR, error.message ?: error.toString()).build())
      }
    }

  private suspend fun download(artifact: ModelArtifact, temporary: File) {
    val existingBytes = temporary.takeIf { it.isFile }?.length() ?: 0L
    val connection = (URL(artifact.url).openConnection() as HttpURLConnection)
    try {
      connection.connectTimeout = 30_000
      connection.readTimeout = 30_000
      connection.instanceFollowRedirects = true
      connection.setRequestProperty("Accept-Encoding", "identity")
      inputData.getString(Keys.ACCESS_TOKEN)?.takeIf { it.isNotBlank() }?.let {
        connection.setRequestProperty("Authorization", "Bearer $it")
      }
      if (existingBytes > 0L) connection.setRequestProperty("Range", "bytes=$existingBytes-")
      connection.connect()

      val append = existingBytes > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
      if (
        connection.responseCode != HttpURLConnection.HTTP_OK &&
          connection.responseCode != HttpURLConnection.HTTP_PARTIAL
      ) {
        throw IOException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
      }

      var receivedBytes = if (append) existingBytes else 0L
      val responseBytes = connection.contentLengthLong.coerceAtLeast(0L)
      val totalBytes =
        when {
          artifact.sizeInBytes > 0L -> artifact.sizeInBytes
          append -> existingBytes + responseBytes
          else -> responseBytes
        }
      var windowStartedAt = System.currentTimeMillis()
      var windowBytes = 0L
      var lastReportAt = 0L

      connection.inputStream.buffered().use { input ->
        FileOutputStream(temporary, append).buffered().use { output ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            receivedBytes += count
            windowBytes += count

            val now = System.currentTimeMillis()
            if (now - lastReportAt >= 300L) {
              val elapsed = (now - windowStartedAt).coerceAtLeast(1L)
              val bytesPerSecond = windowBytes * 1000L / elapsed
              setProgress(
                Data.Builder()
                  .putLong(Keys.RECEIVED_BYTES, receivedBytes)
                  .putLong(Keys.TOTAL_BYTES, totalBytes)
                  .putLong(Keys.BYTES_PER_SECOND, bytesPerSecond)
                  .build()
              )
              if (now - lastReportAt >= 1_000L) {
                val percent =
                  if (totalBytes > 0L) (receivedBytes * 100L / totalBytes).toInt().coerceIn(0, 100)
                  else 0
                setForeground(foregroundInfo(artifact.displayName, percent))
              }
              lastReportAt = now
              if (elapsed >= 2_000L) {
                windowStartedAt = now
                windowBytes = 0L
              }
            }
          }
        }
      }
      setProgress(
        Data.Builder()
          .putLong(Keys.RECEIVED_BYTES, receivedBytes)
          .putLong(Keys.TOTAL_BYTES, totalBytes)
          .putLong(Keys.BYTES_PER_SECOND, 0L)
          .build()
      )
    } finally {
      connection.disconnect()
    }
  }

  private fun readArtifact(): ModelArtifact =
    ModelArtifact(
      id = requireInput(Keys.ID),
      displayName = requireInput(Keys.NAME),
      url = requireInput(Keys.URL),
      fileName = requireInput(Keys.FILE_NAME),
      version = requireInput(Keys.VERSION),
      sizeInBytes = inputData.getLong(Keys.TOTAL_BYTES, 0L),
    )

  private fun requireInput(key: String): String =
    requireNotNull(inputData.getString(key)) { "Missing worker input: $key" }

  private fun success(file: File): Result =
    Result.success(Data.Builder().putString(Keys.OUTPUT_PATH, file.absolutePath).build())

  override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("AI model", 0)

  private fun foregroundInfo(name: String, progress: Int): ForegroundInfo {
    val manager =
      applicationContext.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
      )
    }
    val builder =
      NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Downloading $name")
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, progress, false)
    applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let {
      builder.setContentIntent(
        PendingIntent.getActivity(
          applicationContext,
          id.hashCode(),
          it,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      )
    }
    return ForegroundInfo(
      id.hashCode(),
      builder.build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
  }

  private companion object {
    const val CHANNEL_ID = "ai_model_downloads"
  }
}
