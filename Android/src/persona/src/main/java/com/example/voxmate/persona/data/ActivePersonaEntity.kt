package com.example.voxmate.persona.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/**
 * 保存应用当前启用的人物版本；固定单行设计避免多个包同时成为当前人物。
 *
 * @property slot 固定为 0 的配置行主键
 * @property personaId 当前人物 ID
 * @property version 当前人物版本
 * @property updatedAtEpochMillis 最近切换人物的 Unix 时间戳，单位毫秒
 * @property explicitlySelected 是否由用户主动选择；旧 master 自动选择迁移为 false，不能继续启用
 */
@Entity(tableName = "active_persona")
data class ActivePersonaEntity(
  @PrimaryKey val slot: Int = SINGLETON_SLOT,
  val personaId: String,
  val version: String,
  val updatedAtEpochMillis: Long,
  @ColumnInfo(defaultValue = "0") val explicitlySelected: Boolean = true,
) {
  /** 保存固定配置行键，数据库中只允许存在一项当前人物。 */
  companion object {
    /** 当前人物配置的固定主键。 */
    const val SINGLETON_SLOT = 0
  }
}
