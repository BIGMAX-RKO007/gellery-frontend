package com.google.ai.edge.gallery.modelmanagerui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.modeldownload.ModelArtifact
import com.google.ai.edge.gallery.modeldownload.CatalogSource
import com.google.ai.edge.gallery.modeldownload.ModelDownloadState
import java.util.Locale

@Composable
fun ModelManagerRoute(
  onClose: () -> Unit,
  onModelSelected: (SelectedModel) -> Unit,
  modifier: Modifier = Modifier,
  config: ModelManagerConfig = ModelManagerConfig(),
  repository: ModelManagerRepository? = null,
) {
  val context = LocalContext.current
  val resolvedRepository =
    remember(repository, config) {
      repository ?: DefaultModelManagerRepository(context.applicationContext, config)
    }
  val scope = rememberCoroutineScope()
  val controller = remember(resolvedRepository, scope) { ModelManagerController(resolvedRepository, scope) }
  val state by controller.uiState.collectAsState()

  ModelManagerScreen(
    state = state,
    onClose = onClose,
    onRetryCatalog = controller::refresh,
    onDownload = controller::download,
    onCancel = controller::cancel,
    onDelete = controller::delete,
    onModelSelected = onModelSelected,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
  state: ModelManagerUiState,
  onClose: () -> Unit,
  onRetryCatalog: () -> Unit,
  onDownload: (ModelArtifact) -> Unit,
  onCancel: (ModelArtifact) -> Unit,
  onDelete: (ModelArtifact) -> Unit,
  onModelSelected: (SelectedModel) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onClose)
  val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              Icons.AutoMirrored.Outlined.ListAlt,
              contentDescription = null,
              modifier = Modifier.size(21.dp),
            )
            Text(
              stringResource(R.string.models_count, state.models.size),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        },
        actions = {
          IconButton(onClick = onClose) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = stringResource(R.string.close_models),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    when {
      state.loading ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      state.errorMessage != null ->
        CatalogError(
          message = state.errorMessage,
          onRetry = onRetryCatalog,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
      else ->
        LazyColumn(
          modifier =
            Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.surfaceContainer)
              .padding(horizontal = 16.dp),
          contentPadding =
            PaddingValues(
              top = innerPadding.calculateTopPadding() + 16.dp,
              bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item {
            state.catalogSource?.let { source ->
              Text(
                stringResource(
                  when (source) {
                    CatalogSource.NETWORK -> R.string.catalog_online
                    CatalogSource.CACHE -> R.string.catalog_cached
                    CatalogSource.BUNDLED_ASSET -> R.string.catalog_offline
                  }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
              )
            }
          }
          items(state.models, key = { "${it.artifact.id}@${it.artifact.version}" }) { item ->
            val key = "${item.artifact.id}@${item.artifact.version}"
            val expanded = expandedStates[key] ?: true
            ModelCard(
              item = item,
              expanded = expanded,
              onToggleExpanded = { expandedStates[key] = !expanded },
              onDownload = { onDownload(item.artifact) },
              onCancel = { onCancel(item.artifact) },
              onDelete = { onDelete(item.artifact) },
              onUse = { file -> onModelSelected(SelectedModel(item.artifact, file)) },
            )
          }
        }
    }
  }
}

@Composable
private fun ModelCard(
  item: ModelListItem,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onUse: (java.io.File) -> Unit,
) {
  var confirmDelete by remember { mutableStateOf(false) }
  val artifact = item.artifact
  Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            artifact.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(5.dp))
          ModelStatusLine(item)
        }
        Icon(
          if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
          contentDescription =
            stringResource(
              if (expanded) R.string.collapse_model else R.string.expand_model
            ),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (artifact.description.isNotBlank()) {
            Text(
              artifact.description.plainMarkdown(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (artifact.supportsImage) CapabilityLabel(stringResource(R.string.capability_image))
            if (artifact.supportsAudio) CapabilityLabel(stringResource(R.string.capability_audio))
            artifact.minDeviceMemoryInGb?.let {
              CapabilityLabel(stringResource(R.string.minimum_ram, it))
            }
          }
          if (artifact.repositoryId.contains('/')) {
            val uriHandler = LocalUriHandler.current
            TextButton(
              onClick = { uriHandler.openUri("https://huggingface.co/${artifact.repositoryId}") },
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(stringResource(R.string.learn_more_and_license))
            }
          }
        }
      }

      DownloadActions(
        item = item,
        expanded = expanded,
        onDownload = onDownload,
        onCancel = onCancel,
        onDelete = { confirmDelete = true },
        onUse = onUse,
      )
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.delete_model_question)) },
      text = { Text(stringResource(R.string.delete_model_message, artifact.displayName)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            onDelete()
          }
        ) {
          Text(stringResource(R.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

@Composable
private fun ModelStatusLine(item: ModelListItem) {
  val artifact = item.artifact
  val unknownSize = stringResource(R.string.unknown_size)
  val text =
    when (val state = item.downloadState) {
      ModelDownloadState.NotDownloaded -> formatBytes(artifact.sizeInBytes, unknownSize)
      ModelDownloadState.Queued -> stringResource(R.string.waiting_to_download)
      is ModelDownloadState.Downloading ->
        stringResource(
          R.string.download_progress,
          formatBytes(state.receivedBytes, unknownSize),
          formatBytes(state.totalBytes, unknownSize),
        )
      is ModelDownloadState.Completed ->
        stringResource(
          R.string.downloaded_size,
          formatBytes(artifact.sizeInBytes, unknownSize),
        )
      is ModelDownloadState.Failed ->
        state.message.ifBlank { stringResource(R.string.download_failed) }
      ModelDownloadState.Cancelled -> stringResource(R.string.download_cancelled)
    }
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color =
      if (item.downloadState is ModelDownloadState.Failed) MaterialTheme.colorScheme.error
      else MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun DownloadActions(
  item: ModelListItem,
  expanded: Boolean,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onUse: (java.io.File) -> Unit,
) {
  when (val state = item.downloadState) {
    ModelDownloadState.NotDownloaded,
    ModelDownloadState.Cancelled,
    is ModelDownloadState.Failed ->
      Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Download, contentDescription = null)
        Text(stringResource(R.string.download), modifier = Modifier.padding(start = 8.dp))
      }
    ModelDownloadState.Queued ->
      OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.cancel_queued_download))
      }
    is ModelDownloadState.Downloading -> {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fraction = state.fraction
        if (fraction != null) {
          LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (expanded) {
          Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
              if (state.bytesPerSecond <= 0L) {
                stringResource(R.string.download_starting)
              } else {
                stringResource(
                  R.string.download_speed,
                  formatBytes(state.bytesPerSecond, stringResource(R.string.unknown_size)),
                )
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
          }
        }
      }
    }
    is ModelDownloadState.Completed ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.supportsVoiceChat) {
          Button(onClick = { onUse(state.file) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            Text(stringResource(R.string.use_model), modifier = Modifier.padding(start = 8.dp))
          }
        } else {
          OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.not_for_voice_chat))
          }
        }
        IconButton(onClick = onDelete) {
          Icon(
            Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.delete_model),
          )
        }
      }
  }
}

@Composable
private fun CapabilityLabel(text: String) {
  Box(
    modifier =
      Modifier.background(
          MaterialTheme.colorScheme.secondaryContainer,
          RoundedCornerShape(100.dp),
        )
        .padding(horizontal = 9.dp, vertical = 4.dp)
  ) {
    Text(text, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun CatalogError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      stringResource(R.string.failed_to_load_model_list),
      style = MaterialTheme.typography.titleLarge,
    )
    Text(
      message.ifBlank { stringResource(R.string.failed_to_load_model_list) },
      modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
  }
}

private fun formatBytes(bytes: Long, unknownSize: String): String {
  if (bytes <= 0L) return unknownSize
  val gb = bytes / 1_000_000_000.0
  return if (gb >= 1.0) String.format(Locale.US, "%.1f GB", gb)
  else String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
}

private fun String.plainMarkdown(): String =
  replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace("`", "")
