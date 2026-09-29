# VoxMate 新应用模块说明

记录日期：2026-09-28

## 1. 产品名与模块

- 产品名：`VoxMate`
- 中文含义：声伴、语音伙伴
- Gradle 模块：`:voice-chat-app`
- 当前占位包名：`com.example.voxmate`
- 源码目录：`Android/src/voice-chat-app`

正式发布前需要把占位包名替换成开发者自己拥有的命名空间。应用商店发布后不建议再修改 `applicationId`。

## 2. 与原 Gallery App 的关系

原项目仍然使用 `:app`，VoxMate 是另一个独立的 Android Application：

```text
:app                         原 Google AI Edge Gallery

:voice-chat-app              新 VoxMate 语音聊天 App
 ├─ 依赖 :ai-core            本地 AI 对话
 ├─ 依赖 :model-download     模型列表和后台下载
 └─ 依赖 :model-manager-ui   Models 页面和模型选择
```

`:app` 不依赖 VoxMate，VoxMate 也不引用 Gallery 的页面、ViewModel 或导航代码，因此两个 App 可以分别运行和开发。

## 3. 当前已建立的内容

- 独立 Manifest、应用名称和启动 Activity。
- 独立 Compose 首页骨架。
- 麦克风权限声明。
- `SpeechInput` 接口：承接语音识别结果。
- `SpeechOutput` 接口：承接文字转语音播放。
- 已连接 `ai-core` 和 `model-download` 两个基础库。
- “选择模型并开始配置”已连接独立 Models 页面。
- VoxMate 首页和 Models 页面已完成英文/简体中文资源化。

- 已通过 `:voice-chat-app:assembleDebug` 构建验证。

## 4. 后续语音调用链

```text
用户说话
  → SpeechInput / Android SpeechRecognizer
  → AiChatSession.send(AiChatRequest)
  → AiChatEvent.TextDelta
  → 按句子缓冲输出文字
  → SpeechOutput / Android TextToSpeech
  → 手机扬声器播放
```

## 5. 开发入口

- 应用入口：`voice-chat-app/src/main/java/com/example/voxmate/MainActivity.kt`
- 首页：`voice-chat-app/src/main/java/com/example/voxmate/ui/VoxMateApp.kt`
- 语音契约：`voice-chat-app/src/main/java/com/example/voxmate/voice/SpeechContracts.kt`
- 模块说明：`voice-chat-app/README.md`

Android Studio 中选择 `voice-chat-app` 运行配置即可启动新 App，原 `app` 运行配置继续启动 Gallery。

## 6. 国际化（2026-09-29）

- 默认 `values/strings.xml` 使用英文。
- `values-zh-rCN/strings.xml` 提供简体中文。
- Manifest 通过 `android:localeConfig` 声明 `en` 和 `zh-CN`。
- Android 13 及以上可以在系统设置中为 VoxMate 单独选择语言。
- `model-manager-ui` 作为独立 Feature Library，维护自己的中英文资源；模型名称和模型原始描述仍由模型清单提供。
- 新增用户可见文案时必须同时补充默认英文和简体中文资源，禁止继续在 Composable 中硬编码。
