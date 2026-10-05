package com.example.voxmate.persona

import android.content.Context
import com.example.voxmate.persona.data.PersonaDao
import com.example.voxmate.persona.data.PersonaDatabase
import com.example.voxmate.persona.data.PersonaPackageEntity
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VoxMate 使用人物能力的统一入口，负责默认人物、安装、选择、读取和提示词编译。
 *
 * @param context Android Context；内部组件只保留 Application Context
 * @param dao 人物数据库访问接口
 * @param codec 人物 JSON 编解码器
 * @param promptBuilder 人物系统指令编译器
 */
class PersonaRepository private constructor(
  context: Context,
  private val dao: PersonaDao,
  private val codec: PersonaJsonCodec,
  private val promptBuilder: PersonaPromptBuilder,
) {
  /** 用于读取 assets 和私有文件的 Application Context。 */
  private val appContext = context.applicationContext

  /** 根据数据库索引读取人物资源。 */
  private val reader = PersonaPackageReader(appContext, codec)

  /** 安装外部人物 ZIP 并写入数据库索引。 */
  private val installer = PersonaPackageInstaller(appContext, dao, codec)

  /**
   * 加载用户明确选择的人物；首次启动仅登记内置资源，不自动启用。
   *
   * @return 当前人物包及已编译系统指令；未选择或已清除选择时返回 null
   * @throws PersonaFormatException 默认人物缺失或人物资源损坏时抛出
   */
  suspend fun loadActive(): ActivePersona? {
    ensureDefaultPersona()
    val selection = dao.getActive() ?: return null
    val entity =
      dao.findPackage(selection.personaId, selection.version)
        ?: throw PersonaFormatException("当前人物索引不存在。")
    return reader.read(entity).toActivePersona()
  }

  /**
   * 安装一个上层已经下载完成的人物包，不自动改变当前人物。
   *
   * @param input `.voxpersona` ZIP 输入流；方法返回后会关闭
   * @param expectedSha256 服务端人物清单提供的完整包摘要；本地开发导入可为 null
   * @return 新安装的人物索引
   */
  suspend fun install(input: InputStream, expectedSha256: String?): PersonaPackageEntity =
    installer.install(input, expectedSha256)

  /**
   * 切换当前人物，并返回可以立即用于重建 AI 会话的人物快照。
   *
   * @param personaId 已安装人物 ID
   * @param version 已安装人物版本
   * @return 切换后的当前人物和系统指令
   */
  suspend fun activate(personaId: String, version: String): ActivePersona {
    val persona = readPersona(personaId, version)
    dao.activate(personaId, version, System.currentTimeMillis())
    return persona
  }

  /**
   * 校验并读取指定人物，不改变持久化选择；适合在切换会话之前预检。
   * @param personaId 已安装人物 ID
   * @param version 已安装人物版本
   * @return 人物快照；不存在或损坏时抛出格式异常；文件读取在 IO 调度器执行
   */
  suspend fun readPersona(personaId: String, version: String): ActivePersona {
    val entity = dao.findPackage(personaId, version)
      ?: throw PersonaFormatException("目标人物尚未安装。")
    return reader.read(entity).toActivePersona()
  }

  /** 清除当前人物选择但保留已安装资源；Room 在后台执行数据库写入。 */
  suspend fun clearActive() = dao.clearActive()

  /**
   * 列出全部内置和已安装人物版本，供后续人物选择页面使用。
   *
   * @return 数据库中的人物包轻量索引
   */
  suspend fun listInstalled(): List<PersonaPackageEntity> {
    ensureDefaultPersona()
    return dao.listPackages()
  }

  /** 登记内置人物；仅曾明确选择此内置人物的用户跟随资源升级，空选择保持为空。 */
  private suspend fun ensureDefaultPersona() =
    withContext(Dispatchers.IO) {
      val manifestJson = readDefaultAsset(MANIFEST_FILE_NAME)
      val manifest = codec.decodeManifest(manifestJson)
      val persona = codec.decodePersona(readDefaultAsset(manifest.personaFile))
      manifest.voiceConfigFile?.let { path -> codec.decodeVoice(readDefaultAsset(path)) }
      val entity =
        PersonaPackageEntity(
          personaId = manifest.id,
          version = manifest.version,
          sourceType = PersonaSource.ASSET.name,
          sourcePath = DEFAULT_ASSET_ROOT,
          displayName = persona.identity.name,
          author = manifest.author,
          packageSha256 = null,
          installedAtEpochMillis = DEFAULT_ASSET_INSTALL_TIME,
        )
      dao.registerBundled(entity)
    }

  /** 从固定默认人物 assets 目录读取 UTF-8 文本。 */
  private fun readDefaultAsset(relativePath: String): String =
    appContext.assets.open("$DEFAULT_ASSET_ROOT/$relativePath").bufferedReader().use { reader ->
      reader.readText()
    }

  /** 把人物包和统一编译器组合为应用可消费的当前人物。 */
  private fun PersonaBundle.toActivePersona(): ActivePersona =
    ActivePersona(bundle = this, systemInstruction = promptBuilder.build(persona))

  /** 提供默认生产仓库的构造入口和 assets 目录约定。 */
  companion object {
    /** 内置默认人物在合并后 assets 中的目录。 */
    const val DEFAULT_ASSET_ROOT = "personas/default"

    /** 默认人物固定清单文件名。 */
    private const val MANIFEST_FILE_NAME = "manifest.json"

    /** 内置资源没有真实安装时刻，使用零避免每次启动产生虚假变更。 */
    private const val DEFAULT_ASSET_INSTALL_TIME = 0L

    /**
     * 创建使用独立 Room 数据库的生产仓库。
     *
     * @param context 任意 Android Context
     * @return 可跨页面复用的人物仓库
     */
    fun create(context: Context): PersonaRepository =
      PersonaRepository(
        context = context.applicationContext,
        dao = PersonaDatabase.getInstance(context).personaDao(),
        codec = PersonaJsonCodec(),
        promptBuilder = PersonaPromptBuilder(),
      )
  }
}
