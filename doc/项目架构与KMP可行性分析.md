# Google AI Edge Gallery 项目架构与 KMP 可行性分析

> 分析日期：2026-09-27  
> 分析范围：当前工作区中的 `Android/src` 源码与仓库根目录说明文件

## 1. 结论

当前源码**不是 Kotlin Multiplatform（KMP）项目**，而是一个使用 Kotlin 和 Jetpack Compose 编写的**原生 Android 单平台应用**。

它不能在不改造的情况下直接编译为 iOS、macOS、桌面端或 Web 应用。仓库 README 虽然提供 Android、iOS 和 macOS 成品入口，但当前仓库中的可见应用源码只有 Android 版本；“产品发布到多个平台”并不能证明这些平台共享 KMP 源码。

从技术上讲，可以将项目逐步改造成 KMP/Compose Multiplatform 架构并复用部分业务代码，但这是一项架构迁移工作，而不是打开一个构建开关。模型推理、系统能力、存储、依赖注入及大量 UI 代码都需要抽象或提供平台实现。

## 2. 判断依据

### 2.1 构建系统是 Android Application，而不是 KMP

- `Android/src/settings.gradle.kts` 只包含一个 `:app` 模块。
- 根构建脚本应用的是 `com.android.application`、`org.jetbrains.kotlin.android` 和 Kotlin Compose 等插件。
- `Android/src/app/build.gradle.kts` 使用 `android { ... }` 配置，并定义 `applicationId`、`minSdk`、`targetSdk`、Android build type 和 Android Manifest 占位符。
- 版本目录 `Android/src/gradle/libs.versions.toml` 中声明了 `org.jetbrains.kotlin.android`，没有 `org.jetbrains.kotlin.multiplatform`。
- 没有 `kotlin { androidTarget(); iosArm64(); ... }` 之类的 KMP target 配置。

### 2.2 没有 KMP 源集

当前应用只有：

```text
Android/src/app/src/main/
├── AndroidManifest.xml
├── java/
├── proto/
└── res/
```

没有 KMP 项目典型的目录：

```text
commonMain/
commonTest/
androidMain/
iosMain/
desktopMain/
```

这意味着当前代码没有通过 `expect/actual` 或平台 source set 组织共享逻辑和平台实现。

### 2.3 源码深度依赖 Android API

当前快照约有 270 个 Kotlin 文件，其中约 234 个文件直接导入 `android.*` 或 `androidx.*`，约 163 个文件导入 AndroidX Compose。典型平台绑定包括：

- 应用入口：`Application`、`ComponentActivity`、Android Manifest。
- UI 与导航：AndroidX Compose、Navigation Compose、Activity Compose。
- 生命周期与状态：AndroidX Lifecycle、`ViewModel`。
- 依赖注入：Hilt Android、`@HiltViewModel`、`@AndroidEntryPoint`。
- 后台任务：WorkManager、前台服务。
- 数据存储：AndroidX DataStore。
- 设备能力：CameraX、录音、相册、日历、Intent、通知、开机广播。
- 服务能力：Firebase Analytics、Firebase Messaging、Google Play OSS Licenses、AICore。
- 本地推理：当前依赖和辅助类接收 Android `Context`、`Bitmap`，并使用 Android/设备侧原生库。

Android Manifest 也注册了 Activity、Service、Receiver、FileProvider，并声明了相机、录音、媒体、通知、前台服务等 Android 权限。这些能力在其他平台必须分别实现。

## 3. 当前技术栈与代码结构

### 3.1 核心技术栈

| 层面 | 当前实现 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 平台 | Android，最低 Android 12（API 31） |
| 架构支撑 | ViewModel、Kotlin Coroutines、Flow、Hilt |
| 导航 | AndroidX Navigation Compose |
| 配置/持久化 | DataStore、Protocol Buffers、Kotlin Serialization、Moshi/Gson |
| 模型推理 | LiteRT-LM、TensorFlow Lite、ML Kit GenAI 等 Android 侧依赖 |
| 多媒体 | CameraX、Android 音频和图片 API |
| 网络 | Ktor Android Client，另有 Hugging Face 集成 |
| 后台能力 | WorkManager、通知、广播接收器 |

注意：Kotlin、协程、Ktor 或声明式 Compose 的存在都不等同于 KMP。只有启用了 Kotlin Multiplatform 插件、配置多平台 target，并建立共享 source set，才属于 KMP 工程。

### 3.2 主要源码目录

`Android/src/app/src/main/java/com/google/ai/edge/gallery/` 下的主要职责如下：

| 目录/文件 | 职责 |
| --- | --- |
| `MainActivity.kt` | Android Activity 入口 |
| `GalleryApplication.kt` | Application 与 Hilt 初始化入口 |
| `GalleryApp.kt` | Compose 应用根组件 |
| `ui/navigation/` | 页面路由和导航图 |
| `ui/` | 首页、模型管理、聊天、单轮推理、基准测试及通用组件 |
| `data/` | 模型、任务、配置、下载与会话等数据结构/仓库 |
| `runtime/` | 模型初始化和推理封装，包括 AICore 实现 |
| `agent/` | Agent 请求、响应、执行器与会话管理 |
| `tools/` | Intent、JavaScript、MCP、Skill 等工具调用 |
| `mcp/`、`skills/` | MCP Server 与 Skill 管理 |
| `customtasks/` | Agent Chat、Mobile Actions、Tiny Garden、Scrapbook 等定制任务 |
| `di/` | Hilt 依赖注入模块 |
| `worker/` | 模型下载后台任务 |
| `notifications/` | 通知调度与系统广播 |
| `huggingface/` | Hugging Face API 与模型处理 |

### 3.3 推荐的首次阅读顺序

1. `MainActivity.kt`、`GalleryApplication.kt`：了解应用启动和依赖注入。
2. `GalleryApp.kt`、`ui/navigation/GalleryNavGraph.kt`：了解页面入口和导航。
3. `data/Tasks.kt`、`data/Model.kt`：理解任务与模型领域对象。
4. `ui/modelmanager/ModelManagerViewModel.kt`：理解模型管理和页面共享状态。
5. `runtime/LlmModelHelper.kt` 及其实现：理解模型加载、推理和释放。
6. `ui/llmchat/`、`ui/llmsingleturn/`、`ui/benchmark/`：按具体业务追踪调用链。
7. `customtasks/examplecustomtask/`：新增自定义任务时优先参考该示例。

## 4. 是否可以改造成跨平台项目

可以，但建议将目标理解为“逐步提取可共享核心”，而不是“整体直接转为 KMP”。

### 4.1 较适合共享的部分

- 不依赖 Android 类型的领域模型和规则。
- Prompt、会话、Agent、Tool 定义等纯 Kotlin 逻辑。
- Hugging Face API、下载协议及其他可使用 Ktor/`kotlinx.serialization` 实现的网络逻辑。
- 模型元数据解析、配置校验、任务编排等业务逻辑。
- 若采用 Compose Multiplatform，可评估复用部分纯 Compose UI，但需先移除 AndroidX 专属依赖。

### 4.2 必须做平台适配或重写的部分

- 模型推理后端及硬件加速。
- 相机、相册、麦克风、音频播放。
- 文件选择、文件路径和权限。
- 通知、后台下载、定时任务、开机广播。
- Android Intent 和日历操作。
- DataStore、Hilt、Firebase、AICore 等 Android 绑定能力。
- Android Navigation、Activity/Application 生命周期和系统 UI。

### 4.3 建议的目标结构

```text
project/
├── shared/                 # KMP library
│   └── src/
│       ├── commonMain/     # 领域模型、用例、接口、网络协议
│       ├── androidMain/    # Android actual/适配器
│       └── iosMain/        # iOS actual/适配器
├── androidApp/             # Android 应用壳和平台能力
└── iosApp/                 # iOS 应用壳和平台能力
```

建议先定义平台无关接口，例如 `InferenceEngine`、`FileStore`、`MediaPicker`、`AudioRecorder`、`NotificationScheduler` 和 `ModelDownloader`，再分别实现 Android/iOS 版本。不要在 `commonMain` 中传递 `Context`、`Bitmap`、`Uri` 或 Android `ViewModel`。

## 5. 推荐迁移路径

1. **先保持 Android 可持续开发**：不要一开始搬迁全部代码，先为现有功能补充关键测试和接口边界。
2. **建立独立 KMP `shared` 模块**：最初只加入纯 Kotlin 模型、解析和规则，不改 UI。
3. **提取数据与业务层**：将网络、仓库接口、Agent/Tool 核心逻辑移入 `commonMain`；平台存储和密钥能力通过接口注入。
4. **抽象推理层**：定义统一推理协议，但为各平台保留独立实现。首先验证目标模型格式、运行时和硬件加速在 iOS/macOS 上是否可用。
5. **决定 UI 策略**：
   - 追求最大代码共享：评估 Compose Multiplatform；
   - 追求平台原生体验和稳定生态：Android 保留 Jetpack Compose，iOS 使用 SwiftUI，只共享业务层。
6. **逐项迁移设备能力**：相机、音频、文件、通知、后台任务逐个建立平台适配，不建议一次重写。

## 6. 二次开发建议

- 如果当前目标仅为 Android 定制，继续使用现有 Android 架构成本最低，没有必要为了“可能的跨平台”立即引入 KMP。
- 新增纯业务逻辑时避免直接依赖 `Context` 和 Android UI 类型，为未来提取共享模块留出空间。
- 新增系统能力时先定义小接口，再在 Android 层实现，避免平台 API 扩散到领域层。
- 自定义 AI 场景可优先参考 `customtasks/examplecustomtask/`，并结合 `Task`、Hilt module 和导航注册方式接入。
- 模型推理是跨平台改造的最高风险项，应在正式迁移前做独立技术验证；UI 是否共享反而可以后置决定。

## 7. 最终判断表

| 问题 | 判断 |
| --- | --- |
| 当前是否使用 Kotlin？ | 是 |
| 当前是否使用 Jetpack Compose？ | 是 |
| 当前是否使用 KMP？ | 否 |
| 当前源码能否直接构建 iOS/macOS？ | 否 |
| 是否能通过改造实现跨平台？ | 可以，但需要分层、抽象平台能力并实现各平台后端 |
| 推荐立即全面迁移吗？ | 若近期只做 Android 二开，不推荐；若已有明确 iOS/macOS 交付计划，建议从共享业务层渐进迁移 |

