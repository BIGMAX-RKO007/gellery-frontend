package com.example.voxmate.speech

/** 一次端侧语音识别的输入，音频样本不会被实现持久化。 */
data class SpeechRecognitionRequest(
  /** 归一化到 [-1, 1] 的单声道 PCM 样本。 */
  val samples: FloatArray,
  /** 输入音频采样率，单位 Hz。 */
  val sampleRate: Int,
  /** `auto` 或模型支持的 ISO 语言代码。 */
  val language: String = "auto",
)

/** 一次成功识别得到的最终文本及模型检测信息。 */
data class SpeechTranscript(
  /** 已完成逆文本标准化、可以直接发送给语言模型的文字。 */
  val text: String,
  /** 模型检测出的语言代码；模型未提供时为空。 */
  val detectedLanguage: String = "",
)

/**
 * 与具体端侧识别框架隔离的完整语句识别边界。
 *
 * 实现必须在后台线程加载和推理；调用方不再使用时必须调用 [close]。
 */
interface SpeechRecognitionEngine {
  /** 提前加载并预热识别模型，重复调用安全。 */
  suspend fun prepare()

  /** 将一段完整语句转换为最终文字。 */
  suspend fun transcribe(request: SpeechRecognitionRequest): SpeechTranscript

  /** 释放模型和原生资源，释放后不得再次识别。 */
  fun close()
}
