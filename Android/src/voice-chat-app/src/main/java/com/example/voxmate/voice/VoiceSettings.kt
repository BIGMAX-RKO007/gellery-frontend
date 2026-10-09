package com.example.voxmate.voice

/**
 * 用户自定义的文字转语音（TTS）播报配置。
 *
 * 封装语速、音调与可选的系统特定音色名称，作为不可变领域状态向下传递给底层引擎。
 *
 * @property speechRate 语速倍率，常规范围 0.8f ~ 1.6f，默认 1.10f（略高于标准语速以消除机械拖音）。
 * @property pitch 音调倍率，常规范围 0.8f ~ 1.4f，默认 1.05f（微调清亮感）。
 * @property voiceName 选中的系统音色唯一标识；若为 null，则由引擎自动挑选最优音色。
 */
data class VoiceSettings(
  val speechRate: Float = DEFAULT_SPEECH_RATE,
  val pitch: Float = DEFAULT_PITCH,
  val voiceName: String? = null,
) {
  companion object {
    /** 推荐默认语速：1.10x，中文听感自然流畅，无拖泥带水感。 */
    const val DEFAULT_SPEECH_RATE = 1.10f

    /** 推荐默认音调：1.05x，音色清爽具亲和力。 */
    const val DEFAULT_PITCH = 1.05f

    /** 允许调节的最小语速。 */
    const val MIN_SPEECH_RATE = 0.75f

    /** 允许调节的最大语速。 */
    const val MAX_SPEECH_RATE = 1.75f

    /** 允许调节的最小音调。 */
    const val MIN_PITCH = 0.75f

    /** 允许调节的最大音调。 */
    const val MAX_PITCH = 1.50f
  }
}

/**
 * 供 UI 界面呈现的可用系统音色描述项。
 *
 * 包装 Android 原生 [android.speech.tts.Voice] 的属性，避免界面层直接耦合系统框架类。
 *
 * @property name 底层 Voice 唯一标识符（如 "cmn-cn-x-ccc-local"）。
 * @property displayName 用户可读的音色展示名称。
 * @property isHighQuality 是否为高保真或非常高质量的音色。
 * @property requiresNetwork 是否必须依赖网络连接。
 * @property locale 所属语言标签（如 "zh-CN"）。
 */
data class VoiceOption(
  val name: String,
  val displayName: String,
  val isHighQuality: Boolean,
  val requiresNetwork: Boolean,
  val locale: String,
)
