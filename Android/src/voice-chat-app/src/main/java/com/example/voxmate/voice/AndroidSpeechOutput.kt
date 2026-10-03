package com.example.voxmate.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * 基于 Android 原生 [TextToSpeech] 的语音播放实现。
 *
 * 职责：
 * 1. 管理系统 TTS 引擎生命周期与多语言初始化。
 * 2. 播报传入的文本，并通过 [SpeechOutputEvent] 流上报发音开始、结束与异常状态。
 *
 * 线程约束：
 * 初始化与方法调用应在主线程执行，事件通过协程分发。
 */
class AndroidSpeechOutput(
  context: Context,
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : SpeechOutput {

  companion object {
    private const val TAG = "AndroidSpeechOutput"
  }

  /**
   * 语音输出事件流，用于驱动唇形同步和 UI 状态。
   */
  private val _events = MutableSharedFlow<SpeechOutputEvent>(extraBufferCapacity = 16)
  override val events: SharedFlow<SpeechOutputEvent> = _events.asSharedFlow()

  /**
   * 系统 TTS 实例。
   */
  private var tts: TextToSpeech? = null

  /**
   * 标记 TTS 引擎是否已成功初始化。
   */
  private var isInitialized = false

  init {
    tts = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        val result = tts?.setLanguage(Locale.CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          // 若不支持中文则回退到当前系统默认语言
          tts?.language = Locale.getDefault()
        }
        isInitialized = true
        setupUtteranceListener()
        Log.d(TAG, "TTS 引擎初始化就绪")
      } else {
        Log.e(TAG, "TTS 引擎初始化失败，错误码: $status")
        emitEvent(SpeechOutputEvent.Error("TTS 引擎初始化失败: $status"))
      }
    }
  }

  /**
   * 注册播报进度监听器，跟踪语音发音时机。
   */
  private fun setupUtteranceListener() {
    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {
        emitEvent(SpeechOutputEvent.Started)
      }

      override fun onDone(utteranceId: String?) {
        emitEvent(SpeechOutputEvent.Completed)
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        emitEvent(SpeechOutputEvent.Error("播报发生错误"))
      }

      override fun onError(utteranceId: String?, errorCode: Int) {
        emitEvent(SpeechOutputEvent.Error("播报发生错误, 错误码: $errorCode"))
      }
    })
  }

  /**
   * 合成并播放指定文本。
   *
   * @param text 欲发音的纯文本内容。
   * @param queue 是否排队播放；为 false 时会打断当前正在播放的语音。
   */
  override fun speak(text: String, queue: Boolean) {
    if (!isInitialized || tts == null) {
      Log.w(TAG, "TTS 尚未初始化完成，放弃播放")
      return
    }
    val queueMode = if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
    val utteranceId = UUID.randomUUID().toString()
    tts?.speak(text, queueMode, null, utteranceId)
  }

  /**
   * 停止当前所有语音播放。
   */
  override fun stop() {
    tts?.stop()
    emitEvent(SpeechOutputEvent.Completed)
  }

  /**
   * 释放底层 TTS 资源。
   */
  override fun release() {
    tts?.stop()
    tts?.shutdown()
    tts = null
    isInitialized = false
  }

  private fun emitEvent(event: SpeechOutputEvent) {
    coroutineScope.launch {
      _events.emit(event)
    }
  }
}
