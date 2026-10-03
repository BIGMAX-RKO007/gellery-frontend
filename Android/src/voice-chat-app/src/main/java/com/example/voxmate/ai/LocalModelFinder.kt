package com.example.voxmate.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.modeldownload.ModelArtifact
import com.google.ai.edge.gallery.modelmanagerui.SelectedModel
import java.io.File

/**
 * 本地已下载 LiteRT-LM 模型的自动发现与扫描工具。
 *
 * 职责：
 * 1. 扫描应用外部与内部存储的 models/ 目录，自动索引已完成下载的本地大模型权重。
 * 2. 识别 Qwen2.5、Gemma 等知名模型，构造可供 LiteRT-LM 驱动的 [SelectedModel] 对象。
 * 3. 在应用初次启动或用户未手动选定时，自动推选最适合当前移动端芯片的默认大模型。
 */
object LocalModelFinder {

  private const val TAG = "LocalModelFinder"

  /**
   * 扫描并返回本地存储中所有已就绪的 LiteRT-LM 模型文件。
   *
   * @param context Android 上下文，用于解析外部与内部 files 路径。
   * @return 本地发现的所有有效模型列表。
   */
  fun findDownloadedModels(context: Context): List<SelectedModel> {
    val results = mutableListOf<SelectedModel>()
    val candidateRoots = listOfNotNull(
      context.getExternalFilesDir("models"),
      File(context.filesDir, "models"),
    )

    for (root in candidateRoots) {
      if (!root.exists() || !root.isDirectory) continue
      scanDirectoryForModels(root, results)
    }

    Log.i(TAG, "已在本地扫描到 ${results.size} 个有效模型: ${results.map { it.artifact.displayName }}")
    return results
  }

  /**
   * 自动推选最佳的首选本地大模型。
   *
   * 策略：
   * 优先推荐参数适中、对骁龙 8 Gen 1 GPU 适配极佳且回复响应极快的 Qwen2.5-1.5B；
   * 其次推荐 Gemma 4 / Gemma 3；若无则返回首个可用模型。
   *
   * @param context Android 上下文。
   * @return 推荐的本地模型，若未下载任何模型则返回 null。
   */
  fun findDefaultModel(context: Context): SelectedModel? {
    val models = findDownloadedModels(context)
    if (models.isEmpty()) return null

    // 1. 优先推荐 Qwen2.5-1.5B (轻量高智商，端侧 3D 数字人首选)
    val qwen = models.firstOrNull {
      it.artifact.displayName.contains("Qwen", ignoreCase = true) ||
        it.file.name.contains("Qwen", ignoreCase = true)
    }
    if (qwen != null) return qwen

    // 2. 其次推荐 Gemma
    val gemma = models.firstOrNull {
      it.artifact.displayName.contains("Gemma", ignoreCase = true) ||
        it.file.name.contains("gemma", ignoreCase = true)
    }
    if (gemma != null) return gemma

    // 3. 兜底返回第一个支持语言对话的模型
    return models.firstOrNull()
  }

  /**
   * 递归扫描指定目录下的 .litertlm 与 .bin 模型文件。
   */
  private fun scanDirectoryForModels(dir: File, outList: MutableList<SelectedModel>) {
    val files = dir.listFiles() ?: return
    for (file in files) {
      if (file.isDirectory) {
        scanDirectoryForModels(file, outList)
      } else if (file.isFile && isSupportedModelFile(file)) {
        val artifact = buildArtifactForFile(file)
        outList.add(SelectedModel(artifact = artifact, file = file))
      }
    }
  }

  /**
   * 判断文件是否为支持的端侧 LiteRT-LM 大语言模型权重。
   */
  private fun isSupportedModelFile(file: File): Boolean {
    val name = file.name.lowercase()
    // 排除分片临时文件与非大语言模型 task
    if (name.endsWith(".gallerytmp") || name.endsWith(".tmp") || name.startsWith(".")) {
      return false
    }
    // 排除特定非 LLM 的任务模型 (如 magic_touch 分割 task)
    if (name.endsWith(".task") && !name.contains("llm") && !name.contains("gemma")) {
      return false
    }
    return name.endsWith(".litertlm") || name.endsWith(".bin")
  }

  /**
   * 为扫描到的本地物理模型文件生成标准 [ModelArtifact]。
   */
  private fun buildArtifactForFile(file: File): ModelArtifact {
    val fileName = file.name
    val lowerName = fileName.lowercase()

    val (displayName, modelId) = when {
      lowerName.contains("qwen2.5-1.5b") ->
        "Qwen2.5-1.5B-Instruct" to "litert-community/Qwen2.5-1.5B-Instruct"
      lowerName.contains("gemma-4-e4b") ->
        "Gemma 4 E4B-IT" to "litert-community/gemma-4-E4B-it-litert-lm"
      lowerName.contains("gemma-3n-e4b") ->
        "Gemma 3n E4B-IT" to "google/gemma-3n-E4B-it-litert-lm"
      lowerName.contains("gemma-3n-e2b") ->
        "Gemma 3n E2B-IT" to "google/gemma-3n-E2B-it-litert-lm"
      lowerName.contains("gemma3-1b") ->
        "Gemma 3 1B-IT" to "litert-community/Gemma3-1B-IT"
      else -> {
        val baseName = fileName.substringBeforeLast(".")
        baseName to "local/$baseName"
      }
    }

    return ModelArtifact(
      id = modelId,
      displayName = displayName,
      url = "https://local.litertlm.model/$fileName",
      fileName = fileName,
      version = file.parentFile?.name ?: "1.0",
      sizeInBytes = file.length(),
      taskTypes = listOf("llm_chat", "llm_prompt_lab"),
    )
  }
}
