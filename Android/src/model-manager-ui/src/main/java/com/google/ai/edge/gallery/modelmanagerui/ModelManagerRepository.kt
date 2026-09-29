package com.google.ai.edge.gallery.modelmanagerui

import android.content.Context
import com.google.ai.edge.gallery.modeldownload.JsonModelCatalogRepository
import com.google.ai.edge.gallery.modeldownload.ModelArtifact
import com.google.ai.edge.gallery.modeldownload.ModelCatalog
import com.google.ai.edge.gallery.modeldownload.ModelDownloadManager
import com.google.ai.edge.gallery.modeldownload.ModelDownloadState
import com.google.ai.edge.gallery.modeldownload.WorkManagerModelDownloadManager
import java.io.File
import kotlinx.coroutines.flow.Flow

interface ModelManagerRepository {
  suspend fun loadCatalog(): ModelCatalog

  /** Returns the durable local or WorkManager state used to restore a model row after navigation. */
  suspend fun currentState(artifact: ModelArtifact): ModelDownloadState
  fun enqueue(artifact: ModelArtifact)
  fun observe(artifact: ModelArtifact): Flow<ModelDownloadState>
  fun downloadedFile(artifact: ModelArtifact): File?
  fun cancel(artifact: ModelArtifact)
  fun delete(artifact: ModelArtifact): Boolean
}

class DefaultModelManagerRepository(
  context: Context,
  config: ModelManagerConfig,
  private val downloads: ModelDownloadManager = WorkManagerModelDownloadManager(context),
) : ModelManagerRepository {
  private val catalog =
    JsonModelCatalogRepository(
      context = context,
      remoteUrl = config.catalogUrl,
      bundledAssetPath = config.bundledCatalogAsset,
      cacheFileName = "voxmate-model-catalog.json",
    )
  private val accessToken = config.huggingFaceAccessToken

  override suspend fun loadCatalog(): ModelCatalog = catalog.load()

  override suspend fun currentState(artifact: ModelArtifact): ModelDownloadState =
    downloads.currentState(artifact)

  override fun enqueue(artifact: ModelArtifact) = downloads.enqueue(artifact, accessToken)

  override fun observe(artifact: ModelArtifact): Flow<ModelDownloadState> =
    downloads.observe(artifact)

  override fun downloadedFile(artifact: ModelArtifact): File? = downloads.downloadedFile(artifact)

  override fun cancel(artifact: ModelArtifact) = downloads.cancel(artifact)

  override fun delete(artifact: ModelArtifact): Boolean = downloads.delete(artifact)
}
