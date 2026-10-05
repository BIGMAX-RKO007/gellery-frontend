package com.example.voxmate.voice

import com.example.voxmate.speech.SpeechRecognitionEngine
import com.example.voxmate.speech.SpeechRecognitionRequest
import com.example.voxmate.speech.SpeechTranscript
import com.google.ai.edge.gallery.aicore.AiChatEvent
import com.google.ai.edge.gallery.aicore.AiChatRequest
import com.google.ai.edge.gallery.aicore.AiChatSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 使用可控事件验证自动监听、停麦和插话，不依赖真实手机麦克风。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AvatarCallControllerTest {
  /** 文字模式麦克风已停时进入后台，仍必须停止回答。 */
  @Test fun backgroundStopsTextGeneration() = runTest {
    val session = TestSession()
    val controller = AvatarCallController(
      session, TestInput(), TestRecognition(), TestOutput(), backgroundScope
    )
    runCurrent()
    controller.send("text")
    runCurrent()
    assertEquals(1, session.active)
    controller.setActive(false)
    runCurrent()
    assertEquals(0, session.active)
    controller.close()
  }

  /** 情绪标签跨增量形成时，不朗读标签且不丢失正文开头。 */
  @Test fun partialEmotionTagIsNotSpoken() = runTest {
    val output = TestOutput()
    val controller = AvatarCallController(
      TestSession(complete = true), TestInput(), TestRecognition(), output, backgroundScope
    )
    runCurrent()
    controller.send("hello")
    runCurrent()
    assertEquals(listOf("Hello!"), output.spoken)
    controller.close()
  }

  /** 授权和模型就绪后启动监听，切换文字模式停止采集且丢弃晚到录音。 */
  @Test fun activationAndTextModeStopCapture() = runTest {
    val input = TestInput()
    val engine = TestRecognition()
    val session = TestSession()
    val controller = AvatarCallController(session, input, engine, TestOutput(), backgroundScope)
    runCurrent()
    controller.setActive(true)
    runCurrent()
    assertEquals(1, engine.preparations)
    assertEquals(1, input.starts)
    controller.setActive(false)
    input.events.emit(VoiceAudioInputEvent.TurnReady(VoiceTurn(floatArrayOf(0.1f), 16000)))
    runCurrent()
    assertEquals(1, input.stops)
    assertTrue(session.requests.isEmpty())
    controller.close()
    assertTrue(engine.closed)
  }

  /** 起声立即停止播报和旧生成，最终转写进入新回合且不存在并发生成。 */
  @Test fun speechInterruptsAndSendsNextTurnSerially() = runTest {
    val input = TestInput()
    val session = TestSession()
    val output = TestOutput()
    val controller = AvatarCallController(session, input, TestRecognition(), output, backgroundScope)
    runCurrent()
    controller.setActive(true)
    runCurrent()
    controller.send("first")
    runCurrent()
    assertEquals(1, session.active)
    val previousStops = output.stops
    input.events.emit(VoiceAudioInputEvent.SpeechStarted)
    runCurrent()
    assertTrue(output.stops > previousStops)
    assertEquals(0, session.active)
    input.events.emit(VoiceAudioInputEvent.TurnReady(VoiceTurn(floatArrayOf(0.1f), 16000)))
    runCurrent()
    assertEquals(listOf("first", "recognized"), session.requests)
    assertEquals(1, session.maximumActive)
    controller.close()
    runCurrent()
    assertEquals(0, session.active)
  }
}

/** 可控采音替身，计数仅用于验证停麦和启动契约。 */
private class TestInput : VoiceAudioInput {
  /** 主线程顺序推送的录音事件。 */
  override val events = MutableSharedFlow<VoiceAudioInputEvent>()
  /** 启动次数。 */
  var starts = 0
  /** 停止次数。 */
  var stops = 0
  /** 记录启动，无真实音频。 */
  override fun startListening() { starts++ }
  /** 记录停止，允许再次启动。 */
  override fun stopListening() { stops++ }
  /** 替身没有平台资源。 */
  override fun release() = Unit
}

/** 返回稳定文字的识别替身，验证准备和释放所有权。 */
private class TestRecognition : SpeechRecognitionEngine {
  /** 预热次数。 */
  var preparations = 0
  /** 是否已释放。 */
  var closed = false
  /** 模拟后台准备完成。 */
  override suspend fun prepare() { preparations++ }
  /** 返回固定转写，不保存输入音频。 */
  override suspend fun transcribe(request: SpeechRecognitionRequest) = SpeechTranscript("recognized")
  /** 标记释放。 */
  override fun close() { closed = true }
}

/** 保持生成直到被取消的模型替身，检测同一会话并发请求。 */
private class TestSession(
  /** 为真时按碎片输出一个完整回答；否则持续生成直到取消。 */
  private val complete: Boolean = false,
) : AiChatSession {
  /** 收到的请求文字，仅测试使用。 */
  val requests = mutableListOf<String>()
  /** 正在运行的生成数。 */
  var active = 0
  /** 最大并行生成数，契约要求不超过一。 */
  var maximumActive = 0
  /** 是否仍有生成任务。 */
  override val isGenerating get() = active > 0
  /** 保持流活跃，取消时减少运行计数。 */
  override fun send(request: AiChatRequest) = flow<AiChatEvent> {
    requests += request.text
    active++
    maximumActive = maxOf(maximumActive, active)
    try {
      if (complete) {
        emit(AiChatEvent.TextDelta("[ha"))
        emit(AiChatEvent.TextDelta("ppy] He"))
        emit(AiChatEvent.TextDelta("llo!"))
        emit(AiChatEvent.Completed)
      } else {
        emit(AiChatEvent.TextDelta("[happy] Hello"))
        awaitCancellation()
      }
    } finally {
      active--
    }
  }
  /** 任务取消由控制器执行，替身不主动关闭流。 */
  override fun stop() = Unit
  /** 无历史需要重置。 */
  override suspend fun reset(systemInstruction: String?) = Unit
  /** 替身无原生资源。 */
  override suspend fun close() = Unit
}

/** 播报替身，仅记录打断次数，不连接系统服务。 */
private class TestOutput : SpeechOutput {
  /** 可控播报事件。 */
  override val events = MutableSharedFlow<SpeechOutputEvent>()
  /** 停播次数。 */
  var stops = 0
  /** 已要求播报的短句，用于验证情绪标记和正文切分。 */
  val spoken = mutableListOf<String>()
  /** 测试无需真实合成。 */
  override fun speak(text: String, queue: Boolean) { spoken += text }
  /** 记录同步停播。 */
  override fun stop() { stops++ }
  /** 替身无系统资源。 */
  override fun release() = Unit
}
