# Google AI Edge Gallery / VoxMate AI 开发规范

本文件是仓库级 AI 编码规则入口，适用于本目录及所有子目录。任何 AI 或自动化编码工具在分析、修改、生成代码前，都必须先阅读并遵守本文件。

## 1. 项目定位与目录

- Android Gradle 根目录是 `Android/src`，不是仓库根目录，也不是外层 `src`。
- 原 Google AI Edge Gallery 应用模块是 `Android/src/app`。
- 新语音聊天应用是 `Android/src/voice-chat-app`，产品名为 VoxMate（声伴）。
- 项目分析和架构记录统一存放在仓库根目录的 `doc` 文件夹。
- 每次完成重要分析、架构调整或新功能后，必须更新 `doc` 中的对应文档和 `doc/README.md` 索引。

## 2. 模块边界

当前核心模块及职责如下：

```text
:app
  原 Gallery 应用。作为参考实现保留，不应被 VoxMate 直接依赖。

:voice-chat-app
  VoxMate Application。只负责应用导航、页面组合、权限和业务流程。

:ai-core
  无 UI 的 LiteRT-LM 推理能力：初始化、会话、多模态输入、流式输出、停止和释放。

:model-download
  无 UI 的模型清单与下载能力：缓存、断点续传、进度、取消和本地文件路径。

:model-manager-ui
  可复用 Models 功能页面：模型浏览、下载状态、删除和模型选择。

:db_lib
  从 password_generator 引入的 Room 数据库参考模块；当前保留原业务表供架构参考，VoxMate 新数据必须重新定义领域模型。
```

必须遵守以下依赖方向：

```text
voice-chat-app → ai-core
voice-chat-app → model-manager-ui → model-download
voice-chat-app → model-download（仅业务确有需要时）
```

- `ai-core` 和 `model-download` 之间不得相互依赖。
- 基础库不得依赖 `app`、`voice-chat-app`、Compose 页面、Navigation 或 Firebase。
- `voice-chat-app` 不得复制或直接引用 `app` 内部的 ViewModel、页面、`Model`、`Task` 等类型。
- 新增数据库功能时必须参考 `C:/as_project/password_generator/password_generator/db_lib` 的 `Database / DAO / Entity` 分层；VoxMate 应定义自己的实体、DAO、数据库名和迁移，不得直接复用密码业务表。
- 从 Gallery 迁移功能时，应提取必要行为并重新定义清晰接口，不要整文件复制耦合代码。
- 未经用户明确要求，不修改或删除原 `:app` 的功能和源码。

## 3. Kotlin 与 Android 编码规则

- 使用 Kotlin；新增 Java 代码必须有明确兼容性理由。
- 遵循 Kotlin 官方代码风格，使用 2 空格缩进，与现有项目保持一致。
- 优先使用不可变 `val`、不可变集合和 `data class`。
- 避免 `!!`；优先使用显式校验、可空处理或有意义的失败信息。
- 不捕获并静默忽略异常。失败必须转换为状态、回调、Flow 异常或日志。
- 公共 API 不得暴露 Gallery 页面类型或不必要的第三方实现类型。
- 所有新增的类、接口、枚举和 `object`（包括非公开类型）必须添加完整 KDoc/Javadoc，说明用途、职责、生命周期或所有权，以及重要使用限制。
- 所有新增的方法和函数（包括私有方法）必须添加完整 KDoc/Javadoc，说明行为、参数、返回值、失败或异常情况，以及适用的线程或协程约束。
- 所有新增的字段、属性和常量（包括私有成员）必须添加注释，说明含义；适用时还要写明单位、有效范围、数据来源、持久化方式和安全要求。
- 注释应描述契约和设计原因，不能只是重复名称；代码行为变化时必须同步更新对应注释。
- 单个文件只承担一个清晰职责；页面、状态控制、数据访问和平台实现应拆分。
- 不使用全局可变状态保存会话、Token、下载状态或 Activity Context。
- 长耗时、磁盘、网络和模型初始化不得运行在主线程。
- 协程必须绑定明确生命周期；不要创建无法取消的匿名全局 CoroutineScope。

## 4. Compose UI 规则

- Composable 只负责渲染状态和上报用户事件，不直接执行网络、文件或 WorkManager 操作。
- 页面应采用“Route/Screen”分层：
  - `Route` 负责连接状态控制器、仓库和导航回调。
  - `Screen` 只接收 UI state 与事件函数，便于预览和测试。
- 状态应向下传递，事件应向上传递；避免在多层组件中读取全局单例。
- 可复用组件不得直接持有 NavController。
- 所有可点击图标必须提供 contentDescription，纯装饰图标应显式使用 `null`。
- 列表必须提供稳定 key；下载进度更新不得造成整个页面无必要重建。
- 文案应优先进入资源文件。原型阶段允许少量临时文案，但功能稳定后必须资源化。
- 页面必须处理 Loading、Content、Empty 和 Error 状态。

## 5. AI 会话规则

- 上层只能通过 `AiChatSession` 使用模型，不直接操作 LiteRT-LM `Engine` 或 `Conversation`。
- 下载库返回的本地文件路径通过 `AiModelConfig.modelPath` 传给 `ai-core`。
- 同一会话同一时间只允许一个生成任务；开始新请求前必须处理当前生成状态。
- 用户离开会话或停止对话时，应调用 `stop()`；不再使用模型时必须调用 `close()`。
- 流式结果使用 `Flow<AiChatEvent>`，不要让 UI 直接依赖 LiteRT-LM callback。
- STT 和 TTS 属于 VoxMate 应用层，通过 `SpeechInput`、`SpeechOutput` 等接口接入，不放进 `ai-core`。

## 6. 模型清单与下载规则

- UI 不得直接创建 HTTP 请求或操作下载临时文件。
- 模型下载统一通过 `ModelDownloadManager` 或其上层 Repository。
- 大模型下载必须支持后台执行、取消、断点续传和明确进度。
- 服务端应支持 HTTP Range；服务器忽略 Range 时必须安全地从头覆盖，不能追加损坏文件。
- 最终文件写入应尽可能采用临时文件加原子替换。
- 文件名、模型 ID 和版本号用于路径前必须清理，禁止目录穿越。
- 后续增加 SHA-256 后，校验成功前不得把文件标记为 Completed。
- 网络清单加载失败时，按“网络 → 磁盘缓存 → 内置 assets”顺序回退。
- 模型字段变更时，应保持 JSON 解析向后兼容，新增字段提供默认值。

## 7. Token、隐私与安全

- Hugging Face Token、API Key、签名密钥和真实服务地址凭证不得硬编码或提交到 Git。
- Token 不得写入日志、错误提示、截图、测试夹具或文档示例。
- 需要认证时通过配置接口或安全存储注入，并只申请最小权限，例如只读 Token。
- 不得用自建下载地址规避模型许可证、访问许可或地区限制。
- 自托管模型前必须确认许可证允许重新分发，并保留要求的许可证和声明。
- 麦克风录音必须在用户明确授权和可感知的状态下进行。
- 默认优先端侧处理语音和对话；新增云端上传必须明确告知用户并取得授权。

## 8. Gradle 与依赖规则

- 使用 `gradle/libs.versions.toml` 管理依赖版本和别名，不在各模块散落版本号。
- 当前项目使用 AGP 9 内置 Kotlin，新 Android 模块不要应用 `org.jetbrains.kotlin.android` 插件。
- Compose 模块使用 `org.jetbrains.kotlin.plugin.compose`。
- 优先复用已有依赖，新增依赖前确认必要性、许可证、体积和 Android 最低版本。
- 不要让基础库引入 Hilt、Firebase、Navigation 或整个 Gallery App 依赖树。
- 新模块必须在 `settings.gradle.kts` 注册，并提供简短 README 说明职责和调用方式。

## 9. 测试与验收

修改代码后按影响范围进行验证：

- 修改 `ai-core`：至少运行 `:ai-core:testDebugUnitTest` 和 `:ai-core:assembleDebug`。
- 修改 `model-download`：至少运行 `:model-download:testDebugUnitTest` 和 `:model-download:assembleDebug`。
- 修改 `model-manager-ui`：至少运行 `:model-manager-ui:assembleDebug`。
- 修改 VoxMate：至少运行 `:voice-chat-app:assembleDebug`。
- 修改原 Gallery：至少运行 `:app:assembleDebug`。
- 修改公共 Gradle 配置或版本目录：构建所有受影响模块。

测试应优先覆盖：

- URL、模型路径和文件名转换。
- 清单解析与回退顺序。
- 下载状态转换和失败处理。
- 会话并发、取消、重置和资源释放。
- Controller/Repository 的状态变化。

不得把“源码编译成功”等同于“APK/AAR 打包成功”。交付前要验证对应最终产物。

## 10. 变更原则

- 修改前先阅读相关实现和 `doc` 记录，不要重复猜测已经确认的架构。
- 保留用户已有修改，不重置、不覆盖、不格式化无关文件。
- 优先小步迁移：新增接口和适配器 → 编译验证 → 切换调用 → 真机验证 → 删除旧实现。
- 不为了“清理”而顺带重构无关代码。
- 发现需求超出当前模块边界时，先说明影响，再建立新接口或独立模块。
- 完成后报告修改范围、验证命令、产物位置和仍存在的限制。
