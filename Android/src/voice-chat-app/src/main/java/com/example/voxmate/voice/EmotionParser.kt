package com.example.voxmate.voice

/**
 * 文本情绪标签解析结果。
 *
 * @param cleanText 剔除情绪标签后用于实际 TTS 发音与界面展示的纯净文本。
 * @param expression 检测到的情绪标识，如 "happy", "sad", "angry", "relaxed", "surprised"；未匹配到时为 null。
 */
data class EmotionParseResult(
  val cleanText: String,
  val expression: String? = null,
)

/**
 * AI 文本流情绪解析器。
 *
 * 职责：
 * 从大模型生成的文本流或预设语句中提取 [happy]、[sad]、[thinking] 等情绪标签，
 * 触发数字人表情切换，并将过滤后的干净文本交付给语音合成系统。
 */
object EmotionParser {

  private val EmotionPattern = Regex("\\[(happy|sad|angry|relaxed|surprised|thinking|smile|friendly|joy|calm|excited)\\]", RegexOption.IGNORE_CASE)

  /**
   * 解析带情绪标签的文本。
   *
   * 示例：
   * 输入: "[happy] 很高兴见到你！"
   * 输出: EmotionParseResult(cleanText = "很高兴见到你！", expression = "happy")
   */
  fun parse(rawText: String): EmotionParseResult {
    val match = EmotionPattern.find(rawText)
    if (match == null) {
      return EmotionParseResult(cleanText = rawText.trim(), expression = null)
    }

    val rawTag = match.groupValues[1].lowercase()
    val normalizedExpression = when (rawTag) {
      "smile", "friendly", "joy", "excited" -> "happy"
      "thinking", "calm" -> "relaxed"
      else -> rawTag
    }

    val clean = rawText.replace(EmotionPattern, "").trim()
    return EmotionParseResult(cleanText = clean, expression = normalizedExpression)
  }
}
