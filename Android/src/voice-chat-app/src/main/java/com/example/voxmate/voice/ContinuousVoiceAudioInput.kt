package com.example.voxmate.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import com.example.voxmate.speech.SpeechTurnTooLongException

/**
 * 将持续录音和可替换分句器组合成电话式音频输入，不执行本地语音转写。
 *
 * @param sourceFactory 每次监听创建独立音频来源
 * @param detectorFactory 每次监听创建独立分句器
 * @param eventDispatcher 事件串行分发调度器，测试可注入无需 Android Looper 的实现
 * @param captureDispatcher 录音和 VAD 独立后台调度器，测试可注入可控调度器
 */
class ContinuousVoiceAudioInput(
  private val sourceFactory: () -> AudioFrameSource = { AndroidAudioFrameSource() },
  private val detectorFactory: () -> UtteranceDetector = { EnergyUtteranceDetector() },
  private val eventDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
  private val captureDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VoiceAudioInput {
  /** 页面拥有的作用域，释放时取消全部采集任务。 */
  private val scope = CoroutineScope(SupervisorJob() + eventDispatcher)

  /** 事件按应用线程顺序发送，用户录音不写入磁盘或日志。 */
  override val events = MutableSharedFlow<VoiceAudioInputEvent>(extraBufferCapacity = 32)

  /** 当前持续采集任务。 */
  private var captureJob: Job? = null

  /** 正在采集的音频来源，用于立即停麦。 */
  @Volatile
  private var source: AudioFrameSource? = null

  /** 生命周期轮次，屏蔽暂停之前晚到的事件。 */
  @Volatile private var epoch = 0L

  /** 永久释放后禁止重新启动。 */
  private var released = false

  /** 启动一次持续收音；每个完成语句作为独立音频回合发出，麦克风不会重开。 */
  override fun startListening() {
    if (released || captureJob?.isActive == true) return
    val token = ++epoch
    val previous = captureJob
    captureJob =
      scope.launch(captureDispatcher) {
        var detector: UtteranceDetector? = null
        try {
          previous?.join()
          val microphone = sourceFactory()
          val activeDetector = detectorFactory()
          detector = activeDetector
          source = microphone
          capture(microphone, activeDetector, token)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          if (token == epoch) {
            events.emit(
              VoiceAudioInputEvent.Error(
                when (error) {
                  is SecurityException -> ERROR_PERMISSION
                  is SpeechTurnTooLongException -> ERROR_TOO_LONG
                  else -> ERROR_CAPTURE
                }
              )
            )
          }
        } finally {
          detector?.close()
          if (token == epoch) source = null
        }
      }
  }

  /**
   * 独立读取录音和 VAD，通过有界队列顺序分发页面事件，不等待停止 AI。
   *
   * @param microphone 本轮独占音频来源，底层负责 finally 释放
   * @param detector 本轮独占 VAD，由上层采集任务释放
   * @param token 当前生命周期轮次，暂停前的事件不再发送
   * @throws IllegalStateException 队列溢出时终止采集并由上层转换为错误
   */
  private suspend fun capture(
    microphone: AudioFrameSource,
    detector: UtteranceDetector,
    token: Long,
  ) = coroutineScope {
    /** 队列存放语音边界而非每一帧 PCM，满时明确报告失败。 */
    val pending = Channel<VoiceAudioInputEvent>(capacity = 32)
    /** 分发任务只负责串行通知页面，不参与录音读取。 */
    val delivery = launch(eventDispatcher) {
      for (event in pending) {
        if (token == epoch) events.emit(event)
      }
    }
    /**
     * 非阻塞送入当前轮次的事件；队列满时抛出异常，不静默丢弃。
     * @param event 录音或 VAD 事件，音频仅驻留内存
     */
    fun enqueue(event: VoiceAudioInputEvent) {
      if (token != epoch) return
      check(pending.trySend(event).isSuccess) { "Voice event queue overflow" }
    }
    try {
      microphone.capture(
        onReady = { aec ->
          enqueue(VoiceAudioInputEvent.EchoCancellation(aec))
          enqueue(VoiceAudioInputEvent.Listening)
        },
        onFrame = { frame ->
          detector.accept(frame).forEach { boundary ->
            when (boundary) {
              UtteranceBoundary.Started -> enqueue(VoiceAudioInputEvent.SpeechStarted)
              is UtteranceBoundary.Finished -> {
                if (boundary.samples.isNotEmpty()) {
                  enqueue(VoiceAudioInputEvent.TurnReady(
                    VoiceTurn(boundary.samples, SAMPLE_RATE_HZ)
                  ))
                }
              }
            }
          }
        },
      )
    } finally {
      pending.close()
    }
    delivery.join()
  }

  /** 停止当前采集并使所有晚到事件失效，后续仍可重新启动。 */
  override fun stopListening() {
    epoch++
    source?.stop()
    captureJob?.cancel()
    source = null
  }

  /** 停止麦克风并取消页面输入作用域；重复调用安全。 */
  override fun release() {
    if (released) return
    released = true
    stopListening()
    scope.cancel()
  }

  /** 固定录音格式及稳定错误码。 */
  private companion object {
    /** Gemma 音频前处理要求的采样率。 */
    const val SAMPLE_RATE_HZ = 16_000

    /** 麦克风权限错误码。 */
    const val ERROR_PERMISSION = "permission"

    /** 麦克风初始化或读取失败错误码。 */
    const val ERROR_CAPTURE = "capture"
    /** 发言超出端侧安全上限，不自动提交已录的部分。 */
    const val ERROR_TOO_LONG = "turn_too_long"
  }
}
