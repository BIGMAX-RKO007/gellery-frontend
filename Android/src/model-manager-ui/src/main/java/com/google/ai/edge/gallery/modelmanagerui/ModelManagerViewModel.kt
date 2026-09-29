package com.google.ai.edge.gallery.modelmanagerui

import com.google.ai.edge.gallery.modeldownload.ModelArtifact
import com.google.ai.edge.gallery.modeldownload.ModelDownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelManagerController(
  private val repository: ModelManagerRepository,
  private val scope: CoroutineScope,
) {
  private val _uiState = MutableStateFlow(ModelManagerUiState())
  val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()
  private val observationJobs = mutableMapOf<String, Job>()

  init {
    refresh()
  }

  fun refresh() {
    scope.launch {
      _uiState.update { it.copy(loading = true, errorMessage = null) }
      runCatching {
          val catalog = repository.loadCatalog()
          val items =
            catalog.models.map { artifact ->
              ModelListItem(
                artifact = artifact,
                downloadState = repository.currentState(artifact),
              )
            }
          catalog to items
        }
        .onSuccess { (catalog, items) ->
          observationJobs.values.forEach(Job::cancel)
          observationJobs.clear()
          _uiState.value =
            ModelManagerUiState(
              loading = false,
              models = items,
              catalogSource = catalog.source,
            )
          items
            .filter { item ->
              item.downloadState is ModelDownloadState.Queued ||
                item.downloadState is ModelDownloadState.Downloading
            }
            .forEach { item -> observeDownload(item.artifact) }
        }
        .onFailure { error ->
          _uiState.update {
            it.copy(
              loading = false,
              errorMessage = error.message.orEmpty(),
            )
          }
        }
    }
  }

  fun download(artifact: ModelArtifact) {
    updateState(artifact, ModelDownloadState.Queued)
    repository.enqueue(artifact)
    observeDownload(artifact)
  }

  fun cancel(artifact: ModelArtifact) {
    repository.cancel(artifact)
  }

  fun delete(artifact: ModelArtifact) {
    observationJobs.remove(artifact.key)?.cancel()
    if (repository.delete(artifact)) updateState(artifact, ModelDownloadState.NotDownloaded)
  }

  private fun observeDownload(artifact: ModelArtifact) {
    observationJobs.remove(artifact.key)?.cancel()
    observationJobs[artifact.key] =
      scope.launch {
        repository
          .observe(artifact)
          .catch { error ->
            emit(ModelDownloadState.Failed(error.message.orEmpty()))
          }
          .collect { state -> updateState(artifact, state) }
      }
  }

  private fun updateState(artifact: ModelArtifact, state: ModelDownloadState) {
    _uiState.update { current ->
      current.copy(
        models =
          current.models.map { item ->
            if (item.artifact.key == artifact.key) item.copy(downloadState = state) else item
          }
      )
    }
  }

}

private val ModelArtifact.key: String
  get() = "$id@$version"
