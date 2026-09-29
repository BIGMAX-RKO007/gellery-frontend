package com.google.ai.edge.gallery.modeldownload

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CatalogSource { NETWORK, CACHE, BUNDLED_ASSET }

data class ModelCatalog(val models: List<ModelArtifact>, val source: CatalogSource)

interface ModelCatalogRepository {
  suspend fun load(): ModelCatalog
}

/**
 * Reads the Gallery allowlist shape without depending on the Gallery app's Model/UI classes.
 * A successful network response is cached; cache and bundled asset are automatic fallbacks.
 */
class JsonModelCatalogRepository(
  context: Context,
  private val remoteUrl: String,
  private val bundledAssetPath: String? = null,
  cacheFileName: String = "model-catalog.json",
) : ModelCatalogRepository {
  private val appContext = context.applicationContext
  private val cacheFile = File(appContext.filesDir, cacheFileName)
  private val gson = Gson()

  override suspend fun load(): ModelCatalog =
    withContext(Dispatchers.IO) {
      runCatching {
          val json = fetch(remoteUrl)
          cacheFile.writeText(json)
          ModelCatalog(parse(json), CatalogSource.NETWORK)
        }
        .getOrElse { networkError ->
          if (cacheFile.isFile) {
            return@withContext ModelCatalog(parse(cacheFile.readText()), CatalogSource.CACHE)
          }
          val asset = bundledAssetPath
          if (asset != null) {
            val json = appContext.assets.open(asset).bufferedReader().use { it.readText() }
            return@withContext ModelCatalog(parse(json), CatalogSource.BUNDLED_ASSET)
          }
          throw networkError
        }
    }

  private fun fetch(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 15_000
      connection.readTimeout = 15_000
      connection.instanceFollowRedirects = true
      if (connection.responseCode !in 200..299) {
        throw java.io.IOException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
      }
      return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }

  private fun parse(json: String): List<ModelArtifact> {
    val catalog = gson.fromJson(json, CatalogJson::class.java)
    return catalog.models
      .filterNot { it.disabled == true }
      .mapNotNull { model -> runCatching { model.toArtifact() }.getOrNull() }
  }
}

private data class CatalogJson(val models: List<CatalogModel> = emptyList())

private data class CatalogModel(
  val name: String = "",
  val modelId: String = "",
  val modelFile: String = "",
  val commitHash: String = "",
  val sizeInBytes: Long = 0L,
  val description: String = "",
  val minDeviceMemoryInGb: Int? = null,
  val llmSupportImage: Boolean = false,
  val llmSupportAudio: Boolean = false,
  val taskTypes: List<String> = emptyList(),
  val defaultConfig: CatalogDefaultConfig? = null,
  val disabled: Boolean? = null,
  val url: String? = null,
  val socToModelFiles: Map<String, SocModelFile>? = null,
) {
  fun toArtifact(): ModelArtifact {
    val soc =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty().lowercase()
      else ""
    val socFile = socToModelFiles?.get(soc)
    val selectedFile = socFile?.modelFile ?: modelFile
    val selectedRevision = socFile?.commitHash ?: commitHash
    val selectedUrl =
      socFile?.url
        ?: url
        ?: "https://huggingface.co/$modelId/resolve/$selectedRevision/$selectedFile?download=true"
    return ModelArtifact(
      id = modelId.ifBlank { name },
      displayName = name,
      url = selectedUrl,
      fileName = selectedFile,
      version = selectedRevision,
      sizeInBytes = socFile?.sizeInBytes ?: sizeInBytes,
      description = description,
      repositoryId = modelId,
      minDeviceMemoryInGb = minDeviceMemoryInGb,
      supportsImage = llmSupportImage,
      supportsAudio = llmSupportAudio,
      taskTypes = taskTypes,
      inferenceDefaults =
        defaultConfig?.let {
          ModelInferenceDefaults(
            topK = it.topK,
            topP = it.topP,
            temperature = it.temperature,
            maxTokens = it.maxTokens,
            maxContextLength = it.maxContextLength,
            accelerators = it.accelerators,
          )
        },
    )
  }
}

private data class CatalogDefaultConfig(
  val topK: Int? = null,
  val topP: Double? = null,
  val temperature: Double? = null,
  val maxTokens: Int? = null,
  val maxContextLength: Int? = null,
  val accelerators: String? = null,
)

private data class SocModelFile(
  val modelFile: String? = null,
  val url: String? = null,
  val commitHash: String? = null,
  val sizeInBytes: Long? = null,
)
