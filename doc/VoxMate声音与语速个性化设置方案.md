# VoxMate 声音与语速个性化设置方案

记录日期：2026-10-09

## 1. 背景与目标

VoxMate 默认使用 Android 原生系统的 `TextToSpeech` 引擎进行语音合成。由于原生默认配置未配置音色筛选、语速与音调，导致语音偏慢、拖沓且存在明显的机械感。
为提升用户体验并将声音控制权交给用户，本项目实现了用户自定义的文字转语音（TTS）播报设置，包括：
1. **语速调节**（0.75x ~ 1.75x，默认推荐 1.10x）；
2. **音调调节**（0.75x ~ 1.50x，默认推荐 1.05x）；
3. **系统高保真音色筛选与选择**（支持自动优选高品质音色及列出设备已安装的所有音色）；
4. **即时试听发音**（边调边听）；
5. **本地配置持久化**（应用重启依然保持）；
6. **一键直达手机系统 TTS 设置**（方便用户安装高品质离线语音包）。

---

## 2. 核心架构与职责分工

```text
[UI 层]
VoxMateApp (顶部栏快捷按钮 🔊 / 侧边栏设置条目)
  └── VoiceSettingsSheet (Material 3 底部设置抽屉：滑块/单选卡片/试听)
        │
        ├──> VoiceSettingsRepository (SharedPreferences 持久化与状态发射)
        │
        └──> AndroidTextToSpeechOutput (底层系统 TTS API 调用与音色遍历)
```

### 关键文件清单

| 文件 | 职责说明 |
| :--- | :--- |
| `com.example.voxmate.voice.VoiceSettings` | 领域模型：包含语速、音调、音色唯一名及边界常量 |
| `com.example.voxmate.voice.VoiceSettingsRepository` | 持久化仓库：读写 SharedPreferences 并通过 StateFlow 提供配置 |
| `com.example.voxmate.voice.SpeechContracts` | 接口拓展：`SpeechOutput` 增加 `applySettings` 与 `availableVoices` |
| `com.example.voxmate.voice.AndroidTextToSpeechOutput` | 引擎适配：枚举系统音色、筛选高保真中文音色、动态设置 `setSpeechRate`、`setPitch`、`setVoice` |
| `com.example.voxmate.ui.settings.VoiceSettingsSheet` | UI 抽屉：提供滑块微调、音色单选、试听发音与直达系统设置入口 |
| `com.example.voxmate.ui.VoxMateApp` | 页面装配：在通话界面顶部栏与左侧抽屉暴露入口，管理弹窗生命周期 |

---

## 3. 音质提升技术细节

1. **语速与音调优化**：
   - 默认语速提升为 `1.10f`，有效消除中文合成中一字一顿的机械拖音，使语流轻快自然；
   - 默认音调提升为 `1.05f`，适配数字人形象，提升发音清亮感与亲和力。
2. **多音色评分与自动优选算法**：
   - 初始化时枚举底层 `engine.voices`；
   - 优先过滤与当前播报语言（中文 `zh`）匹配的音色；
   - 评分标准：非网络离线音色（+50分）、非常高质量（+40分）、高质量（+20分）、包含神经网络特征（+30分）、包含女性/自然特征（+15分）；
   - 用户选择“自动优选”时，引擎会自动选用评分最高的音色。
3. **系统设置联动**：
   - 针对不同机型厂商（如小米、华为、三星、Google），用户可通过应用内按钮一键打开系统“文字转语音”设置，下载最新离线神经网络语音包，即可在 VoxMate 中直接选用。

---

## 4. 验证方式

- 单元测试：`com.example.voxmate.voice.VoiceSettingsTest`
- 编译与打包：
  ```bash
  cd Android/src
  ./gradlew :voice-chat-app:assembleDebug
  ```
- 产物位置：`Android/src/voice-chat-app/build/outputs/apk/debug/voice-chat-app-debug.apk`
