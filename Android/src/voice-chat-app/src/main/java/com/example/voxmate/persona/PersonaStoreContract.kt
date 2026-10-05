package com.example.voxmate.persona

import androidx.compose.ui.graphics.Color

/**
 * 人物商店页面使用的轻量展示状态，不承担人物包下载或数据库操作。
 *
 * @property items 页面展示的人物列表
 * @property activePersonaId 当前启用人物 ID；没有可用人物时为 null
 * @property loading 是否正在读取索引，此时不可应用人物
 * @property applying 是否正在切换会话并持久化选择
 * @property errorMessage 读取或应用失败的原因；成功时为空
 * @property activePersonaVersion 当前人物版本，与 ID 一起标识选择，防止多版本都被标为当前
 */
data class PersonaStoreUiState(
  val items: List<PersonaStoreItem>,
  val activePersonaId: String?,
  val loading: Boolean = false,
  val applying: Boolean = false,
  val errorMessage: String? = null,
  val activePersonaVersion: String? = null,
)

/**
 * 人物商店卡片的纯展示模型，后续接入服务端时应由网络 DTO 映射得到。
 *
 * @property id 与人物包 manifest 一致的全局 ID
 * @property name 本地化人物名称
 * @property description 本地化人物简介
 * @property tags 本地化性格标签
 * @property author 人物包发布者
 * @property version 人物包版本
 * @property accentColor 人物占位头像使用的强调色
 * @property installed 当前版本是否已经安装
 */
data class PersonaStoreItem(
  val id: String,
  val name: String,
  val description: String,
  val tags: List<String>,
  val author: String,
  val version: String,
  val accentColor: Color,
  val installed: Boolean,
)
