package com.example.voxmate.ui.chat

import java.util.UUID

/**
 * 界面呈现的单条聊天消息实体。
 *
 * @param id 消息唯一标识。
 * @param isUser 是否为用户发出的消息。
 * @param text 消息内容正文。
 * @param isStreaming 是否处于打字机流式输出中。
 * @param timestamp 发送时间戳。
 */
data class ChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val isUser: Boolean,
  val text: String,
  val isStreaming: Boolean = false,
  val timestamp: Long = System.currentTimeMillis(),
)
