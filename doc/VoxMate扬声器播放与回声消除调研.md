# VoxMate 扬声器播放与回声消除调研

日期：2026-10-05。此次为源码分析与方案调研，未修改音频运行代码。

## 结论

可以用扬声器与 AI 实时通话，但外放声音会被麦克风收到。核心不是更换 ASR 模型，而是让采音管线消除播放回声、保留用户插话。不能仅提高音量或改成媒体播放后就认定解决。
推荐先补齐 Android 通话音频路由和设备 AEC 状态，再真机评估；设备 AEC 不够稳定时升级到受控 PCM 播放和 WebRTC 软件 AEC。

## 当前源码事实

- AndroidTextToSpeechOutput.kt:160 使用 USAGE_VOICE_COMMUNICATION，语音内容类型是 SPEECH；submit 调用系统 TTS speak，参数为 Bundle.EMPTY，没有人为设置低播报音量。
- 运行代码中未发现 AudioManager 通话模式和 setCommunicationDevice，没有明确选择扬声器。由系统/设备决定实际路径，与用户反映的听筒播放一致，但没有运行路由日志，不能仅凭源码断定全部声音小的原因。
- AudioFrameSource.kt:40 采音使用 VOICE_COMMUNICATION，已尝试启用 AcousticEchoCanceler 和 NoiseSuppressor，采集为 16 kHz、单声道 PCM。
- AEC 是否成功通过 EchoCancellation 事件上报；AvatarCallController.kt:105 当前忽略该事件。
- 控制器收到 SpeechStarted 就 interrupt，无法判断 VAD 检到的是用户还是扬声器回声。神经 VAD 判断是否有语音，不是识别人声来自哪个说话者。
- 已有 500ms 前滚与持续采音可保护起声；但无法恢复被 AEC/降噪抑制掉的字。
- Manifest 未声明 MODIFY_AUDIO_SETTINGS，新增路由控制需添加。原 app 不涉及本次分析。

## 官方和其他厂商做法

1. Android 提供通信设备选择接口：API 31 起，可在可用通信设备中选择内置扬声器，检查返回值，并在结束/离开时清除请求。通话应用使用 MODE_IN_COMMUNICATION，不使用运营商电话专用 MODE_IN_CALL。
   [AudioManager 官方文档](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo))
2. Android AEC 挂接录音器 session，旨在消除录音中的远端播放贡献。设备可能默认启用、拒绝创建或效果不同，enabled 只是配置状态，不是回声已完全消失的证明。
   [AcousticEchoCanceler 官方文档](https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler)
3. LiveKit 的 Android 录音选项有 echoCancellation、noiseSuppression、autoGainControl；官方建议保持其 WebRTC 回声/降噪处理开启。它不是靠关闭麦克风获得同时听说。
   [Android 录音配置](https://docs.livekit.io/reference/client-sdk-android/livekit-android-sdk/io.livekit.android.room.track/-local-audio-track-options/index.html)
   [LiveKit 回声和噪声处理](https://docs.livekit.io/transport/media/noise-cancellation/)
4. 声网 Agora 的公开发布说明描述 AIAEC 对近端人声保留和双讲的改进。说明外放全双工需要专门的音频处理，而不仅是路由开关；这是厂商描述，不是本项目实测效果，也不表示应引入其付费/联网 SDK。
   [Agora 官方发布记录](https://docs.agora.io/en/realtime-media/rtc/reference/release-notes)
5. WebRTC APM 分别处理采集流和播放参考流。可作为端侧软件 AEC 的技术路线；不是必须把声音上传服务器，也不是只调用本地 VAD 就具有软件 AEC。
   [WebRTC APM 主接口源码](https://webrtc.googlesource.com/src/+/refs/heads/main/api/audio/audio_processing.h)

以上仅据公开资料，不猜测微信或其他闭源 App 的内部实现。

## 第一阶段：针对当前实现的低改动方案

- 新增应用层 AudioRouteController，独立管理通信模式、设备选择、路由监听、音频焦点及释放。UI 只上报事件，录音/TTS 不各自竞争路由。
- 无耳机时默认内置扬声器；连接耳机时优先保护耳机路由，不强行外放泄露对话，可提供用户明确选择。
- 保留 TTS 通信用途和采音 VOICE_COMMUNICATION，配置 MODE_IN_COMMUNICATION，通过 availableCommunicationDevices 选择 TYPE_BUILTIN_SPEAKER；检查返回值和实际 communicationDevice。
- 页面前台通话生命周期内稳定持有路由，不按每个 TTS 短句反复改模式；退出、后台或被其他通话抢占时停止音频并释放自己申请的设备/焦点，避免影响其他 App。
- 手机音量键对应通话音量，不悄悄把系统音量拉到最大。路由、通话音量和 TTS 信号音量分开诊断。
- 把 AEC 可用/启用状态纳入通话状态与诊断。失败时显示提示，提供耳机或手动打断的兼容方式，不宣称支持稳定自动插话。
- 继续持续录音和前滚，不在 AI 播报时停麦；保留插话流程，但应以消回声后的语音起声为输入。残余回声保护需调参和真机验证，不能只调高阈值或延长确认时间，否则可能漏轻声和增加延迟。
- 如果设备 AEC 已足够好，无需新模型、云端服务或完整 RTC SDK。

本阶段能修正播放路径，不能保证所有设备、所有音量下零误打断。AEC 开关成功也必须通过实际双讲测试判断。

## 第二阶段：更可控的跨设备方案

### 第一阶段代码落地（2026-10-05）

- `voice/CallAudioRoute.kt` 定义可替换接口、状态和纯设备选择策略；`AndroidCallAudioRoute.kt` 实现 Android 12+ 通信设备选择及音频焦点。没有耳机时选内置扬声器，有耳机时优先耳机，不改用户系统音量。
- 系统实际设备确认成功后才开放录音和文字发送；焦点丢失暂停采音和播报，路由失败可主动重试。页面后台或导航退出清理自己申请的设备、焦点与监听。仍持有焦点时恢复原音频模式，避免在其他通话抢占时覆盖全局模式。
- `ui/avatar/CallAudioVolumeKeys.kt` 将实体音量键绑定通信音量，退出恢复；`CallAudioNotice.kt` 独立渲染路由状态和故障，中文及英文资源已添加。
- `AvatarCallController` 展示采音端报告的 AEC 启用状态，未启用时提示使用耳机。AEC 和降噪初始化独立，单项失败不阻断另一项。保留持续采音、500ms 前滚和插话打断，不在 AI 发声时停麦，不改变识别阈值。
- 新增正常权限 `MODIFY_AUDIO_SETTINGS`。不增加录音上传，不修改原 Gallery，也不引入新依赖或软件 AEC。
- 验证命令：在 `Android/src` 执行 `gradlew.bat :voice-chat-app:testDebugUnitTest :voice-chat-app:assembleDebug --offline`。新增设备策略测试覆盖扬声器优先、耳机优先、当前耳机保留、拔出回退和无目标设备。
- APK：`Android/src/voice-chat-app/build/outputs/apk/debug/voice-chat-app-debug.apk`。软件构建不能验证声学效果；必须按本文真机清单验证扬声器实际路由、回声和插话，设备 AEC 启用不等于保证所有 TTS 引擎都能消除回声。
- 本次验证结果：13 项单元测试全部通过，`assembleDebug` 成功，`git diff --check` 无空白错误。真机声学验收尚未执行。

若外放仍发生 AI 自我打断、用户轻声被吞或首字遗漏，增加可替换 AudioDuplexProcessor 与 PcmSpeechOutput：

    TTS 合成 PCM → 自己的播放器/AudioTrack → 扬声器
                   ↘ 实际播放参考与时序 → WebRTC AEC
    麦克风 PCM → WebRTC AEC → 适度降噪 → VAD → SenseVoice → AI

关键点：

- 同时输入真实播放参考和录音，不用回复文字充当音频参考，也不能把普通降噪当成 AEC。
- 对齐采样格式、帧长、播放队列、音量和延迟；WebRTC APM 通常处理约 10ms 的 PCM 帧，当前 20ms 采集需做适配。
- 打断时同时停播放、丢弃待播队列并正确更新播放参考；保留持续麦克风和前滚。
- 设备 AEC 与软件 AEC 选择适当后端，不默认盲目叠加，避免过度处理削弱用户声音。
- 当前系统 TTS speak 的播放由服务负责，现有封装未提供受控播放 PCM。Android onAudioAvailable 可以拿到合成音频，但合成回调不等于实际播放时刻。需要扩展合成/播放接口与对齐，而不是简单加监听器就完成软件 AEC。
  [Android TTS 音频回调](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener#onAudioAvailable(java.lang.String,%20byte[]))
- 可继续使用系统 TTS 做合成，或以后接人物声音引擎；两者输出同一 PCM 接口。无需改变 ai-core 或 persona。
- WebRTC 原生依赖会增加构建和兼容工作；应验证许可证、ABI、体积、CPU 与目标手机效果后决定引入。

## 不建议的捷径

- 改 USAGE_MEDIA 追求更响，忽略通话路由与 AEC 参考兼容性。
- AI 说话时关闭麦克风：会破坏用户要求的随时插话。
- 只对识别文字和 AI 文本做相似度过滤：事后可以减少某些误发送，但起声时已停播，且无法区分用户重复/引用 AI 的话。
- 单纯调高 VAD 门槛：容易误伤轻声、短词和“你是谁”的首字。
- 单纯提高麦克风增益/扬声器最大音量：可能同时放大回声和失真。

## 真机验证与隐私

先记录实际播放设备、通话模式、音量级别、AEC 状态、帧时序、起声到停播耗时，不保存录音或对话正文。
如需保存调试音频，必须经用户明确授权，仅本地临时保存并清理。

测试：仅 AI 发声时不误打断；正常音量和轻声插话“你是谁”“你好”“等一下”；安静房间和回响房间；中/高外放音量；耳机插拔；键盘切换；后台返回；其他电话抢占；挂断后其他 App 音频恢复。
测试指标包括误打断次数、漏首字比例、用户语音识别正确率和起声到停播延迟。无连接真机，不给出未经测量的可靠性承诺。
