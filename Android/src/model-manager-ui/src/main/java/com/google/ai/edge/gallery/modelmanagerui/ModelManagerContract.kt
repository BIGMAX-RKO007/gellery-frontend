package com.google.ai.edge.gallery.modelmanagerui

import com.google.ai.edge.gallery.modeldownload.ModelArtifact
import com.google.ai.edge.gallery.modeldownload.CatalogSource
import com.google.ai.edge.gallery.modeldownload.ModelDownloadState
import java.io.File

data class ModelManagerConfig(
  val catalogUrl: String =
    "https://raw.githubusercontent.com/google-ai-edge/gallery/refs/heads/main/model_allowlists/1_0_19.json",
  val bundledCatalogAsset: String = "1_0_19.json",
  val huggingFaceAccessToken: String? = null,
)

data class SelectedModel(val artifact: ModelArtifact, val file: File)

data class ModelListItem(
  val artifact: ModelArtifact,
  val downloadState: ModelDownloadState = ModelDownloadState.NotDownloaded,
) {
  val supportsVoiceChat: Boolean
    get() = "llm_chat" in artifact.taskTypes
}

data class ModelManagerUiState(
  val loading: Boolean = true,
  val models: List<ModelListItem> = emptyList(),
  val catalogSource: CatalogSource? = null,
  val errorMessage: String? = null,
)
