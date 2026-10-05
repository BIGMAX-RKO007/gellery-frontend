package com.example.voxmate.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 UI 语言值不会把不受支持的配置传入原生模型。 */
class SenseVoiceLanguageTest {
  /** 支持语言保持不变，未知语言安全回退到自动检测。 */
  @Test
  fun normalizesSupportedAndUnknownLanguages() {
    assertEquals("zh", normalizeSenseVoiceLanguage("ZH"))
    assertEquals("en", normalizeSenseVoiceLanguage("en"))
    assertEquals("ja", normalizeSenseVoiceLanguage("ja"))
    assertEquals("ko", normalizeSenseVoiceLanguage("ko"))
    assertEquals("auto", normalizeSenseVoiceLanguage("unknown"))
  }
}
