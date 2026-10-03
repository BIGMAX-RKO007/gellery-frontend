# AI 数字人页面与 three-vrm 驱动方案

记录日期：2026-10-04

## 1. 目标与定位

在 VoxMate（`:voice-chat-app`）中引入 3D AI 数字人界面，使端侧语音助手具备生动的视觉化身（Avatar）：
- 前端使用 Web 技术栈：**Three.js + @pixiv/three-vrm** 渲染 3D 虚拟人物（VRM 规范）。
- 离线全闭环：前端工程打包为静态资产置于 Android `assets/vrm-web/` 目录，通过硬件加速 `WebView` 离线加载，不依赖外网。
- AI 驱动链路：`:ai-core`（端侧 LiteRT-LM）推理 ➔ 流式输出与情绪提取 ➔ Android TTS 语音合成 ➔ 实时音频振幅/音素 ➔ `VrmJsBridge` 驱动 VRM 嘴型（Lip Sync）、表情与呼吸微动。

## 2. 模块边界与架构

```text
Android/src/voice-chat-app/
  ├── src/main/assets/vrm-web/       前端打包静态产物（HTML/JS/3D模型）
  ├── src/main/java/com/example/voxmate/
  │    ├── ui/avatar/VrmAvatarView.kt   Compose WebView 容器与生命周期管理
  │    └── bridge/VrmJsBridge.kt        Native ↔ Web 双向通信契约

vrm-avatar-web/                      独立前端工程（Vite + TypeScript）
  ├── src/
  │    ├── scene/                     Three.js 场景、相机、光照与渲染器
  │    ├── vrm/                       VRM 模型加载、眨眼、呼吸与表情控制器
  │    └── bridge/                    挂载 window.avatarController 接口
  ├── package.json
  └── vite.config.ts
```

## 3. 分阶段落地与实施记录

### 阶段 1：前端工程 `vrm-avatar-web` 搭建【已完成】
- 采用 Vite + TypeScript + Three.js 0.174 + `@pixiv/three-vrm` 3.3.6。
- 实现了 `SceneManager`（透视相机、平行光、环境光、`ResizeObserver` 自适应）、`VrmManager`（VRM 1.0 模型加载、自然待机呼吸、随机眨眼、BlendShape 嘴型与情绪控制）。
- 通过 `sync-to-android.sh` 一键构建并同步到 `Android/src/voice-chat-app/src/main/assets/vrm-web/`。
- **关键经验**：VRM 1.0 规范中模型默认朝向 +Z 面向相机，不需要也不应执行 `scene.rotation.y = Math.PI`（VRM 0.0 需要，1.0 不需要，否则模型会背对镜头）。

### 阶段 2：Android Native WebView & Compose 集成【已完成并在真机验证】
- 封装 `VrmAvatarView.kt` Compose 组件，使用 `WebViewAssetLoader` 拦截 `https://appassets.androidplatform.net/assets/` 安全高效加载本地离线资源。
- 注入 `VrmBridgeController`（实现 `@JavascriptInterface` 与线程安全的 `evaluateJavascript` 调度）。
- **关键经验**：高通骁龙 8 Gen 1（Adreno 730）在 WebView 透明背景与 WebGL `alpha: true` 组合下会触发 `OpenGLRenderer: Unable to match the desired swap behavior` 导致花屏或白屏。采用不透明底色与 `alpha: false` 彻底解决渲染稳定性问题。

### 阶段 3：TTS 语音合成与实时唇形/情绪联动【已完成并在真机验证】
- `AndroidSpeechOutput.kt`：封装 Android 系统原生 `TextToSpeech` 引擎，通过 `UtteranceProgressListener` 发送 `SpeechEvent.Started`、`SpeechEvent.Completed`、`SpeechEvent.Error`。
- `LipSyncDriver.kt`：挂载 TTS 播放事件，在发声期间以 ~30fps 节拍生成自然韵律变化的元音音素（`speak(volume)`，范围 0.15~0.90），语音结束时柔和归零闭嘴。
- `EmotionParser.kt`：解析对话文本中的情绪标签（如 `[happy]你好！`），提取出纯文本交给 TTS 播报，同时自动驱动数字人对应的表情 BlendShape。
- **真机验证结果**：在 Sony Xperia 1 IV（Android 14）真机上验证，点击发音按钮后，数字人同步展现开朗微笑表情、自然开合嘴型配合 TTS 语音输出，播报完成后自然闭嘴复位。

### 阶段 4：沉浸式数字人语音交互页面重构与端侧模型串联【下一步】
- 重构 `VoiceChatHome` 页面布局：上方全屏/半屏沉浸式 3D 数字人视口，下方浮动对话气泡与语音交互控制器。
- 将端侧模型推理（`:ai-core`）流式 Token 输出接入 `EmotionParser` + `AndroidSpeechOutput` + `LipSyncDriver`，实现完整的端到端语音数字人对话。

