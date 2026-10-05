package com.example.voxmate.persona

import com.example.voxmate.ai.VoxMateAiSessionManager
import com.example.voxmate.voice.EmotionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证可选人物不改变未选择时的提示词，同时保留数字人表情与朗读分离协议。 */
class AvatarPersonaPromptTest {
  /** 没有选择人物时必须逐字保留当前提示词，不能自动引入阿焰。 */
  @Test fun noSelectionKeepsOriginalPrompt() {
    val original = VoxMateAiSessionManager.SYSTEM_INSTRUCTION_PROMPT
    assertEquals(original, AvatarPersonaPrompt.compose(original, null))
  }

  /** 显式人物取代默认身份，但必须保留情绪标签规则而非关闭表情控制。 */
  @Test fun selectedPersonaKeepsIdentityAndEmotionProtocol() {
    val document = PersonaDocument(identity = PersonaIdentity(name = "阿焰", role = "损友"))
    val persona = ActivePersona(
      PersonaBundle(PersonaManifest(), document, null, PersonaSource.ASSET),
      PersonaPromptBuilder().build(document),
    )
    val prompt = AvatarPersonaPrompt.compose(VoxMateAiSessionManager.SYSTEM_INSTRUCTION_PROMPT, persona)
    assertTrue(prompt.contains("姓名：阿焰"))
    assertTrue(prompt.contains("[happy]"))
    assertTrue(prompt.contains("情绪标签用于控制数字人表情"))
    assertFalse(prompt.contains("你是 VoxMate，一个温暖"))
    assertEquals(VoxMateAiSessionManager.SYSTEM_INSTRUCTION_PROMPT,
      AvatarPersonaPrompt.compose(VoxMateAiSessionManager.SYSTEM_INSTRUCTION_PROMPT, null))
  }

  /** 保留的表情标签被现有解析器提取，不会作为文本交给 TTS 朗读。 */
  @Test fun emotionTagStillDrivesAvatarWithoutBeingSpoken() {
    val parsed = EmotionParser.parse("[happy] 说吧，我听着。")
    assertEquals("happy", parsed.expression)
    assertEquals("说吧，我听着。", parsed.cleanText)
  }
}
