package com.example.voxmate.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 将持续录音和可替换分句器组合成电话式音频输入，不执行本地语音转写。
 *
 * @param sourceFactory 每次监听创建独立音频来源
 * @param detectorFactory 每次监听创建独立分句器
 * @param eventDispatcher 事件串行分发调度器，测试可注入无需 Android Looper 的实现
 */
class ContinuousVoiceAudioInput(
  private val sourceFactory: () -> AudioFrameSource = { AndroidAudioFrameSource() },
  private val detectorFactory: () -> UtteranceDetector = { EnergyUtteranceDetector() },
  private val eventDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : VoiceAudioInput {
  /** 页面拥有的作用域，释放时取消全部采集任务。 */
  private val scope = CoroutineScope(SupervisorJob() + eventDispatcher)

  /** 事件按应用线程顺序发送，用户录音不写入磁盘或日志。 */
  override val events = MutableSharedFlow<VoiceAudioInputEvent>(extraBufferCapacity = 32)

  /** 当前持续采集任务。 */
  private var captureJob: Job? = null

  /** 正在采集的音频来源，用于立即停麦。 */
  private var source: AudioFrameSource? = null

  /** 生命周期轮次，屏蔽暂停之前晚到的事件。 */
  private var epoch = 0L

  /** 永久释放后禁止重新启动。 */
  private var released = false

  /** 启动一次持续收音；每个完成语句作为独立音频回合发出，麦克风不会重开。 */
  override fun startListening() {
    if (released || captureJob?.isActive == true) return
    val token = ++epoch
    val previous = captureJob
    captureJob =
      scope.launch {
        var detector: UtteranceDetector? = null
        try {
          previous?.join()
          val microphone = sourceFactory()
          val activeDetector = detectorFactory()
          detector = activeDetector
          source = microphone
          microphone.capture(
            onReady = { aec ->
              withContext(eventDispatcher) {
                if (token == epoch) {
                  events.emit(VoiceAudioInputEvent.EchoCancellation(aec))
                  events.emit(VoiceAudioInputEvent.Listening)
                }
              }
            },
            onFrame = { frame ->
              val boundaries = activeDetector.accept(frame)
              if (boundaries.isNotEmpty()) {
                withContext(eventDispatcher) {
                  if (token == epoch) {
                    boundaries.forEach { boundary ->
                      when (boundary) {
                        UtteranceBoundary.Started ->
                          events.emit(VoiceAudioInputEvent.SpeechStarted)
                        is UtteranceBoundary.Finished -> {
                          if (boundary.samples.isNotEmpty()) {
                            events.emit(
                              VoiceAudioInputEvent.TurnReady(
                                VoiceTurn(boundary.samples, SAMPLE_RATE_HZ)
                              )
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
            },
          )
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          if (token == epoch) {
            events.emit(
              VoiceAudioInputEvent.Error(
                if (error is SecurityException) ERROR_PERMISSION else ERROR_CAPTURE
              )
            )
          }
        } finally {
          detector?.close()
          if (token == epoch) source = null
        }
      }
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
  }
}
