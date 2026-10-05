package com.example.voxmate.ai

import android.content.Context
import android.util.Log
import com.example.voxmate.persona.ActivePersona
import com.example.voxmate.persona.AvatarPersonaPrompt
import com.example.voxmate.persona.PersonaRepository
import com.google.ai.edge.gallery.aicore.AiBackend
import com.google.ai.edge.gallery.aicore.AiChatEvent
import com.google.ai.edge.gallery.aicore.AiChatRequest
import com.google.ai.edge.gallery.aicore.AiChatSession
import com.google.ai.edge.gallery.aicore.AiModelConfig
import com.google.ai.edge.gallery.aicore.LiteRtAiCore
import com.google.ai.edge.gallery.modelmanagerui.SelectedModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * @param context 用于创建引擎和人物仓库，内部资源只保留 Application Context
 * @param coroutineScope 应用根页面持有的可取消协程作用域，控制预加载生命周期
 * @param personaRepository 人物持久化入口，未选择时不改变原有系统提示词
 */
class VoxMateAiSessionManager(
  private val context: Context,
  private val coroutineScope: CoroutineScope,
  /** 应用级人物仓库；只读取用户明确持久化的选择，不自动启用内置人物。 */
  private val personaRepository: PersonaRepository = PersonaRepository.create(context),
) : AiChatSession {

  /** 引擎初始化需要的应用 Context，不持有 Activity。 */
  private val appContext = context.applicationContext

  /** 串行保护模型加载、人物切换和会话重置，避免创建过程中覆盖系统指令。 */
  private val sessionMutex = Mutex()

  /** 当前会话使用的合并指令；未选择人物时等于原有系统提示词。 */
  private var currentInstruction = SYSTEM_INSTRUCTION_PROMPT

  /** 已成功应用且持久化的人物；null 表示使用原有对话方式。 */
  private val _activePersona = MutableStateFlow<ActivePersona?>(null)

  /** 首页姓名和商店选中状态的数据源，随应用会话生命周期持有。 */
  val activePersona: StateFlow<ActivePersona?> = _activePersona.asStateFlow()

  /** 人物恢复错误可供商店展示；失败不阻止使用原有对话。 */
  private val _personaError = MutableStateFlow<String?>(null)

  /** 最近一次启动恢复失败原因，不记录人物包正文或敏感信息。 */
  val personaError: StateFlow<String?> = _personaError.asStateFlow()

  /** 加载模型之前恢复明确选择，防止启动时先创建无人物的对话。 */
  private val personaRestoration = coroutineScope.async {
    try {
      val persona = personaRepository.loadActive()
      currentInstruction = AvatarPersonaPrompt.compose(SYSTEM_INSTRUCTION_PROMPT, persona)
      _activePersona.value = persona
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Log.e(TAG, "恢复人物失败，保留原有对话", error)
      _personaError.value = error.message ?: error.javaClass.simpleName
    }
  }

  /** 当前活跃的 AI 会话（真实 LiteRT 实例或兜底 Companion 实例）。 */
  @Volatile
  private var activeSession: AiChatSession = CompanionAiChatSession()

  /** 当前选定的本地模型信息。 */
  private val _currentModel = MutableStateFlow<SelectedModel?>(null)
  /** 首页和模型切换入口消费的只读模型状态。 */
  val currentModel: StateFlow<SelectedModel?> = _currentModel.asStateFlow()

  /** 当前模型加载状态流。 */
  private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Idle)
  /** 控制首页自动通话启停的模型就绪状态。 */
  val status: StateFlow<SessionStatus> = _status.asStateFlow()

  override val isGenerating: Boolean
    get() = activeSession.isGenerating

  init {
    // 启动时自动嗅探并装载本地已下载的模型
    coroutineScope.launch {
      personaRestoration.await()
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
      personaRestoration.await()
      sessionMutex.withLock {
        val modelName = selectedModel.artifact.displayName
        Log.i(TAG, "开始加载本地大模型: $modelName, 物理路径: ${selectedModel.file.absolutePath}")

        _currentModel.value = selectedModel
        _status.value = SessionStatus.Loading(modelName)

        // 1. 关闭前一个模型释放 GPU/RAM
        try {
          activeSession.close()
        } catch (error: CancellationException) {
          throw error
        } catch (error: Exception) {
          Log.w(TAG, "释放旧模型会话失败", error)
        }

        withContext(Dispatchers.IO) {
          try {
            // 2. 尝试 GPU 加速加载
            Log.i(TAG, "正在以 GPU 加速初始化 LiteRT-LM 引擎...")
            val session = createLiteRtSession(selectedModel, AiBackend.GPU)
            activeSession = session
            _status.value = SessionStatus.Ready(modelName, AiBackend.GPU)
            Log.i(TAG, "本地大模型 $modelName GPU 初始化成功！已就绪。")
          } catch (error: CancellationException) {
            throw error
          } catch (gpuError: Exception) {
            Log.w(TAG, "GPU 初始化失败 (${gpuError.message})，尝试使用 CPU 后端降级重试...", gpuError)
            try {
              // 3. 降级为 CPU 后端
              val session = createLiteRtSession(selectedModel, AiBackend.CPU)
              activeSession = session
              _status.value = SessionStatus.Ready(modelName, AiBackend.CPU)
              Log.i(TAG, "本地大模型 $modelName CPU 初始化成功！")
            } catch (error: CancellationException) {
              throw error
            } catch (cpuError: Exception) {
              val errMsg = cpuError.message ?: "引擎初始化失败"
              Log.e(TAG, "CPU 初始化亦失败: $errMsg", cpuError)
              activeSession = CompanionAiChatSession()
              _status.value = SessionStatus.Error(modelName, errMsg)
            }
          }
        }
      }
    }
  }

  /** 等待启动恢复完成；调用协程可取消，供商店读取索引前同步启动状态。 */
  suspend fun awaitPersonaRestored() = personaRestoration.await()

  /**
   * 应用商店中明确选择的人物，或恢复原有对话；不重新加载模型权重。
   * @param personaId 已安装人物 ID；null 表示清除选择
   * @param version 对应版本，选择人物时不可为 null
   * @throws Exception 校验、会话重置或持久化失败时抛出，尽力恢复原指令并保留旧选择
   * 在应用生命周期协程中调用；会话与磁盘操作在 IO 调度器执行。
   */
  suspend fun selectPersona(personaId: String?, version: String? = null) {
    personaRestoration.await()
    sessionMutex.withLock {
      withContext(Dispatchers.IO) {
        val candidate = personaId?.let {
          personaRepository.readPersona(it, requireNotNull(version))
        }
        val instruction = AvatarPersonaPrompt.compose(SYSTEM_INSTRUCTION_PROMPT, candidate)
        val previousInstruction = currentInstruction
        activeSession.stop()
        // LiteRT 的取消完成回调异步到达；不能仅调用 stop 就立即创建新 Conversation。
        withTimeout(10_000L) {
          while (activeSession.isGenerating) delay(20L)
        }
        // 短暂提交段不被导航取消打断，确保数据库选择和已应用会话一起完成。
        withContext(kotlinx.coroutines.NonCancellable) {
          try {
            activeSession.reset(instruction)
            if (candidate == null) personaRepository.clearActive()
            else personaRepository.activate(candidate.bundle.manifest.id, candidate.bundle.manifest.version)
          } catch (error: Exception) {
            try {
              activeSession.reset(previousInstruction)
            } catch (rollbackError: Exception) {
              error.addSuppressed(rollbackError)
              Log.e(TAG, "人物切换失败后恢复会话失败", rollbackError)
            }
            throw error
          }
          currentInstruction = instruction
          _activePersona.value = candidate
          _personaError.value = null
        }
      }
    }
  }

  /**
   * 在 IO 协程创建已注入当前人物的真实会话。
   * @param model 已下载模型文件
   * @param backend GPU 优先，失败后可由调用者重试 CPU
   * @return 拥有引擎的会话；初始化错误交给调用者转换为加载状态
   */
  private suspend fun createLiteRtSession(model: SelectedModel, backend: AiBackend): AiChatSession {
    val config = AiModelConfig(
      modelPath = model.file.absolutePath,
      backend = backend,
      maxNumTokens = 4096,
      topK = 20,
      topP = 0.8,
      temperature = 0.7,
      systemInstruction = currentInstruction,
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
    personaRestoration.await()
    sessionMutex.withLock {
      withContext(Dispatchers.IO) {
        activeSession.reset(systemInstruction ?: currentInstruction)
      }
    }
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
