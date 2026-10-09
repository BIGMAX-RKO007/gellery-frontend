package com.example.voxmate.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceSettings] 与 [VoiceOption] 领域配置单元测试。
 */
class VoiceSettingsTest {

  @Test
  fun defaultValuesAreNaturalAndWithinBounds() {
    val settings = VoiceSettings()
    assertEquals(1.10f, settings.speechRate, 0.001f)
    assertEquals(1.05f, settings.pitch, 0.001f)
    assertNull(settings.voiceName)

    assertTrue(settings.speechRate >= VoiceSettings.MIN_SPEECH_RATE)
    assertTrue(settings.speechRate <= VoiceSettings.MAX_SPEECH_RATE)
    assertTrue(settings.pitch >= VoiceSettings.MIN_PITCH)
    assertTrue(settings.pitch <= VoiceSettings.MAX_PITCH)
  }

  @Test
  fun customSettingsCopyCorrectly() {
    val initial = VoiceSettings()
    val updated = initial.copy(
      speechRate = 1.25f,
      pitch = 1.15f,
      voiceName = "zh-cn-x-female-hq",
    )

    assertEquals(1.25f, updated.speechRate, 0.001f)
    assertEquals(1.15f, updated.pitch, 0.001f)
    assertEquals("zh-cn-x-female-hq", updated.voiceName)
  }

  @Test
  fun voiceOptionPropertiesHoldCorrectly() {
    val option = VoiceOption(
      name = "cmn-cn-x-ccc-local",
      displayName = "女性音色 (CN)",
      isHighQuality = true,
      requiresNetwork = false,
      locale = "zh-CN",
    )

    assertEquals("cmn-cn-x-ccc-local", option.name)
    assertEquals("女性音色 (CN)", option.displayName)
    assertTrue(option.isHighQuality)
    assertTrue(!option.requiresNetwork)
    assertEquals("zh-CN", option.locale)
  }
}
