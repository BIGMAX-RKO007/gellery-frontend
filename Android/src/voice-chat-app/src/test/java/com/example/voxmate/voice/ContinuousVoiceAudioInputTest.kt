package com.example.voxmate.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证页面打断处理暂停时，采音仍可继续读取，不等待主线程回调。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousVoiceAudioInputTest {
  /** 页面故意暂停处理起声事件，音频来源仍完整读完所有后续帧。 */
  @Test fun slowInterruptionDoesNotBlockCapture() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val source = TestFrameSource()
    val detector = TestBoundaries()
    val input = ContinuousVoiceAudioInput({ source }, { detector }, dispatcher, dispatcher)
    val pause = CompletableDeferred<Unit>()
    val received = mutableListOf<VoiceAudioInputEvent>()
    backgroundScope.launch {
      input.events.collect { event ->
        received += event
        if (event == VoiceAudioInputEvent.SpeechStarted) pause.await()
      }
    }
    runCurrent()
    input.startListening()
    runCurrent()
    assertEquals(100, source.framesRead)
    pause.complete(Unit)
    runCurrent()
    assertEquals(1, received.filterIsInstance<VoiceAudioInputEvent.TurnReady>().size)
    input.release()
    runCurrent()
  }
}

/** 有限音频来源替身，不执行真实录音，用帧计数检测读取是否被阻塞。 */
private class TestFrameSource : AudioFrameSource {
  /** 已读取的帧数量。 */
  var framesRead = 0
  /** 依次读取一百帧，任何等待页面事件的行为都会暴露在帧计数中。 */
  override suspend fun capture(
    onReady: suspend (Boolean) -> Unit,
    onFrame: suspend (FloatArray) -> Unit,
  ) {
    onReady(true)
    repeat(100) {
      framesRead++
      onFrame(FloatArray(320))
    }
  }
  /** 有限替身无需解除平台阻塞。 */
  override fun stop() = Unit
}

/** 第一次送帧起声，最后一帧完成发言，其余帧没有边界事件。 */
private class TestBoundaries : UtteranceDetector {
  /** 顺序接收的帧数。 */
  private var count = 0
  /** 返回可控边界以验证音频事件保持顺序。 */
  override fun accept(frame: FloatArray): List<UtteranceBoundary> {
    count++
    return when (count) {
      1 -> listOf(UtteranceBoundary.Started)
      100 -> listOf(UtteranceBoundary.Finished(floatArrayOf(0.1f)))
      else -> emptyList()
    }
  }
}
