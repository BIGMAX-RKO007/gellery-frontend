package com.example.voxmate.voice

/**
 * 把语言模型的流式文字增量切分成适合 TTS 的完整短句。
 *
 * 优先在中英文句末标点或换行处切分；长时间没有句末标点时，会在最大长度前最近的逗号
 * 处分段，降低用户等待首段语音的时间。实例不保证线程安全，只能由一个协调器顺序调用。
 */
class StreamingSentenceSegmenter {
  /** 尚未形成可播报短句的文字缓冲区。 */
  private val buffer = StringBuilder()

  /**
   * 追加模型新增文字并返回本次形成的完整短句。
   *
   * @param delta 只包含本次新增内容的模型文字片段
   * @return 按生成顺序排列的可播报短句
   */
  fun append(delta: String): List<String> {
    if (delta.isEmpty()) return emptyList()
    buffer.append(delta)
    val sentences = mutableListOf<String>()
    while (true) {
      val boundary = findBoundary()
      if (boundary <= 0) break
      val sentence = buffer.substring(0, boundary).toSpeechText()
      buffer.delete(0, boundary)
      if (sentence.isNotEmpty()) sentences += sentence
    }
    return sentences
  }

  /**
   * 在模型生成结束时返回剩余文字并清空缓冲区。
   *
   * @return 清理 Markdown 后的剩余短句；没有内容时返回 `null`
   */
  fun flush(): String? {
    val remaining = buffer.toString().toSpeechText()
    buffer.clear()
    return remaining.ifEmpty { null }
  }

  /** 清空未播报缓冲区，用于新请求、停止或离开页面。 */
  fun reset() {
    buffer.clear()
  }

  /**
   * 查找下一处切分位置，返回值为缓冲区中需要移除的字符数。
   *
   * @return 大于零表示可切分长度，零表示需要等待更多文字
   */
  private fun findBoundary(): Int {
    for (index in buffer.indices) {
      if (buffer[index] in SENTENCE_ENDINGS) {
        var endExclusive = index + 1
        while (endExclusive < buffer.length && buffer[endExclusive] in CLOSING_MARKS) {
          endExclusive += 1
        }
        return endExclusive
      }
    }
    if (buffer.length < MAX_CHUNK_LENGTH) return 0
    for (index in MAX_CHUNK_LENGTH - 1 downTo MIN_COMMA_SPLIT_LENGTH) {
      if (buffer[index] in SOFT_BOUNDARIES) return index + 1
    }
    return MAX_CHUNK_LENGTH
  }

  /** 流式短句切分使用的固定边界集合。 */
  private companion object {
    /** 可以立即结束一个播报短句的中英文标点。 */
    val SENTENCE_ENDINGS = setOf('。', '！', '？', '!', '?', '；', ';', '\n')

    /** 句末标点后应和前句一起播报的闭合符号。 */
    val CLOSING_MARKS = setOf('”', '’', '"', '\'', '）', ')', '】', ']')

    /** 长句达到上限时优先使用的柔性边界。 */
    val SOFT_BOUNDARIES = setOf('，', ',', '、', '：', ':', ' ')

    /** 没有句末标点时单个播报片段的最大字符数。 */
    const val MAX_CHUNK_LENGTH = 60

    /** 为避免过短片段，逗号切分至少保留的字符数。 */
    const val MIN_COMMA_SPLIT_LENGTH = 20
  }
}

/**
 * 清理不适合直接朗读的常见 Markdown 符号。
 *
 * @receiver 模型生成的一个完整文字片段
 * @return 保留正文内容、压缩多余空白后的播报文字
 */
private fun String.toSpeechText(): String =
  replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("`([^`]*)`"), "\$1")
    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "\$1")
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "\$1")
    .replace(Regex("^[>#*+\\-]+\\s*", RegexOption.MULTILINE), "")
    .replace(Regex("[*_~]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()
