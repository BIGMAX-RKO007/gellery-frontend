package com.example.voxmate.persona

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证人物 JSON 校验和系统指令编译不依赖 Android 运行环境。 */
class PersonaPromptBuilderTest {
  /** 仓库 assets 中的默认人物、清单和声音配置必须始终能被正式解析器读取。 */
  @Test
  fun bundledDefaultPersonaIsValid() {
    val root = File("src/main/assets/personas/default")
    val codec = PersonaJsonCodec()
    val manifest = codec.decodeManifest(File(root, "manifest.json").readText())
    val persona = codec.decodePersona(File(root, manifest.personaFile).readText())
    val voice = codec.decodeVoice(File(root, requireNotNull(manifest.voiceConfigFile)).readText())

    assertEquals("com.voxmate.persona.ayan", manifest.id)
    assertEquals("阿焰", persona.identity.name)
    assertEquals("android-system-tts", voice.engine)
  }

  /** 默认风格关键字段和应用固定边界必须进入最终系统指令。 */
  @Test
  fun buildIncludesIdentityStyleExamplesAndSafetyBoundary() {
    val persona =
      PersonaDocument(
        identity =
          PersonaIdentity(
            name = "阿焰",
            genderPresentation = "女性",
            role = "可靠搭档",
          ),
        personality =
          PersonaPersonality(
            traits = listOf(PersonaTrait("直率", 0.9)),
            profanityLevel = 1,
          ),
        communication =
          PersonaCommunication(
            tone = "简短直接",
            verbosity = "short",
            humorStyle = "冷幽默",
          ),
        examples =
          listOf(PersonaConversationExample(user = "怎么改？", assistant = "先改报错，别乱拆。")),
      )

    val prompt = PersonaPromptBuilder().build(persona)

    assertTrue(prompt.contains("姓名：阿焰"))
    assertTrue(prompt.contains("直率（强度 90%）"))
    assertTrue(prompt.contains("回答长度：short"))
    assertTrue(prompt.contains("不得仇恨、真实威胁、鼓励伤害或持续霸凌"))
    assertTrue(prompt.contains("阿焰：先改报错，别乱拆。"))
  }

  /** 超过上下文预算的示例不会全部写入系统指令。 */
  @Test
  fun buildLimitsExamplesInPrompt() {
    val examples =
      (1..8).map { index ->
        PersonaConversationExample(user = "问题$index", assistant = "回答$index")
      }
    val persona =
      PersonaDocument(
        identity = PersonaIdentity(name = "测试人物", role = "测试"),
        examples = examples,
      )

    val prompt = PersonaPromptBuilder().build(persona)

    assertTrue(prompt.contains("问题6"))
    assertFalse(prompt.contains("问题7"))
    assertTrue(prompt.contains("不要连续复用相同句首"))
  }

  /** 非法人物 ID 和越界性格强度必须在安装前被拒绝。 */
  @Test
  fun codecRejectsUnsafeManifestAndOutOfRangeTrait() {
    val codec = PersonaJsonCodec()

    val invalidManifest =
      """{"schemaVersion":1,"id":"../bad","version":"1.0.0","personaFile":"persona.json"}"""
    val invalidPersona =
      """{"identity":{"name":"人物","role":"助手"},"personality":{"traits":[{"name":"暴躁","intensity":2.0}]}}"""

    assertFailsPersonaFormat { codec.decodeManifest(invalidManifest) }
    assertFailsPersonaFormat { codec.decodePersona(invalidPersona) }
  }

  /** 执行断言并要求代码抛出 [PersonaFormatException]。 */
  private fun assertFailsPersonaFormat(block: () -> Unit) {
    try {
      block()
      throw AssertionError("预期人物格式校验失败。")
    } catch (_: PersonaFormatException) {
      // 预期异常表示不安全数据已在进入数据库前被拒绝。
    }
  }
}
