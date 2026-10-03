package com.example.voxmate.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 基于 Android 系统原生 [SpeechRecognizer] 的语音输入转写器 (STT)。
 *
 * 职责：
 * 1. 监听麦克风音频流并将其转写为实时文字。
 * 2. 通过 [events] 暴露流式部分结果与最终识别结果。
 * 3. 严格在主线程初始化与调用 [SpeechRecognizer] API，保证稳定性。
 *
 * @param context Android 上下文。
 * @param coroutineScope 协程作用域，用于发送事件流。
 */
class AndroidSpeechInput(
  private val context: Context,
  private val coroutineScope: CoroutineScope,
) : SpeechInput {

  private val _events = MutableSharedFlow<SpeechInputEvent>(extraBufferCapacity = 16)
  override val events: SharedFlow<SpeechInputEvent> = _events.asSharedFlow()

  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null
  private var isListening = false

  companion object {
    private const val TAG = "AndroidSpeechInput"
  }

  init {
    mainHandler.post {
      if (SpeechRecognizer.isRecognitionAvailable(context)) {
        initRecognizer()
      } else {
        Log.w(TAG, "当前设备不支持系统原生语音识别引擎")
      }
    }
  }

  private fun initRecognizer() {
    speechRecognizer?.destroy()
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
      setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
          Log.d(TAG, "麦克风就绪，开始聆听")
        }

        override fun onBeginningOfSpeech() {
          Log.d(TAG, "检测到用户发声")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
          isListening = false
          Log.d(TAG, "用户发声结束")
        }

        override fun onError(error: Int) {
          isListening = false
          val errorMsg = mapErrorCode(error)
          Log.w(TAG, "识别错误: $errorMsg (code: $error)")
          coroutineScope.launch {
            _events.emit(SpeechInputEvent.Error(errorMsg))
          }
        }

        override fun onResults(results: Bundle?) {
          isListening = false
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val text = matches?.firstOrNull().orEmpty()
          Log.d(TAG, "识别最终结果: $text")
          if (text.isNotBlank()) {
            coroutineScope.launch {
              _events.emit(SpeechInputEvent.FinalText(text))
            }
          }
        }

        override fun onPartialResults(partialResults: Bundle?) {
          val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val partial = matches?.firstOrNull().orEmpty()
          if (partial.isNotBlank()) {
            coroutineScope.launch {
              _events.emit(SpeechInputEvent.PartialText(partial))
            }
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
      })
    }
  }

  /**
   * 启动麦克风录音并开始语音识别。
   */
  override fun startListening() {
    mainHandler.post {
      if (speechRecognizer == null) {
        initRecognizer()
      }
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
      }
      try {
        speechRecognizer?.startListening(intent)
        isListening = true
      } catch (e: Exception) {
        Log.e(TAG, "启动语音识别失败", e)
        coroutineScope.launch {
          _events.emit(SpeechInputEvent.Error(e.message ?: "启动失败"))
        }
      }
    }
  }

  /**
   * 停止麦克风录音。
   */
  override fun stopListening() {
    mainHandler.post {
      if (isListening) {
        isListening = false
        try {
          speechRecognizer?.stopListening()
        } catch (e: Exception) {
          Log.e(TAG, "停止语音识别异常", e)
        }
      }
    }
  }

  /**
   * 释放底层语音识别器资源。
   */
  override fun release() {
    mainHandler.post {
      try {
        speechRecognizer?.destroy()
        speechRecognizer = null
      } catch (e: Exception) {
        Log.e(TAG, "销毁识别器异常", e)
      }
    }
  }

  private fun mapErrorCode(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
    SpeechRecognizer.ERROR_CLIENT -> "客户端连接异常"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
    SpeechRecognizer.ERROR_NETWORK -> "网络超时"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络连接超时"
    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到清晰语音"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别引擎繁忙"
    SpeechRecognizer.ERROR_SERVER -> "识别服务异常"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到说话声音"
    else -> "识别异常 (代码 $error)"
  }
}
