package com.example.voxmate.voice

import kotlinx.coroutines.flow.Flow

/** Speech-to-text boundary. Android SpeechRecognizer can be the first implementation. */
interface SpeechInput {
  val events: Flow<SpeechInputEvent>
  fun startListening()
  fun stopListening()
  fun release()
}

sealed interface SpeechInputEvent {
  data class PartialText(val text: String) : SpeechInputEvent
  data class FinalText(val text: String) : SpeechInputEvent
  data class Error(val message: String) : SpeechInputEvent
}

interface SpeechOutput {
  /** 设置播报语言；默认实现保持引擎原语言，auto 表示设备默认语言。 */
  fun setLanguage(language: String) = Unit

  /** 动态更新语速、音调及选中的音色偏好。 */
  fun applySettings(settings: VoiceSettings) = Unit

  /** 暴露底层引擎当前检测到的可用系统/模型音色列表。 */
  val availableVoices: Flow<List<VoiceOption>> get() = kotlinx.coroutines.flow.emptyFlow()

  val events: Flow<SpeechOutputEvent>
  fun speak(text: String, queue: Boolean = false)
  fun stop()
  fun release()
}

sealed interface SpeechOutputEvent {
  data object Started : SpeechOutputEvent
  data object Completed : SpeechOutputEvent
  data class Error(val message: String) : SpeechOutputEvent
}
