package com.example.voxmate.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.aicore.AiBackend
import com.google.ai.edge.gallery.aicore.AiChatEvent
import com.google.ai.edge.gallery.aicore.AiChatRequest
import com.google.ai.edge.gallery.aicore.AiChatSession
import com.google.ai.edge.gallery.aicore.AiModelConfig
import com.google.ai.edge.gallery.aicore.LiteRtAiCore
import com.google.ai.edge.gallery.modelmanagerui.SelectedModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 模型运行时会话状态枚举。
 */
sealed interface SessionStatus {
  /** 尚未加载任何本地大模型（使用内置轻量即时伴侣会话兜底）。 */
  data object Idle : SessionStatus

  /** 正在将大模型权重从磁盘加载至移动端 NPU/GPU 显存中。 */
  data class Loading(val modelName: String) : SessionStatus

  /** 本地大模型已成功装载至移动端显存，全功能就绪。 */
  data class Ready(val modelName: String, val backend: AiBackend) : SessionStatus

  /** 模型加载失败（携带具体原因），自动降级使用内置智能会话。 */
  data class Error(val modelName: String, val message: String) : SessionStatus
}

/**
 * VoxMate 核心端侧大模型会话生命周期管理器。
 *
 * 职责：
 * 1. 负责真实端侧 LiteRT-LM 模型的异步加载、硬件后端自适应（GPU 优先，失败自动降级 CPU）。
 * 2. 注入针对 3D 数字人情绪控制的系统提示词（System Instruction），引导模型在句首输出 [happy] 等情绪标记。
 * 3. 严格遵循 [AiChatSession] 契约，支持随时打断、释放旧模型显存与流式输出。
 */
class VoxMateAiSessionManager(
  private val context: Context,
  private val coroutineScope: CoroutineScope,
) : AiChatSession {

  private val appContext = context.applicationContext

  /** 当前活跃的 AI 会话（真实 LiteRT 实例或兜底 Companion 实例）。 */
  @Volatile
  private var activeSession: AiChatSession = CompanionAiChatSession()

  /** 当前选定的本地模型信息。 */
  private val _currentModel = MutableStateFlow<SelectedModel?>(null)
  val currentModel: StateFlow<SelectedModel?> = _currentModel.asStateFlow()

  /** 当前模型加载状态流。 */
  private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Idle)
  val status: StateFlow<SessionStatus> = _status.asStateFlow()

  override val isGenerating: Boolean
    get() = activeSession.isGenerating

  init {
    // 启动时自动嗅探并装载本地已下载的模型
    coroutineScope.launch {
      val defaultModel = withContext(Dispatchers.IO) {
        LocalModelFinder.findDefaultModel(appContext)
      }
      if (defaultModel != null) {
        loadModel(defaultModel)
      }
    }
  }

  /**
   * 异步加载指定的本地大模型。
   *
   * @param selectedModel 待加载的本地模型定义与物理文件。
   */
  fun loadModel(selectedModel: SelectedModel) {
    coroutineScope.launch {
      val modelName = selectedModel.artifact.displayName
      Log.i(TAG, "开始加载本地大模型: $modelName, 物理路径: ${selectedModel.file.absolutePath}")

      _currentModel.value = selectedModel
      _status.value = SessionStatus.Loading(modelName)

      // 1. 关闭前一个模型释放 GPU/RAM
      runCatching { activeSession.close() }

      withContext(Dispatchers.IO) {
        try {
          // 2. 尝试 GPU 加速加载
          Log.i(TAG, "正在以 GPU 加速初始化 LiteRT-LM 引擎...")
          val session = createLiteRtSession(selectedModel, AiBackend.GPU)
          activeSession = session
          _status.value = SessionStatus.Ready(modelName, AiBackend.GPU)
          Log.i(TAG, "本地大模型 $modelName GPU 初始化成功！已就绪。")
        } catch (gpuError: Throwable) {
          Log.w(TAG, "GPU 初始化失败 (${gpuError.message})，尝试使用 CPU 后端降级重试...", gpuError)
          try {
            // 3. 降级为 CPU 后端
            val session = createLiteRtSession(selectedModel, AiBackend.CPU)
            activeSession = session
            _status.value = SessionStatus.Ready(modelName, AiBackend.CPU)
            Log.i(TAG, "本地大模型 $modelName CPU 初始化成功！")
          } catch (cpuError: Throwable) {
            val errMsg = cpuError.message ?: "引擎初始化失败"
            Log.e(TAG, "CPU 初始化亦失败: $errMsg", cpuError)
            activeSession = CompanionAiChatSession()
            _status.value = SessionStatus.Error(modelName, errMsg)
          }
        }
      }
    }
  }

  private suspend fun createLiteRtSession(model: SelectedModel, backend: AiBackend): AiChatSession {
    val config = AiModelConfig(
      modelPath = model.file.absolutePath,
      backend = backend,
      maxNumTokens = 4096,
      topK = 20,
      topP = 0.8,
      temperature = 0.7,
      systemInstruction = SYSTEM_INSTRUCTION_PROMPT,
    )
    return LiteRtAiCore.createSession(appContext, config)
  }

  override fun send(request: AiChatRequest): Flow<AiChatEvent> {
    return activeSession.send(request)
  }

  override fun stop() {
    activeSession.stop()
  }

  override suspend fun reset(systemInstruction: String?) {
    activeSession.reset(systemInstruction ?: SYSTEM_INSTRUCTION_PROMPT)
  }

  override suspend fun close() {
    activeSession.close()
  }

  companion object {
    private const val TAG = "VoxMateAiManager"

    /** 专为 3D 数字人伴侣定制的系统人设与情绪指令。 */
    const val SYSTEM_INSTRUCTION_PROMPT =
      "你是 VoxMate，一个温暖、真诚、聪颖的端侧 3D AI 数字人伴侣。请用亲切、生动、简练的口语（非常适合实时语音朗读，请避免过长长篇大论）与用户对话交流。你可以根据当前语境与对话氛围，在回答的最开头附带一个情绪标签，例如 [happy]、[relaxed]、[sad]、[surprised] 等。"
  }
}
