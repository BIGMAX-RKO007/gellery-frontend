package com.example.voxmate.persona.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.voxmate.persona.PersonaSource

/** 为人物包索引和当前人物选择提供原子数据库操作。 */
@Dao
interface PersonaDao {
  /**
   * 插入或刷新人物包索引。
   *
   * @param entity 已完成资源校验的人物包索引
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPackage(entity: PersonaPackageEntity)

  /**
   * 查找指定人物版本。
   *
   * @param personaId 人物 ID
   * @param version 人物版本
   * @return 已安装索引，不存在时返回 null
   */
  @Query("SELECT * FROM persona_packages WHERE personaId = :personaId AND version = :version")
  suspend fun findPackage(personaId: String, version: String): PersonaPackageEntity?

  /**
   * 返回全部已安装人物版本，供后续人物选择页面展示。
   *
   * @return 按人物名称和安装时间排序的不可变结果
   */
  @Query("SELECT * FROM persona_packages ORDER BY displayName, installedAtEpochMillis DESC")
  suspend fun listPackages(): List<PersonaPackageEntity>

  /**
   * 读取当前人物选择。
   *
   * @return 用户明确选择的配置行；首次启动或旧 master 自动选择返回 null
   */
  @Query("SELECT * FROM active_persona WHERE slot = 0 AND explicitlySelected = 1")
  suspend fun getActive(): ActivePersonaEntity?

  /** 清除显式人物选择；不删除人物包，后续启动继续使用应用原有对话方式。 */
  @Query("DELETE FROM active_persona WHERE slot = 0")
  suspend fun clearActive()

  /**
   * 清理同一 assets 目录已被替换的旧索引，不删除真实资源或外部安装包。
   * @param sourcePath 内置人物目录
   * @param version 当前实际存在的版本
   */
  @Query("DELETE FROM persona_packages WHERE sourceType = 'ASSET' AND sourcePath = :sourcePath AND version != :version")
  suspend fun deleteSupersededAssetVersions(sourcePath: String, version: String)

  /**
   * 原子登记内置人物；只升级此前明确选中的相同内置目录，空选择始终保持为空。
   * @param entity 已校验的当前 assets 人物索引；不允许外部安装包调用此入口
   * Room 在后台事务执行；登记失败时索引和选择一起回滚。
   */
  @Transaction
  suspend fun registerBundled(entity: PersonaPackageEntity) {
    require(entity.sourceType == PersonaSource.ASSET.name)
    val selection = getActive()
    val current = selection?.let { findPackage(it.personaId, it.version) }
    upsertPackage(entity)
    if (current?.sourceType == PersonaSource.ASSET.name &&
      current.sourcePath == entity.sourcePath) {
      activate(entity.personaId, entity.version, System.currentTimeMillis())
    }
    deleteSupersededAssetVersions(entity.sourcePath, entity.version)
  }

  /**
   * 写入当前人物选择。
   *
   * @param entity 已确认存在的人物版本
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun setActiveEntity(entity: ActivePersonaEntity)

  /**
   * 在事务内确认人物存在并切换当前选择。
   *
   * @param personaId 目标人物 ID
   * @param version 目标人物版本
   * @param updatedAtEpochMillis 切换时间，单位毫秒
   * @throws IllegalArgumentException 目标人物尚未安装时抛出
   */
  @Transaction
  suspend fun activate(personaId: String, version: String, updatedAtEpochMillis: Long) {
    requireNotNull(findPackage(personaId, version)) { "目标人物尚未安装。" }
    setActiveEntity(
      ActivePersonaEntity(
        personaId = personaId,
        version = version,
        updatedAtEpochMillis = updatedAtEpochMillis,
      )
    )
  }
}
