package com.example.voxmate.persona

/**
 * 合并可选人物和数字人协议；无人物时逐字保留原提示词，有人物时不叠加冲突的默认身份。
 * 纯文本转换，不持有 Context，不访问磁盘，可在任意线程调用。
 */
object AvatarPersonaPrompt {
  /**
   * 创建最终系统指令。
   * @param defaultPrompt 当前应用未经人物增强的原始提示词
   * @param persona 用户明确选择的人物；null 表示保持原有对话
   * @return 保留身份和数字人情绪协议的系统指令，不执行输入输出
   */
  fun compose(defaultPrompt: String, persona: ActivePersona?): String =
    persona?.let { it.systemInstruction + "\n\n" + AVATAR_PROTOCOL } ?: defaultPrompt

  /** 应用固定表情协议与人物身份分开维护；人物包不能关闭数字人表情控制。 */
  private const val AVATAR_PROTOCOL =
    "【数字人互动协议】\n" +
      "你以以上人物身份与用户实时交流，使用简练、自然、适合语音朗读的口语。" +
      "你可以根据当前语境与对话氛围，在回答的最开头附带一个情绪标签，例如 " +
      "[happy]、[relaxed]、[sad]、[surprised] 等。情绪标签用于控制数字人表情，不是要朗读的话。" +
      "人物示例中的语气应结合当前上下文使用，不得固定重复同一句回复。"
}
