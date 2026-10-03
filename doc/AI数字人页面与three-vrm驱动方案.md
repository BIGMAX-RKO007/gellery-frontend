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

## 3. 分阶段落地步骤

- **阶段 1**：搭建前端工程 `vrm-avatar-web`，调通 Three.js + three-vrm 场景与模型渲染，暴露 JS 控制接口，实现离线打包同步。
- **阶段 2**：在 `voice-chat-app` 中集成 `WebView`，实现 `VrmAvatarView` Compose 组件并在真机上验证 60fps 流畅运行。
- **阶段 3**：打通 TTS 语音发音时的唇形同步（Viseme Lip Sync）与 AI 情绪表情切换。
- **阶段 4**：重构 VoxMate 页面布局，实现沉浸式数字人语音交互，支持用户导入自定义 `.vrm` 模型。
