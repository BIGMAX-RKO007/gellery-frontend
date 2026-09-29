package com.google.ai.edge.gallery.modeldownload

import java.io.File

/** A single downloadable model file. */
data class ModelArtifact(
  val id: String,
  val displayName: String,
  val url: String,
  val fileName: String,
  val version: String,
  val sizeInBytes: Long = 0L,
  val description: String = "",
  val repositoryId: String = "",
  val minDeviceMemoryInGb: Int? = null,
  val supportsImage: Boolean = false,
  val supportsAudio: Boolean = false,
  val taskTypes: List<String> = emptyList(),
  val inferenceDefaults: ModelInferenceDefaults? = null,
) {
  init {
    require(id.isNotBlank()) { "id must not be blank." }
    require(url.startsWith("https://") || url.startsWith("http://")) {
      "Only HTTP(S) model URLs are supported."
    }
    require(fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\')) {
      "fileName must be a plain file name."
    }
  }

  companion object {
    fun fromHuggingFace(
      id: String,
      displayName: String,
      repositoryId: String,
      fileName: String,
      revision: String,
      sizeInBytes: Long = 0L,
    ): ModelArtifact =
      ModelArtifact(
        id = id,
        displayName = displayName,
        url = "https://huggingface.co/$repositoryId/resolve/$revision/$fileName?download=true",
        fileName = fileName,
        version = revision,
        sizeInBytes = sizeInBytes,
        repositoryId = repositoryId,
      )
  }
}

data class ModelInferenceDefaults(
  val topK: Int? = null,
  val topP: Double? = null,
  val temperature: Double? = null,
  val maxTokens: Int? = null,
  val maxContextLength: Int? = null,
  val accelerators: String? = null,
)

sealed interface ModelDownloadState {
  data object NotDownloaded : ModelDownloadState

  data object Queued : ModelDownloadState

  data class Downloading(
    val receivedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
  ) : ModelDownloadState {
    val fraction: Float?
      get() = if (totalBytes > 0L) (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
  }

  data class Completed(val file: File) : ModelDownloadState

  data class Failed(val message: String) : ModelDownloadState

  data object Cancelled : ModelDownloadState
}
