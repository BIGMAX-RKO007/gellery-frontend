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

### 阶段 3.5：姿态动作引擎与 25 动作控制台集成【已完成并在真机验证】
- **姿态定义库**：在 `posePresets.ts` 中针对参考图的 25 个精选地面与互动姿态（跪坐、猫爬、趴卧、侧卧托腮、盘坐、正坐等），建立了基于 VRM Humanoid 标准人形骨骼的角度与 Hips 高度映射字典。
- **平滑插值引擎**：封装 `PoseManager.ts`，基于 `THREE.MathUtils.lerp` 在约 0.35s 内实现骨骼旋转角度与 Hips 根位置的柔和阻尼过渡，防止突变抽搐。
- **UI 布局重组**：在 Compose 界面“AI 对话核心”卡片下方的原空白区域，开辟了 5×5 动作控制矩阵（编号 1 ~ 25），点击任意编号即时高亮并向数字人下发动作，右上角提供“站姿复位”。
- **真机验证**：在真机上验证多组动作切换（如 #2 猫爬回眸、#3 跪坐前倾、#20 抱胸屈坐等），模型均能平滑流畅变换全身姿态，触控响应迅速。

### 阶段 4：沉浸式数字人语音交互页面重构与全链路双模式闭环【已完成并在真机验证】
- **沉浸式交互架构（VoxMateApp.kt）**：
  - 顶部 3D 数字人视口卡片：支持「半身 / 全身 / 特写」视角切换、状态标签指示（如“正在发声...”、“正在思考...”、“正在聆听...”）、姿态展开切换与一键微笑。
  - 中部多轮会话气泡流：支持用户（蓝色）与数字人助手（毛玻璃白卡）多轮对话，流式打字机逐字渲染并自动滚屏。
  - 右下角折叠式动作抽屉：浮动动作按钮展开 5×5 姿态矩阵，既保留全部 25 动作调试能力，又使聊天主视口干净清爽。
  - 底部语音/文本双模输入条：大麦克风按钮提供触控录音与状态动效，支持随时切换为键盘文本输入框。
- **免下载即时智能伴侣模式（CompanionAiChatSession.kt）**：
  - 为避免用户或开发者因未预先下载 2GB+ 大模型权重而无法测试体验，设计了即时免下载伴侣模式。
  - 支持打字机流式输出与智能问答，自动附带情绪标签（如 `[happy]`、`[relaxed]`），零门槛即测即用。
- **真实端侧 LiteRT-LM 推理模式**：
  - 在已下载本地 `.bin` / `.task` 权重时，可通过右上角模型管理选择真实本地模型，平滑接入 `:ai-core` 的端侧大模型推理。
- **原生语音输入识别（AndroidSpeechInput.kt）**：
  - 基于 Android 原生 `SpeechRecognizer`，实现全双工语音输入，实时返回部分文本（PartialText）并在说话结束时发射最终文本（FinalText），自动触发伴侣会话。
- **真机验证**：
  - 在 Sony Xperia 1 IV（SO-51C, Android 14）真机上验证语音识别（录入“你今年”）、打字机流式回复、TTS 语音播报、30fps 实时唇形与微笑联动全部正常走通。


