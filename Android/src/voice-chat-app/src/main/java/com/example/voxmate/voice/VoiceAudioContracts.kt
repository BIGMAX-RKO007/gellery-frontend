package com.example.voxmate.voice

import kotlinx.coroutines.flow.Flow

/** 持续录音与自动断句边界，不负责识别文字或依赖具体音频模型。 */
interface VoiceAudioInput {
  /** 持续输出麦克风、起声和完整语句事件的只读事件流。 */
  val events: Flow<VoiceAudioInputEvent>

  /** 在取得麦克风权限后开始监听用户语音。 */
  fun startListening()

  /** 停止当前语音监听，但保留后续再次启动所需资源。 */
  fun stopListening()

  /** 永久释放录音资源；释放后不得再次启动。 */
  fun release()
}

/** 表示一次持续录音过程中可能产生的应用层事件。 */
sealed interface VoiceAudioInputEvent {
  /** 已检测到持续声音活动，可以立即停止 AI 和 TTS。 */
  data object SpeechStarted : VoiceAudioInputEvent

  /** 当前录音会话是否成功启用设备声学回声消除。 */
  data class EchoCancellation(val enabled: Boolean) : VoiceAudioInputEvent

  /** 录音器已经打开麦克风并可以持续接收语音。 */
  data object Listening : VoiceAudioInputEvent

  /** VAD 已完成一段语句，可直接提交给支持音频的模型。 */
  data class TurnReady(val turn: VoiceTurn) : VoiceAudioInputEvent

  /** 不包含 Token 等敏感信息的录音错误说明。 */
  data class Error(val message: String) : VoiceAudioInputEvent
}

/** VAD 完成的一段发言，仅驻留内存，由转写任务独占使用。 */
data class VoiceTurn(
  /** 归一化单声道音频，数值范围为 [-1, 1]。 */
  val samples: FloatArray,
  /** 实际采样率，单位 Hz。 */
  val sampleRate: Int,
)
