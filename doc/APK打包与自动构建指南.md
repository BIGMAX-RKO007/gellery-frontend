# VoxMate APK 打包与自动化构建指南

本文档记录 VoxMate Android 客户端（含 3D VRM 虚拟数字人与离线 LiteRT-LM 本地大模型）的 APK 打包方式与 CI/CD 自动化流水线配置。

## 1. 产物说明

- **默认打包目标**: `:voice-chat-app:assembleDebug`
- **产物文件**: `apk/VoxMate-v0.1.0-3D-Avatar.apk` (约 131MB)
- **体积构成说明**:
  - `LiteRT-LM` C++ 底层动态链接库（`liblitert_lm.so` 等，支持 arm64-v8a / x86_64）
  - 3D VRM 数字人预设模型（`assets/models/avatar.vrm`，约 10.7MB）
  - Three.js + three-vrm WebGL 渲染运行时资源（`assets/vrm-web/`）

> **注意**: `.apk` 二进制文件已在 `.gitignore` 中配置忽略，不进入 Git 仓库，避免仓库体积膨胀。

---

## 2. 本地一键打包

仓库根目录下提供了打包脚本 `scripts/build-apk.sh`：

```bash
# 赋予权限并执行打包
chmod +x scripts/build-apk.sh
./scripts/build-apk.sh
```

脚本会自动执行以下步骤：
1. 检查本地 JDK 17 环境。
2. 进入 `Android/src` 目录执行 `./gradlew :voice-chat-app:assembleDebug`。
3. 复制生成的 debug APK 到项目根目录 `apk/VoxMate-v0.1.0-3D-Avatar.apk`。

若直接使用 Gradle 命令行：
```bash
cd Android/src
./gradlew :voice-chat-app:assembleDebug
```
产物位置：`Android/src/voice-chat-app/build/outputs/apk/debug/voice-chat-app-debug.apk`。

---

## 3. 云端 CI/CD 自动打包

### 3.1 GitHub Actions
工作流文件位于 `.github/workflows/build-apk.yml`。
- **触发条件**: 推送代码至 `master` 或 `feat/*` 分支，或在 GitHub Actions 页面手动点击 `Run workflow`。
- **产物获取**: 构建成功后，可在对应 Workflow Run 页面的 **Artifacts** 区域直接下载 `VoxMate-v0.1.0-3D-Avatar.zip`（解包即可获得 APK）。

### 3.2 Gitee Go 流水线
配置文件位于 `.workflow/build.yml`。
- **触发条件**: 开启 Gitee Go 后，代码推送自动触发构建阶段。
- **执行逻辑**: 在容器环境中执行 `scripts/build-apk.sh` 并归档 `apk/` 下的构建产物。

---

## 4. 安装与测试指引

1. **安装**: 将 APK 发送至 Android 手机（Android 10+，推荐 8GB+ 内存以运行本地大模型），在文件管理器中点击安装。
2. **首次进入**:
   - 授予麦克风录音权限。
   - 页面全屏展示 3D 虚拟形象，支持触摸旋转/缩放视角。
   - 点击底部麦克风即可开始语音对话；或点击右侧小键盘切换文本聊天。
   - 左上角抽屉菜单可切换动作姿态、进入“模型管理”下载或导入 GGUF/LiteRT-LM 模型。
