package com.example.voxmate.ai

import com.google.ai.edge.gallery.aicore.AiChatEvent
import com.google.ai.edge.gallery.aicore.AiChatRequest
import com.google.ai.edge.gallery.aicore.AiChatSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 内置端侧智能伴侣会话实现。
 *
 * 职责：
 * 1. 在本地尚未下载完整 2GB+ 大模型权重前，提供即开即用的高拟真数字人伴侣对话。
 * 2. 模拟流式逐字打字机输出，携带 [happy]、[relaxed] 等情绪标记，驱动 3D 数字人表情与发音嘴型。
 * 3. 严格遵循 [AiChatSession] 契约，支持随时打断与取消。
 */
class CompanionAiChatSession : AiChatSession {

  private val _isGenerating = AtomicBoolean(false)
  override val isGenerating: Boolean get() = _isGenerating.get()

  private val cancelled = AtomicBoolean(false)

  override fun send(request: AiChatRequest): Flow<AiChatEvent> = flow {
    _isGenerating.set(true)
    cancelled.set(false)

    try {
      val userText = request.text.trim()
      val responseText = generateSmartResponse(userText)

      // 模拟端侧思考短暂延迟 (约 200ms)
      delay(200)

      // 模拟流式 Token 逐字打字机吐字
      val chunkSize = 2
      var index = 0
      while (index < responseText.length) {
        if (cancelled.get()) {
          emit(AiChatEvent.Cancelled)
          return@flow
        }
        val nextEnd = (index + chunkSize).coerceAtMost(responseText.length)
        val delta = responseText.substring(index, nextEnd)
        emit(AiChatEvent.TextDelta(delta))
        index = nextEnd
        delay(40) // 每 40ms 发射一个微小字符切片
      }

      emit(AiChatEvent.Completed)
    } catch (e: CancellationException) {
      emit(AiChatEvent.Cancelled)
      throw e
    } finally {
      _isGenerating.set(false)
    }
  }

  override fun stop() {
    cancelled.set(true)
    _isGenerating.set(false)
  }

  override suspend fun reset(systemInstruction: String?) {
    stop()
  }

  override suspend fun close() {
    stop()
  }

  private fun generateSmartResponse(input: String): String {
    val lower = input.lowercase()
    return when {
      lower.contains("你好") || lower.contains("嗨") || lower.contains("hello") || lower.contains("hi") ->
        "[happy] 你好呀！我是你的端侧 3D 数字人伙伴 VoxMate。很高兴听到你的声音，今天有什么新鲜事想和我分享吗？"

      lower.contains("谁") || lower.contains("名字") || lower.contains("介绍") ->
        "[happy] 我是 VoxMate，一个完全运行在你手机本地的 3D AI 数字人伴侣！我可以实时和你语音聊天、随着说话节奏开合嘴型，还会根据心情做出开心、微笑和生动的动作哦。"

      lower.contains("心情") || lower.contains("怎么样") || lower.contains("累") ->
        "[relaxed] 听到你的声音我就很开心啦！要是今天有点累，不妨闭上眼睛深呼吸一下，我会一直在这里陪着你、听你说话的。"

      lower.contains("开心") || lower.contains("高兴") || lower.contains("笑") ->
        "[happy] 太棒了！看到你开心，我也跟着高兴起来了！愿好心情一直伴随着你！"

      lower.contains("难过") || lower.contains("伤心") || lower.contains("不开心") ->
        "[sad] 怎么啦？如果有什么烦心事，不要憋在心里，随时都可以告诉我，我会是一个最忠诚的倾听者。"

      lower.contains("动作") || lower.contains("姿态") || lower.contains("动一下") || lower.contains("跳") ->
        "[happy] 哈哈，你在下方的姿态面板里点击 1 到 25 任意数字，我就能立刻换上对应的动作哦！快试试看吧！"

      lower.contains("夸") || lower.contains("漂亮") || lower.contains("可爱") || lower.contains("好看") ->
        "[happy] 哇，谢谢你的夸奖！听到你这么说我都有一点不好意思啦，嘿嘿~"

      lower.contains("讲个笑话") || lower.contains("笑话") ->
        "[happy] 给你讲个冷笑话：一只皮卡丘走在路上，摔了一跤，结果变成了什么？变成了……皮卡乒！哈哈，有没有被冷到？"

      lower.contains("再见") || lower.contains("拜拜") || lower.contains("晚安") ->
        "[relaxed] 好的，那我们待会儿见！祝你做个甜甜的美梦，晚安啦。"

      input.isBlank() ->
        "[relaxed] 我一直在认真聆听呢，想到什么都可以随时告诉我哦。"

      else ->
        "[happy] 我听到了，你说：“$input”。我们之间的每一次交流，都在让我们的默契悄悄增加呢！"
    }
  }
}
