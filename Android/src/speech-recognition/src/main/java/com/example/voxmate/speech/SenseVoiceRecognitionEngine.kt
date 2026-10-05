package com.example.voxmate.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 使用 sherpa-onnx 与 APK 内置 SenseVoiceSmall INT8 的完整语句识别器。
 *
 * @param context 用于读取合并后的 Android assets，只保存 Application Context
 * @param dispatcher 模型加载与推理调度器，测试可替换
 */
class SenseVoiceRecognitionEngine(
  context: Context,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SpeechRecognitionEngine {
  /** 只持有 Application Context，避免页面销毁后泄漏 Activity。 */
  private val applicationContext = context.applicationContext

  /** 等待进行中的 native 推理退出后执行资源释放。 */
  private val cleanupScope = CoroutineScope(SupervisorJob() + dispatcher)

  /** 串行保护模型初始化、配置切换、推理和释放。 */
  private val mutex = Mutex()

  /** 释放状态，禁止关闭后重新创建原生模型。 */
  private val closed = AtomicBoolean(false)

  /** sherpa-onnx 原生识别器，只在 [mutex] 内访问。 */
  private var recognizer: OfflineRecognizer? = null

  /** 当前识别器采用的语言配置，避免每段语音重复更新。 */
  private var configuredLanguage = "auto"

  /** 在后台创建识别器；模型已经就绪时直接返回。 */
  override suspend fun prepare() {
    withContext(dispatcher) {
      mutex.withLock {
        check(!closed.get()) { "Speech recognizer is closed." }
        if (recognizer == null) {
          recognizer = OfflineRecognizer(applicationContext.assets, createConfig(configuredLanguage))
        }
      }
    }
  }

  /** 串行完成一次离线识别，输入音频和结果都只保存在内存。 */
  override suspend fun transcribe(request: SpeechRecognitionRequest): SpeechTranscript =
    withContext(dispatcher) {
      require(request.samples.isNotEmpty()) { "Speech samples must not be empty." }
      require(request.sampleRate > 0) { "Sample rate must be positive." }
      val language = normalizeSenseVoiceLanguage(request.language)
      mutex.withLock {
        check(!closed.get()) { "Speech recognizer is closed." }
        val activeRecognizer =
          recognizer ?: OfflineRecognizer(applicationContext.assets, createConfig(language)).also {
            recognizer = it
            configuredLanguage = language
          }
        if (configuredLanguage != language) {
          activeRecognizer.setConfig(createConfig(language))
          configuredLanguage = language
        }
        val stream = activeRecognizer.createStream()
        try {
          stream.acceptWaveform(request.samples, request.sampleRate)
          activeRecognizer.decode(stream)
          val result = activeRecognizer.getResult(stream)
          SpeechTranscript(
            text = result.text.trim(),
            detectedLanguage = result.lang,
          )
        } finally {
          stream.release()
        }
      }
    }

  /** 立即禁止新请求，并在后台等待推理离开临界区后释放原生识别器。 */
  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    cleanupScope.launch {
      try {
        mutex.withLock {
          recognizer?.release()
          recognizer = null
        }
      } finally {
        cleanupScope.cancel()
      }
    }
  }

  /** 创建不向上层暴露 sherpa-onnx 类型的模型配置。 */
  private fun createConfig(language: String): OfflineRecognizerConfig =
    OfflineRecognizerConfig(
      modelConfig =
        OfflineModelConfig(
          senseVoice =
            OfflineSenseVoiceModelConfig(
              model = MODEL_ASSET,
              language = language,
              useInverseTextNormalization = true,
            ),
          tokens = TOKENS_ASSET,
          numThreads = 2,
          provider = "cpu",
        ),
      decodingMethod = "greedy_search",
    )

  /** SenseVoice 模型文件在 APK assets 中的固定位置。 */
  private companion object {
    /** INT8 模型资源路径。 */
    const val MODEL_ASSET = "speech/sensevoice/model.int8.onnx"

    /** 模型词表资源路径。 */
    const val TOKENS_ASSET = "speech/sensevoice/tokens.txt"
  }
}

/** 把应用语言选项收敛为 SenseVoice 支持的稳定值。 */
internal fun normalizeSenseVoiceLanguage(language: String): String =
  when (language.lowercase()) {
    "zh", "en", "yue", "ja", "ko" -> language.lowercase()
    else -> "auto"
  }
