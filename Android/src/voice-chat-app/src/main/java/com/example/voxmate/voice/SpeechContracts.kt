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

/** Text-to-speech boundary. Android TextToSpeech can be the first implementation. */
interface SpeechOutput {
  /** 设置播报语言；默认实现保持引擎原语言，auto 表示设备默认语言。 */
  fun setLanguage(language: String) = Unit
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
