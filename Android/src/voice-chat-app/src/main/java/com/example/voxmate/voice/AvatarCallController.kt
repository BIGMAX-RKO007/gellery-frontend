package com.example.voxmate.voice

import androidx.annotation.StringRes
import com.example.voxmate.R
import com.example.voxmate.speech.SpeechRecognitionEngine
import com.example.voxmate.speech.SpeechRecognitionRequest
import com.example.voxmate.ui.chat.ChatMessage
import com.google.ai.edge.gallery.aicore.AiChatEvent
import com.google.ai.edge.gallery.aicore.AiChatRequest
import com.google.ai.edge.gallery.aicore.AiChatSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 数字人通话的不可变显示状态，由页面读取，不保存麦克风和原生模型对象。 */
data class AvatarCallState(
  /** 当前会话消息，切换输入方式不清除历史。 */
  val messages: List<ChatMessage> = emptyList(),
  /** 麦克风已打开并持续采集。 */
  val listening: Boolean = false,
  /** 用户已起声且尚未完成断句。 */
  val userSpeaking: Boolean = false,
  /** AI 当前正在生成。 */
  val thinking: Boolean = false,
  /** TTS 当前正在播放。 */
  val speaking: Boolean = false,
  /** 识别引擎正在后台准备。 */
  val preparing: Boolean = false,
  /** 当前数字人表情，未指定时恢复自然表情。 */
  val expression: String? = null,
  /** 可由页面重试的国际化错误资源，不含用户录音或敏感配置。 */
  @StringRes val error: Int? = null,
)

/**
 * 数字人页面独占的电话式对话控制器；不拥有共享的大模型，只拥有采音、转写和播报。
 *
 * @param session 应用持有的已加载会话，页面退出只停止生成
 * @param input 持续采音与 VAD 输入
 * @param recognition 可替换的后台语音识别引擎
 * @param output 页面独占的播报引擎
 * @param scope 页面生命周期作用域；所有公开方法应在主线程调用
 */
class AvatarCallController(
  private val session: AiChatSession,
  private val input: VoiceAudioInput,
  private val recognition: SpeechRecognitionEngine,
  private val output: SpeechOutput,
  private val scope: CoroutineScope,
) {
  /** 仅在主线程更新的显示状态。 */
  private val mutableState = MutableStateFlow(AvatarCallState())
  /** 页面可收集的只读状态。 */
  val state = mutableState.asStateFlow()
  /** 后台准备资源任务。 */
  private var preparation: Job? = null
  /** 当前本地转写任务。 */
  private var transcription: Job? = null
  /** 当前 AI 生成任务，下一轮必须等待此任务退出。 */
  private var generation: Job? = null
  /** 取消等待任务时也保持原始生成和重置互斥，防止快速连续插话跨越取消链。 */
  private val sessionMutex = Mutex()
  /** 收音或生成被取消时递增，隔离晚到结果。 */
  private var epoch = 0L
  /** 前台、权限、输入模式和模型状态合并后的通话开关。 */
  private var active = false
  /** 识别引擎已完成准备。 */
  private var prepared = false
  /** 释放后禁止再启动。 */
  private var closed = false
  /** 采音事件订阅随页面退出取消。 */
  private val inputEvents = scope.launch {
    input.events.collect { event ->
      if (!active) return@collect
      when (event) {
        VoiceAudioInputEvent.Listening ->
          mutableState.value = mutableState.value.copy(listening = true)
        VoiceAudioInputEvent.SpeechStarted -> {
          interrupt()
          mutableState.value = mutableState.value.copy(userSpeaking = true)
        }
        is VoiceAudioInputEvent.TurnReady -> {
          mutableState.value = mutableState.value.copy(userSpeaking = false)
          val token = epoch
          transcription = scope.launch {
            try {
              val result = recognition.transcribe(
                SpeechRecognitionRequest(event.turn.samples, event.turn.sampleRate)
              )
              if (active && token == epoch && result.text.isNotBlank()) send(result.text)
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (error: Exception) {
              if (token == epoch) fail(R.string.call_recognition_error)
            }
          }
        }
        is VoiceAudioInputEvent.Error -> fail(R.string.call_microphone_error)
        is VoiceAudioInputEvent.EchoCancellation -> Unit
      }
    }
  }
  /** 播报状态订阅与数字人口型驱动共用事件，不接触桥接实现。 */
  private val outputEvents = scope.launch {
    output.events.collect { event ->
      mutableState.value = when (event) {
        SpeechOutputEvent.Started -> mutableState.value.copy(speaking = true)
        SpeechOutputEvent.Completed -> mutableState.value.copy(speaking = false, expression = null)
        is SpeechOutputEvent.Error ->
          mutableState.value.copy(speaking = false, error = R.string.call_playback_error)
      }
    }
  }

  /**
   * 根据页面前台和输入模式自动开始或停止通话；初始化失败允许再次调用重试。
   * @param enabled 仅在模型就绪、已授权、前台且使用语音模式时为真
   */
  fun setActive(enabled: Boolean) {
    if (closed) return
    if (active == enabled) {
      // 文字模式进入后台时仍须停止生成和播报，即使麦克风早已关闭。
      if (!enabled) interrupt()
      return
    }
    active = enabled
    if (!enabled) {
      preparation?.cancel()
      input.stopListening()
      interrupt()
      mutableState.value = mutableState.value.copy(listening = false, preparing = false)
      return
    }
    mutableState.value = mutableState.value.copy(preparing = !prepared, error = null)
    preparation = scope.launch {
      try {
        if (!prepared) {
          recognition.prepare()
          prepared = true
        }
        if (active) {
          mutableState.value = mutableState.value.copy(preparing = false)
          input.startListening()
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        fail(R.string.call_recognition_error)
      }
    }
  }

  /** 停止旧播报和生成并使转写结果失效；保留持续麦克风以接收下一句话。 */
  private fun interrupt() {
    epoch++
    transcription?.cancel()
    output.stop()
    session.stop()
    generation?.cancel()
    mutableState.value = mutableState.value.copy(
      thinking = false, speaking = false, userSpeaking = false, expression = null
    )
  }

  /**
   * 发送文字或最终转写；先取消并等待旧生成退出，避免同一会话并发调用。
   * @param text 非空用户文字，空白输入忽略；失败保存国际化错误状态
   */
  fun send(text: String) {
    if (closed || text.isBlank()) return
    val previous = generation
    interrupt()
    val token = epoch
    val user = ChatMessage(isUser = true, text = text.trim())
    val assistant = ChatMessage(isUser = false, text = "", isStreaming = true)
    mutableState.value = mutableState.value.copy(
      messages = mutableState.value.messages + user + assistant,
      thinking = true, error = null
    )
    generation = scope.launch {
      try {
        previous?.cancelAndJoin()
        sessionMutex.withLock {
          val response = StringBuilder()
          /** 每轮独立分句，旧任务取消时未播报的尾句自动丢弃。 */
          val segmenter = StreamingSentenceSegmenter()
          /** 已交给分句器的净文本长度，防止情绪标签被朗读或重复播报。 */
          var spokenLength = 0
          session.send(AiChatRequest(text = text.trim())).collect { event ->
            if (token != epoch) return@collect
            when (event) {
              is AiChatEvent.TextDelta -> {
                response.append(event.text)
                updateAssistant(assistant.id, response.toString(), true)
                val clean = EmotionParser.parse(response.toString()).cleanText
                val incompleteTag = response.startsWith("[") && !response.contains("]")
                if (!incompleteTag && clean.length > spokenLength) {
                  segmenter.append(clean.substring(spokenLength)).forEach {
                    output.speak(it, queue = true)
                  }
                  spokenLength = clean.length
                }
              }
              is AiChatEvent.Completed -> {
                updateAssistant(assistant.id, response.toString(), false)
                segmenter.flush()?.let { output.speak(it, queue = true) }
              }
              is AiChatEvent.Cancelled ->
                updateAssistant(assistant.id, response.toString(), false)
            }
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (token == epoch) {
          mutableState.value = mutableState.value.copy(error = R.string.call_generation_error)
        }
      } finally {
        if (token == epoch) mutableState.value = mutableState.value.copy(thinking = false)
        mutableState.value = mutableState.value.copy(
          messages = mutableState.value.messages.map {
            if (it.id == assistant.id) it.copy(isStreaming = false) else it
          }
        )
      }
    }
  }

  /** 更新指定回答及表情，仅由主线程生成事件调用，取消后不再接收旧事件。 */
  private fun updateAssistant(id: String, text: String, streaming: Boolean) {
    val parsed = EmotionParser.parse(text)
    mutableState.value = mutableState.value.copy(
      messages = mutableState.value.messages.map {
        if (it.id == id) it.copy(text = parsed.cleanText, isStreaming = streaming) else it
      },
      expression = parsed.expression,
      thinking = streaming
    )
  }

  /** 暂停收音并保存错误，圆形按钮允许重试；资源仍归页面所有。 */
  private fun fail(@StringRes error: Int) {
    setActive(false)
    mutableState.value = mutableState.value.copy(error = error, preparing = false)
  }

  /** 新对话先停旧任务并等待退出再重置模型；语音模式重置后恢复采音。 */
  fun reset() {
    val previous = generation
    input.stopListening()
    interrupt()
    mutableState.value = AvatarCallState()
    generation = scope.launch {
      try {
        previous?.cancelAndJoin()
        sessionMutex.withLock { session.reset() }
        if (active && !closed) input.startListening()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        mutableState.value = mutableState.value.copy(error = R.string.call_generation_error)
      }
    }
  }

  /** 页面退出时停止所有任务并释放独占资源，重复调用安全；共享模型不关闭。 */
  fun close() {
    if (closed) return
    setActive(false)
    interrupt()
    closed = true
    preparation?.cancel()
    inputEvents.cancel()
    outputEvents.cancel()
    input.release()
    recognition.close()
    output.release()
  }
}
