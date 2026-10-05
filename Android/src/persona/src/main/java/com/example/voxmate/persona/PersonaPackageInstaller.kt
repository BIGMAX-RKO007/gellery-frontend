package com.example.voxmate.persona

import android.content.Context
import com.example.voxmate.persona.data.PersonaDao
import com.example.voxmate.persona.data.PersonaPackageEntity
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把已经下载的 `.voxpersona` ZIP 安全安装到应用私有目录。
 *
 * 安装器不发起网络请求。上层下载完成后传入输入流和服务端清单提供的 SHA-256，安装器
 * 负责大小限制、路径穿越防护、内容校验和同文件系统原子改名。
 *
 * @param context Android Context；内部只使用 Application Context
 * @param dao 人物数据库访问接口
 * @param codec 人物 JSON 编解码器
 */
internal class PersonaPackageInstaller(
  context: Context,
  private val dao: PersonaDao,
  private val codec: PersonaJsonCodec,
) {
  /** 应用级 Context，用于定位不可导出的私有人物目录。 */
  private val appContext = context.applicationContext

  /**
   * 安装一个人物 ZIP 包。
   *
   * @param input 人物包输入流；方法返回后流会被关闭
   * @param expectedSha256 下载清单提供的完整 ZIP SHA-256；本地开发导入可为 null
   * @return 已写入数据库的人物包索引
   * @throws PersonaInstallException 文件过大、哈希不符、路径不安全或版本已存在时抛出
   */
  suspend fun install(input: InputStream, expectedSha256: String?): PersonaPackageEntity =
    withContext(Dispatchers.IO) {
      val personasRoot = File(appContext.filesDir, PERSONAS_DIRECTORY).apply { mkdirs() }
      val stagingDirectory = File(personasRoot, "$STAGING_PREFIX${UUID.randomUUID()}")
      val zipFile = File(stagingDirectory, PACKAGE_FILE_NAME)
      try {
        if (!stagingDirectory.mkdirs()) throw PersonaInstallException("无法创建人物包安装目录。")
        val actualSha256 = copyPackage(input, zipFile)
        if (expectedSha256 != null && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
          throw PersonaInstallException("人物包 SHA-256 校验失败。")
        }
        val extractedRoot = File(stagingDirectory, EXTRACTED_DIRECTORY).apply { mkdirs() }
        extractSafely(zipFile, extractedRoot)
        val manifestFile = File(extractedRoot, MANIFEST_FILE_NAME)
        if (!manifestFile.isFile) throw PersonaInstallException("人物包缺少 manifest.json。")
        val manifest = codec.decodeManifest(manifestFile.readText(Charsets.UTF_8))
        val personaFile = safePackageFile(extractedRoot, manifest.personaFile)
        val persona = codec.decodePersona(personaFile.readText(Charsets.UTF_8))
        manifest.voiceConfigFile?.let { path ->
          codec.decodeVoice(safePackageFile(extractedRoot, path).readText(Charsets.UTF_8))
        }
        validateDeclaredFiles(extractedRoot, manifest)

        val targetDirectory = File(File(personasRoot, manifest.id), manifest.version)
        if (targetDirectory.exists()) {
          throw PersonaInstallException("相同版本的人物包已经安装。")
        }
        targetDirectory.parentFile?.mkdirs()
        if (!extractedRoot.renameTo(targetDirectory)) {
          throw PersonaInstallException("无法完成物理人物包安装。")
        }
        val entity =
          PersonaPackageEntity(
            personaId = manifest.id,
            version = manifest.version,
            sourceType = PersonaSource.INSTALLED.name,
            sourcePath = targetDirectory.absolutePath,
            displayName = persona.identity.name,
            author = manifest.author,
            packageSha256 = actualSha256,
            installedAtEpochMillis = System.currentTimeMillis(),
          )
        dao.upsertPackage(entity)
        entity
      } catch (error: PersonaInstallException) {
        throw error
      } catch (error: Throwable) {
        throw PersonaInstallException("人物包安装失败。", error)
      } finally {
        stagingDirectory.deleteRecursively()
      }
    }

  /** 复制原始 ZIP、限制下载体积并计算整个文件 SHA-256。 */
  private fun copyPackage(input: InputStream, target: File): String {
    val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
    var totalBytes = 0L
    input.use { source ->
      FileOutputStream(target).buffered().use { output ->
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
          val count = source.read(buffer)
          if (count < 0) break
          totalBytes += count
          if (totalBytes > MAX_ARCHIVE_BYTES) throw PersonaInstallException("人物包体积超过限制。")
          digest.update(buffer, 0, count)
          output.write(buffer, 0, count)
        }
      }
    }
    return digest.digest().toHexString()
  }

  /** 解压 ZIP 并限制文件数量、单次总写入量和目标路径。 */
  private fun extractSafely(zipFile: File, root: File) {
    val canonicalRoot = root.canonicalFile
    var entryCount = 0
    var totalBytes = 0L
    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entryCount += 1
        if (entryCount > MAX_ENTRY_COUNT) throw PersonaInstallException("人物包文件数量超过限制。")
        val target = File(canonicalRoot, entry.name).canonicalFile
        if (!target.path.startsWith(canonicalRoot.path + File.separator)) {
          throw PersonaInstallException("人物包包含不安全路径。")
        }
        if (entry.isDirectory) {
          target.mkdirs()
        } else {
          target.parentFile?.mkdirs()
          FileOutputStream(target).buffered().use { output ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
              val count = zip.read(buffer)
              if (count < 0) break
              totalBytes += count
              if (totalBytes > MAX_EXTRACTED_BYTES) {
                throw PersonaInstallException("人物包解压后体积超过限制。")
              }
              output.write(buffer, 0, count)
            }
          }
        }
        zip.closeEntry()
      }
    }
  }

  /** 校验清单声明的每个资源文件，防止安装后内容被静默替换。 */
  private fun validateDeclaredFiles(root: File, manifest: PersonaManifest) {
    manifest.files.forEach { (relativePath, expectedHash) ->
      val file = safePackageFile(root, relativePath)
      val actualHash = file.inputStream().buffered().use(::sha256)
      if (!actualHash.equals(expectedHash, ignoreCase = true)) {
        throw PersonaInstallException("人物包资源校验失败：$relativePath")
      }
    }
  }

  /** 返回确定处于人物根目录中的现有普通文件。 */
  private fun safePackageFile(root: File, relativePath: String): File {
    val canonicalRoot = root.canonicalFile
    val file = File(canonicalRoot, relativePath).canonicalFile
    if (!file.path.startsWith(canonicalRoot.path + File.separator) || !file.isFile) {
      throw PersonaInstallException("人物包资源不存在或路径不安全。")
    }
    return file
  }

  /** 读取完整输入流并返回 SHA-256 十六进制文本。 */
  private fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      digest.update(buffer, 0, count)
    }
    return digest.digest().toHexString()
  }

  /** 将摘要字节转换为固定两位的小写十六进制文本。 */
  private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

  /** 集中保存人物包磁盘目录与资源上限。 */
  private companion object {
    /** 应用私有人物目录名。 */
    const val PERSONAS_DIRECTORY = "personas"

    /** 安装临时目录前缀，便于与正式人物 ID 区分。 */
    const val STAGING_PREFIX = ".installing-"

    /** 临时保存的原始人物包文件名。 */
    const val PACKAGE_FILE_NAME = "package.voxpersona"

    /** 临时解压目录名。 */
    const val EXTRACTED_DIRECTORY = "content"

    /** 人物包根目录固定清单名。 */
    const val MANIFEST_FILE_NAME = "manifest.json"

    /** 摘要算法标准名称。 */
    const val SHA_256_ALGORITHM = "SHA-256"

    /** 流复制缓冲区字节数。 */
    const val COPY_BUFFER_SIZE = 8 * 1024

    /** 原始人物包最大体积，单位字节。 */
    const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024

    /** 人物包解压后的最大总体积，单位字节。 */
    const val MAX_EXTRACTED_BYTES = 200L * 1024 * 1024

    /** 单个人物包允许的最大 ZIP 条目数。 */
    const val MAX_ENTRY_COUNT = 128
  }
}

/**
 * 表示人物包在落盘、解压或完整性校验阶段失败。
 *
 * @param message 不包含 Token 或隐私数据的失败原因
 * @param cause 可选底层文件异常
 */
class PersonaInstallException(message: String, cause: Throwable? = null) :
  IllegalStateException(message, cause)
