# VoxMate（声伴）

VoxMate 是新的独立语音聊天 Application 模块，Gradle 模块名为 `:voice-chat-app`。原 Gallery `:app` 不依赖本模块，两者可以分别开发和运行。

## 当前基础

- 已依赖 `:ai-core`，用于本地 LiteRT-LM 流式对话。
- 已依赖 `:model-download`，用于模型清单和后台断点续传。
- 已依赖 `:model-manager-ui`，用于独立 Models 页面和模型选择。
- 已建立独立 Compose 首页。
- 已定义 `SpeechInput` 与 `SpeechOutput`，后续可先接 Android `SpeechRecognizer` 和 `TextToSpeech`，再按需替换成离线语音框架。
- 已提供英文和简体中文资源，并声明 Android 13+ 应用语言列表。

## 国际化

- 默认文案：`src/main/res/values/strings.xml`（英文）
- 简体中文：`src/main/res/values-zh-rCN/strings.xml`
- 支持语言声明：`src/main/res/xml/locales_config.xml`

系统语言为简体中文时自动显示中文，否则显示默认英文。Android 13 及以上还可以在系统的应用语言设置中单独选择 VoxMate 的语言。

## 运行

在 Android Studio 的运行配置中选择 `voice-chat-app`，或执行：

```text
gradlew :voice-chat-app:installDebug
```

当前包名为占位值 `com.example.voxmate`。正式发布前应替换为你拥有的域名对应包名，因为应用商店发布后不宜再修改 applicationId。

## 下一步

1. 完成麦克风权限请求和 Android SpeechRecognizer 实现。
2. 完成 Android TextToSpeech 实现。
3. 增加 Hugging Face 登录或 Token 设置入口。
4. 用已选择模型的本地路径创建 `AiChatSession`。
5. 串起“说话 → STT → AI 流式回答 → TTS 播放”的状态机。
