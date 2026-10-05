package com.example.voxmate.persona.data

import androidx.room.Entity

/**
 * 保存一个已发现或已安装人物包的索引，不复制人物 JSON 和声音资源本体。
 *
 * @property personaId 人物全局稳定 ID
 * @property version 此次安装的人物包版本
 * @property sourceType `ASSET` 或 `INSTALLED`，决定资源读取方式
 * @property sourcePath assets 相对目录或应用私有目录绝对路径
 * @property displayName 安装时解析的人物名称，用于无需读取整包的列表展示
 * @property author 人物包作者
 * @property packageSha256 可选原始 ZIP SHA-256；内置人物没有 ZIP 时为空
 * @property installedAtEpochMillis 安装完成的 Unix 时间戳，单位毫秒
 */
@Entity(tableName = "persona_packages", primaryKeys = ["personaId", "version"])
data class PersonaPackageEntity(
  val personaId: String,
  val version: String,
  val sourceType: String,
  val sourcePath: String,
  val displayName: String,
  val author: String,
  val packageSha256: String?,
  val installedAtEpochMillis: Long,
)
