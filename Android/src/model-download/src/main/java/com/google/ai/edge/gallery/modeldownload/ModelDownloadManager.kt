package com.google.ai.edge.gallery.modeldownload

import kotlinx.coroutines.flow.Flow

/** UI-independent API for large, resumable model downloads. */
interface ModelDownloadManager {
  /** Enqueues a unique background download. The optional token is used as a Bearer token. */
  fun enqueue(artifact: ModelArtifact, accessToken: String? = null)

  /**
   * Reads the latest durable state for [artifact].
   *
   * This suspending snapshot is intended for restoring UI after recreation. Implementations must
   * perform disk or scheduler access away from the main thread and return [ModelDownloadState.NotDownloaded]
   * when no local file or scheduled work exists.
   */
  suspend fun currentState(artifact: ModelArtifact): ModelDownloadState

  /** Observes state changes for an existing or newly enqueued download until it reaches a terminal state. */
  fun observe(artifact: ModelArtifact): Flow<ModelDownloadState>

  /** Requests cancellation of the unique background work associated with [artifact]. */
  fun cancel(artifact: ModelArtifact)

  /** Returns the completed local model file, or `null` when the final file does not exist. */
  fun downloadedFile(artifact: ModelArtifact): java.io.File?

  /** Cancels work and deletes final and temporary files, returning whether all existing files were removed. */
  fun delete(artifact: ModelArtifact): Boolean
}
