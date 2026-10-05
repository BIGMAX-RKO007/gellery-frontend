# VoxMate 数字人首页自动通话

日期：2026-10-05。目标分支：feat/majia/new_play，来源为 master 的端侧语音管线。

## 行为

- 首页的本地大模型就绪后申请麦克风权限，页面前台且处于语音模式时自动采音。
- 小圆形波形按钮替代绿色“开始语音”按钮；波形表示采音、说话或处理状态，不是声压仪。
- Silero 检测一句话结束后，SenseVoice 转写为文字，交给当前 AiChatSession。
- 生成文字按完整短句交给系统 TTS 排队播放，继续驱动数字人口型和情绪表情。
- 用户起声即停止 TTS、取消旧生成和转写；新问题等待旧生成退出后提交，防止并发生成。
- 点击键盘同步停止采音；切回麦克风模式恢复自动监听。文字和语音共用当前对话历史。
- 进入后台、模型管理页或离开页面停止录音和生成，页面销毁释放独占资源。

## 解耦

AvatarCallController 负责通话、转写、生成、播报和取消时的晚到事件隔离。
VoxMateApp 连接权限、生命周期、模型状态和数字人桥接。
CallAudioButton 只渲染波形并上报重试事件，不持有录音器。

从 master 迁移 AudioFrameSource、ContinuousVoiceAudioInput、UtteranceDetector、
VoiceInputFactory、StreamingSentenceSegmenter、AndroidTextToSpeechOutput 及独立
speech-recognition 模块。保留当前分支数字人、抽屉、姿态、模型管理和应用导航结构。
未整体合并 master 的旧首页、人物模块或聊天控制器。

## 资源与国际化

新增文案位于 values 和 values-zh-rCN。SenseVoice 使用自动语言检测，TTS 跟随系统语言。
模型继续内置 APK，应用用户无需另行下载。model.int8.onnx 不提交 Git；其他开发环境和
流水线需要预先放入 speech-recognition/src/main/assets/speech/sensevoice。
SHA-256：C71F0CE00BEC95B07744E116345E33D8CBBE08CEF896382CF907BF4B51A2CD51。
VAD 模型、词表和许可证随源码保存。依赖版本在版本目录集中管理，JitPack 限定 sherpa 组。

## 验证与限制

构建命令：

```text
gradlew.bat :speech-recognition:testDebugUnitTest :speech-recognition:assembleDebug :voice-chat-app:testDebugUnitTest :voice-chat-app:assembleDebug
```

控制器单元测试覆盖自动开始、文字模式停麦、晚到录音不发送、起声停播和生成串行。
本次执行结果：识别库测试、AAR 打包、VoxMate 的 4 项通话控制器测试和 APK 打包全部通过。
额外覆盖文字模式进入后台时停止生成，以及跨增量情绪标签不被朗读、不丢正文。
最终 APK 位于 Android/src/voice-chat-app/build/outputs/apk/debug/voice-chat-app-debug.apk。
已检查 APK 内完整 SenseVoice、词表、VAD 和 arm64 sherpa 原生库，并校验包内 SenseVoice
哈希与记录一致；APK 大小为 424,230,571 字节。当前没有连接调试设备，未执行真机验收。
真机仍需验收：授权/拒绝权限、连续多轮、AI 播报时插话、键盘/麦克风切换、
后台/前台、模型切换、扬声器和耳机模式。设备 AEC 能力不同，VAD 不能完全区分回声
和真正用户发言；构建成功不代表声学效果已经验证。
