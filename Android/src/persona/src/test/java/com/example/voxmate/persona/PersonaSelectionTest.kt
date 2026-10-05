package com.example.voxmate.persona

import com.example.voxmate.persona.data.ActivePersonaEntity
import com.example.voxmate.persona.data.PersonaDao
import com.example.voxmate.persona.data.PersonaPackageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 使用内存 DAO 验证生产登记策略；不替代 Room 真机持久化和事务验证。 */
class PersonaSelectionTest {
  /** 旧 master 的自动选择不会因资源升级被当成商店选择启用。 */
  @Test fun legacyAutomaticSelectionDoesNotActivatePersona() = runBlocking {
    val dao = MemoryPersonaDao()
    dao.registerBundled(bundled("1.1.0"))
    dao.setActiveEntity(ActivePersonaEntity(
      personaId = "ayan", version = "1.1.0", updatedAtEpochMillis = 1L,
      explicitlySelected = false,
    ))
    dao.registerBundled(bundled("1.2.0"))
    assertNull(dao.getActive())
  }

  /** 首次登记只能安装索引，不能替用户选择人物。 */
  @Test fun firstRegistrationDoesNotActivatePersona() = runBlocking {
    val dao = MemoryPersonaDao()
    dao.registerBundled(bundled("1.2.0"))
    assertNull(dao.getActive())
    assertEquals(1, dao.listPackages().size)
  }

  /** 明确选择内置人物后跟随升级，并删除不再对应真实 assets 的旧索引。 */
  @Test fun selectedBundledPersonaFollowsUpgrade() = runBlocking {
    val dao = MemoryPersonaDao()
    dao.registerBundled(bundled("1.1.0"))
    dao.activate("ayan", "1.1.0", 1L)
    dao.registerBundled(bundled("1.2.0"))
    assertEquals("1.2.0", dao.getActive()?.version)
    assertNull(dao.findPackage("ayan", "1.1.0"))
  }

  /** 清除选择后，即使再次登记或升级内置人物也不允许重新自动启用。 */
  @Test fun clearingSelectionSurvivesRegistrationAndUpgrade() = runBlocking {
    val dao = MemoryPersonaDao()
    dao.registerBundled(bundled("1.1.0"))
    dao.activate("ayan", "1.1.0", 1L)
    dao.clearActive()
    dao.registerBundled(bundled("1.2.0"))
    assertNull(dao.getActive())
    assertEquals(1, dao.listPackages().size)
  }

  /** 用户选择的外部人物不会被内置资源登记覆盖。 */
  @Test fun installedPersonaIsNotReplacedByBundledDefault() = runBlocking {
    val dao = MemoryPersonaDao()
    val external = bundled("2.0.0").copy(
      personaId = "external", sourceType = PersonaSource.INSTALLED.name, sourcePath = "/personas/external",
    )
    dao.upsertPackage(external)
    dao.activate("external", "2.0.0", 1L)
    dao.registerBundled(bundled("1.2.0"))
    assertEquals("external", dao.getActive()?.personaId)
  }

  /**
   * 构造已校验的内置索引测试夹具，不访问真实 assets。
   * @param version 人物版本
   * @return 对应固定人物目录的测试索引
   */
  private fun bundled(version: String) = PersonaPackageEntity(
    "ayan", version, PersonaSource.ASSET.name, "personas/default", "阿焰", "VoxMate", null, 0L,
  )
}

/** 单测试生命周期的内存索引，复用生产 DAO 默认方法，不启动 Android 数据库。 */
private class MemoryPersonaDao : PersonaDao {
  /** 用人物 ID 和版本索引测试包；不保存实际文件。 */
  private val packages = mutableMapOf<Pair<String, String>, PersonaPackageEntity>()
  /** 当前显式选择；null 模拟数据库没有配置行。 */
  private var active: ActivePersonaEntity? = null

  /** 保存测试索引；当前测试协程串行调用。 */
  override suspend fun upsertPackage(entity: PersonaPackageEntity) {
    packages[entity.personaId to entity.version] = entity
  }
  /** 按 ID 与版本读取夹具，不存在时返回 null。 */
  override suspend fun findPackage(personaId: String, version: String) = packages[personaId to version]
  /** 返回索引快照，外部不能修改内部 Map。 */
  override suspend fun listPackages() = packages.values.toList()
  /** 返回当前配置行，未选择时为 null。 */
  override suspend fun getActive() = active?.takeIf { it.explicitlySelected }
  /** 保存当前选择，模拟 Room 的替换写入。 */
  override suspend fun setActiveEntity(entity: ActivePersonaEntity) { active = entity }
  /** 清除当前选择，不删除索引。 */
  override suspend fun clearActive() { active = null }
  /** 清理已经被 assets 升级替换的索引，保留外部安装包。 */
  override suspend fun deleteSupersededAssetVersions(sourcePath: String, version: String) {
    packages.entries.removeAll {
      it.value.sourceType == PersonaSource.ASSET.name &&
        it.value.sourcePath == sourcePath && it.value.version != version
    }
  }
}
