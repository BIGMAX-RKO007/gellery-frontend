package com.example.voxmate.persona

import android.content.Context
import com.example.voxmate.persona.data.PersonaPackageEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 根据人物索引从 assets 或应用私有目录读取并校验人物资源。
 *
 * @param context Android Context；内部只持有 Application Context
 * @param codec 人物 JSON 编解码器
 */
internal class PersonaPackageReader(
  context: Context,
  private val codec: PersonaJsonCodec,
) {
  /** 用于读取合并后 library assets 的 Application Context。 */
  private val appContext = context.applicationContext

  /**
   * 在 IO 调度器读取一个已索引人物包。
   *
   * @param entity Room 中的人物包索引
   * @return 已解析人物、声音配置和来源
   * @throws PersonaFormatException 索引来源无效或资源内容损坏时抛出
   */
  suspend fun read(entity: PersonaPackageEntity): PersonaBundle =
    withContext(Dispatchers.IO) {
      val source =
        try {
          PersonaSource.valueOf(entity.sourceType)
        } catch (error: IllegalArgumentException) {
          throw PersonaFormatException("人物包来源不受支持。", error)
        }
      val manifestJson = readText(source, entity.sourcePath, MANIFEST_FILE_NAME)
      val manifest = codec.decodeManifest(manifestJson)
      if (manifest.id != entity.personaId || manifest.version != entity.version) {
        throw PersonaFormatException("人物包索引与清单不一致。")
      }
      val persona = codec.decodePersona(readText(source, entity.sourcePath, manifest.personaFile))
      val voice =
        manifest.voiceConfigFile?.let { path ->
          codec.decodeVoice(readText(source, entity.sourcePath, path))
        }
      PersonaBundle(manifest = manifest, persona = persona, voice = voice, source = source)
    }

  /**
   * 从指定来源读取 UTF-8 文本。
   *
   * @param source 资源来源类型
   * @param rootPath assets 相对目录或本地绝对目录
   * @param relativePath 人物包内安全相对路径
   * @return UTF-8 文本
   */
  private fun readText(source: PersonaSource, rootPath: String, relativePath: String): String =
    when (source) {
      PersonaSource.ASSET ->
        appContext.assets.open("${rootPath.trimEnd('/')}/$relativePath").bufferedReader().use {
          reader -> reader.readText()
        }
      PersonaSource.INSTALLED -> {
        val root = File(rootPath).canonicalFile
        val target = File(root, relativePath).canonicalFile
        if (!target.path.startsWith(root.path + File.separator) || !target.isFile) {
          throw PersonaFormatException("人物包资源路径不安全或文件不存在。")
        }
        target.readText(Charsets.UTF_8)
      }
    }

  /** 保存人物包根目录固定清单文件名。 */
  private companion object {
    /** 每个人物包根目录必须存在的清单文件。 */
    const val MANIFEST_FILE_NAME = "manifest.json"
  }
}
