package com.example.voxmate.voice

import com.example.voxmate.bridge.VrmBridgeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 实时唇形驱动器（Lip Sync Driver）。
 *
 * 职责：
 * 监听 [SpeechOutput] 的播放事件，并在语音发声期间以 ~30fps 采样率生成自然的唇形元音律动，
 * 实时驱动 [VrmBridgeController] 完成嘴型张合，并在语音结束时平滑归零闭合。
 */
class LipSyncDriver(
  private val bridgeControllerProvider: () -> VrmBridgeController?,
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

  /**
   * 活跃的唇形驱动协程任务。
   */
  private var drivingJob: Job? = null

  /**
   * 标记当前是否正处于发音状态。
   */
  private var isSpeaking = false

  /**
   * 绑定语音事件流，实现全自动唇形联动。
   */
  fun attachSpeechOutput(speechOutput: SpeechOutput) {
    coroutineScope.launch {
      speechOutput.events.collect { event ->
        when (event) {
          is SpeechOutputEvent.Started -> startLipSync()
          is SpeechOutputEvent.Completed, is SpeechOutputEvent.Error -> stopLipSync()
        }
      }
    }
  }

  /**
   * 启动实时嘴型律动。
   */
  fun startLipSync() {
    if (isSpeaking) return
    isSpeaking = true

    drivingJob?.cancel()
    drivingJob = coroutineScope.launch {
      var time = 0.0f
      val controller = bridgeControllerProvider()

      while (isActive && isSpeaking && controller != null) {
        time += 0.033f
        // 拟合真实语音元音节奏：主基频正弦波叠加上随机音节突变
        val basePulse = abs(sin(time * 14.0f))
        val randomVariation = Random.nextFloat() * 0.35f
        val mouthVolume = (basePulse * 0.65f + randomVariation).coerceIn(0.15f, 0.90f)

        controller.speak(mouthVolume)
        delay(33) // ~30fps 刷新率
      }

      // 结束时平滑复位嘴巴闭合
      controller?.speak(0.0f)
    }
  }

  /**
   * 停止嘴型律动并重置闭合。
   */
  fun stopLipSync() {
    isSpeaking = false
    drivingJob?.cancel()
    drivingJob = null
    bridgeControllerProvider()?.speak(0.0f)
  }
}
