# 无 UI 能力库拆分设计

记录日期：2026-09-28

## 1. 目标

二次开发不复用 Google AI Edge Gallery 当前的 Compose 页面，只复用底层能力，最终形成：

```text
自定义语音聊天 App
├── 语音识别（STT）
├── 自定义业务/UI/状态机
├── ai-core：本地模型和 AI 会话
├── model-download：模型清单和下载管理
└── 文字转语音（TTS）
```

目标模块不依赖 Gallery 的 Compose 页面、导航、页面 ViewModel、Firebase Analytics 或具体产品文案。

## 2. 模块规划

### 2.1 `:ai-core`

职责：

- 初始化和释放 LiteRT-LM Engine。
- 创建、重置和关闭 Conversation。
- 发送文字、图片和音频输入。
- 以 Flow 或回调形式返回流式 token、thinking、完成和错误事件。
- 停止当前生成。
- 管理采样配置：`topK`、`topP`、`temperature`、`maxTokens`。
- 管理 CPU/GPU/NPU 后端选择。
- 提供不依赖聊天页面的数据结构和生命周期接口。

不负责：

- Compose UI 和消息气泡。
- Navigation。
- 下载模型。
- Firebase Analytics。
- Gallery 的 Task、ModelManagerViewModel 和页面状态。
- 用户授权、付费、业务账号。

建议公共 API：

```kotlin
data class AiModelConfig(
  val id: String,
  val modelPath: String,
  val maxTokens: Int = 1024,
  val topK: Int = 64,
  val topP: Float = 0.95f,
  val temperature: Float = 1.0f,
  val backend: AiBackend = AiBackend.GPU,
  val supportsImage: Boolean = false,
  val supportsAudio: Boolean = false,
)

data class AiChatRequest(
  val text: String = "",
  val images: List<ByteArray> = emptyList(),
  val audioClips: List<ByteArray> = emptyList(),
  val enableThinking: Boolean = false,
)

sealed interface AiChatEvent {
  data class Token(val text: String, val thinking: String? = null) : AiChatEvent
  data object Completed : AiChatEvent
  data class Failed(val cause: Throwable) : AiChatEvent
}

interface AiChatSession : AutoCloseable {
  suspend fun initialize()
  fun send(request: AiChatRequest): Flow<AiChatEvent>
  fun stop()
  suspend fun reset(systemInstruction: String? = null)
  override fun close()
}
```

对应来源代码：

- `ui/llmchat/LlmChatModelHelper.kt`
- `runtime/LlmModelHelper.kt`
- `runtime/LlmSessionManager.kt`
- LiteRT-LM Engine、Conversation、MessageCallback 的调用代码

迁移时不能把现有 `data.Model` 原样作为库 API，因为它包含下载状态、UI 配置、运行时实例和任务配置，职责过多。库需要使用稳定、不可变的 `AiModelConfig`。

### 2.2 `:model-download`

职责：

- 获取和解析远端模型清单。
- 支持内置默认清单和远端更新。
- 构造 Hugging Face 或自有服务器下载地址。
- 下载、暂停/取消、断点续传和进度通知。
- 管理临时文件与正式文件。
- 保存已下载模型状态。
- 导入本地模型文件。
- 可选文件长度/SHA-256 校验。
- 返回最终可供 `ai-core` 使用的绝对模型路径。

不负责：

- 显示下载弹窗和进度页面。
- 选择哪个模型用于具体 Gallery Task。
- Gemma 会话和推理。
- 在库中硬编码 App 的 `versionName`。

建议公共 API：

```kotlin
data class ModelArtifact(
  val id: String,
  val displayName: String,
  val url: String,
  val fileName: String,
  val expectedBytes: Long? = null,
  val sha256: String? = null,
)

sealed interface ModelDownloadState {
  data object NotDownloaded : ModelDownloadState
  data class Downloading(
    val downloadedBytes: Long,
    val totalBytes: Long?,
  ) : ModelDownloadState
  data class Ready(val absolutePath: String) : ModelDownloadState
  data class Failed(val message: String, val recoverable: Boolean) : ModelDownloadState
}

interface ModelDownloadManager {
  fun observe(modelId: String): Flow<ModelDownloadState>
  suspend fun enqueue(artifact: ModelArtifact)
  suspend fun cancel(modelId: String)
  suspend fun delete(modelId: String)
  suspend fun import(modelId: String, source: Uri): String
}

interface ModelCatalogRepository {
  suspend fun getCatalog(forceRefresh: Boolean = false): List<ModelArtifact>
}
```

对应来源代码：

- `data/DownloadRepository.kt`
- `worker/DownloadWorker.kt`
- `data/ModelAllowlist.kt`
- `ui/modelmanager/ModelManagerViewModel.kt` 中与网络、缓存、文件相关的非 UI 逻辑
- Hugging Face URL 构造和访问令牌逻辑

建议模型清单采用“内置清单优先、远端更新覆盖、失败继续使用缓存”的策略，避免首次启动因为 GitHub Raw 无法访问而完全不可用。

## 3. 依赖方向

```text
voice-chat-app
  ├── depends on :ai-core
  └── depends on :model-download

:ai-core
  ├── LiteRT-LM
  └── Kotlin Coroutines

:model-download
  ├── Android WorkManager 或 DownloadManager
  ├── HTTP/JSON
  └── DataStore（仅保存下载状态）
```

`:ai-core` 不依赖 `:model-download`。下载库只返回模型文件路径，App 把路径组装成 `AiModelConfig` 传给 AI 库。这样未来可以替换下载方案，也可以直接使用预装模型。

## 4. 语音聊天如何组合

```text
用户开始说话
  ↓
SpeechRecognizer / 离线 STT
  ↓
得到 recognizedText
  ↓
AiChatSession.send(AiChatRequest(text = recognizedText))
  ↓
收集 AiChatEvent.Token 并显示文字
  ↓
遇到完整句子时加入 TTS 队列
  ↓
AiChatEvent.Completed 后等待 TTS 播放完成
  ↓
重新开始监听
```

STT 和 TTS 不应直接写进 LiteRT 会话实现。建议定义独立接口：

```kotlin
interface SpeechInput {
  val events: Flow<SpeechInputEvent>
  fun start()
  fun stop()
}

interface SpeechOutput {
  val events: Flow<SpeechOutputEvent>
  fun speak(text: String, queue: Boolean = false)
  fun stop()
  fun shutdown()
}
```

第一版可以在 App 层使用 Android `SpeechRecognizer` 和 `TextToSpeech` 实现。以后替换为 sherpa-onnx 时不影响 `ai-core`。

## 5. 第一阶段不迁移的内容

- Gallery 首页和各功能页面。
- Compose 聊天气泡及输入框。
- Navigation。
- Firebase Analytics 和 Messaging。
- Benchmark 页面及传感器 UI。
- Agent Skills、MCP、WebView 技能执行器。
- Tiny Garden、Mobile Actions、Scrapbook 等产品演示功能。

这些能力与基础语音聊天无关，直接放入核心库会增加依赖、包体和故障面。后续如果确实需要 Agent Skills，可单独建立 `:agent-skills`，通过 `ai-core` 的工具调用扩展点接入。

## 6. 迁移步骤

### 阶段 A：建立库和公共契约

1. 在 `settings.gradle.kts` 注册 `:ai-core` 和 `:model-download`。
2. 建立两个 Android Library 模块。
3. 只添加最小依赖，不引入 Compose、Navigation、Firebase。
4. 定义上述公共接口和数据结构。
5. 为配置映射、状态转换和 URL/路径计算增加单元测试。

### 阶段 B：迁移 AI 会话实现

1. 从 `LlmChatModelHelper` 提取 LiteRT-LM 初始化代码。
2. 删除对 Gallery `Model`、Task、Metrics UI 的依赖。
3. 把 `MessageCallback` 转换为 `Flow<AiChatEvent>`。
4. 实现 stop、reset 和 close。
5. 让原 Gallery App 通过适配器调用新库，保证原功能继续工作。

### 阶段 C：迁移下载实现

1. 从 ViewModel 中移出清单网络请求和磁盘缓存。
2. 迁移 DownloadRepository/Worker。
3. 把通知、存储目录和 Token 获取设计成可注入配置。
4. 实现内置清单兜底。
5. 让原模型管理页面改为订阅库的下载状态。

### 阶段 D：建立无 Gallery UI 的示例

增加一个最小示例 Activity 或测试 App，仅验证：

```text
下载 Gemma-4-E2B-it
→ 初始化
→ 输入一段文字
→ 收到流式结果
→ 停止/重置/释放
```

示例通过后，再开发最终语音聊天 UI。

## 7. 验收标准

- 两个库都不依赖 Compose、Navigation 和 Gallery 页面代码。
- 上层只需模型路径即可创建会话。
- token 流式返回，不需要访问 Gallery ViewModel。
- 生成可以停止，会话可以重置和释放。
- 下载在页面退出后可以继续或恢复。
- 首次无网络时仍能读到内置模型清单。
- 下载失败能区分网络、鉴权、磁盘不足和文件校验失败。
- 原 Gallery App 在迁移期间仍可编译运行。
- 提供一个最小调用示例和模块 README。

## 8. 实施原则

不要一次性删除原实现。先新增库和适配器、通过编译和真机验证，再逐步让旧页面改用库。等没有旧代码引用后，最后删除重复实现。这能显著降低大型重构中 UI、下载和原生推理同时损坏的风险。

## 9. 实施记录（2026-09-28）

本次已在 `Android/src` Gradle 根目录完成第一版能力库：

- 新增 `:ai-core`，公开 `AiModelConfig`、`AiChatRequest`、`AiChatEvent`、`AiChatSession` 和 `LiteRtAiCore`。
- 已迁入 LiteRT-LM 的模型初始化、多模态输入、流式回调、thinking channel、停止、重置和资源释放能力。
- 新增 `:model-download`，公开 `ModelArtifact`、`ModelDownloadState`、`ModelDownloadManager`、`WorkManagerModelDownloadManager` 和 `ModelCatalogRepository`。
- 已实现 WorkManager 后台下载、HTTP Range 断点续传、Hugging Face Bearer Token、进度 Flow、取消、删除、本地路径返回，以及网络清单的缓存/assets 回退。
- 两个模块均未依赖 Compose、Navigation、Gallery ViewModel 或 Firebase。
- 每个模块已提供 README 和最小调用示例，并添加基础单元测试。

当前仍保留 Gallery 原来的 `LlmChatModelHelper`、`DownloadRepository` 和 `DownloadWorker`。这是有意的渐进迁移：新语音聊天页面可以直接使用新库；原 Gallery 页面将在适配器阶段逐步切换，完成真机回归后再删除旧实现。

尚未完成的增强项：

- 下载完成后的 SHA-256 校验和磁盘空间预检查。
- 将下载失败细分为网络、鉴权、空间不足和校验失败等结构化错误。
- 原 Gallery 页面到新库的兼容适配器。
- 只包含“下载 → 对话”的最小示例 App，以及语音 STT/TTS 层。
