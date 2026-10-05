package com.example.voxmate.persona

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import com.example.voxmate.ai.VoxMateAiSessionManager
import com.example.voxmate.persona.data.PersonaPackageEntity
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.res.stringResource
import com.example.voxmate.R
import kotlinx.coroutines.launch

/**
 * 连接人物索引、会话选择与纯展示页面；在线下载暂为占位，数据库操作统一委托仓库。
 *
 * @param repository 应用级人物仓库，负责资源读取和持久化
 * @param aiManager 应用级会话管理器，保证人物指令与持久化选择一致
 * @param onBack 用户离开人物商店时调用
 */
@Composable
fun PersonaStoreRoute(
  repository: PersonaRepository,
  aiManager: VoxMateAiSessionManager,
  onBack: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  /** 仅持有页面提示所需 Context，不交给后台仓库。 */
  val context = LocalContext.current
  /** 已安装索引与页面操作状态，不承担模型生命周期。 */
  var installed by remember { mutableStateOf<List<PersonaPackageEntity>>(emptyList()) }
  /** 首次读取与重试期间禁止选择人物。 */
  var loading by remember { mutableStateOf(true) }
  /** 切换期间禁止返回，避免取消导致会话与数据库部分更新。 */
  var applying by remember { mutableStateOf(false) }
  /** 可重试的页面错误，避免失败被静默吞掉。 */
  var errorMessage by remember { mutableStateOf<String?>(null) }
  /** 成功应用的人物来自管理器，不能用本地点击状态伪装选中。 */
  val activePersona by aiManager.activePersona.collectAsState()
  val unavailableMessage = stringResource(R.string.persona_store_download_unavailable)

  /** 在当前页面协程读取索引；文件 IO 由仓库调度，取消正常传播，错误展示在页面。 */
  suspend fun reload() {
    loading = true
    errorMessage = null
    try {
      aiManager.awaitPersonaRestored()
      installed = repository.listInstalled()
      errorMessage = aiManager.personaError.value
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      errorMessage = error.message ?: error.javaClass.simpleName
    } finally {
      loading = false
    }
  }

  /**
   * 上报用户明确选择，串行更新会话与持久化数据。
   * @param item 已安装人物；null 表示恢复默认；网络占位由点击处单独处理
   * 主线程发起可取消页面协程，失败展示原因，完成后解除操作锁。
   */
  fun applySelection(item: PersonaStoreItem?) {
    if (loading || applying) return
    applying = true
    errorMessage = null
    scope.launch {
      try {
        aiManager.selectPersona(item?.id, item?.version)
        applying = false
        snackbarHostState.showSnackbar(context.getString(
          if (item == null) R.string.persona_store_restored else R.string.persona_store_applied
        ))
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        errorMessage = error.message ?: error.javaClass.simpleName
      } finally {
        applying = false
      }
    }
  }
  LaunchedEffect(repository, aiManager) { reload() }
  BackHandler(enabled = applying) { /* 切换提交完成后再允许返回，防止中途取消。 */ }
  val state =
    PersonaStoreUiState(
      items = fakePersonaStoreItems(installed),
      activePersonaId = activePersona?.bundle?.manifest?.id,
      activePersonaVersion = activePersona?.bundle?.manifest?.version,
      loading = loading,
      applying = applying,
      errorMessage = errorMessage,
    )
  PersonaStoreScreen(
    state = state,
    snackbarHostState = snackbarHostState,
    onBack = onBack,
    onPersonaAction = { item ->
      if (item.installed) applySelection(item)
      else scope.launch { snackbarHostState.showSnackbar(unavailableMessage) }
    },
    onReset = { applySelection(null) },
    onRetry = { scope.launch { reload() } },
  )
}

/**
 * 合并真实安装索引和商店预览人物，真实安装包优先展示且允许选择。
 *
 * @param installed 仓库确认的已安装版本，只有真实存在的包才允许使用
 * @return 不会触发真实下载的不可变展示列表，已安装条目保留实际 ID 和版本
 */
@Composable
private fun fakePersonaStoreItems(
  installed: List<PersonaPackageEntity>,
): List<PersonaStoreItem> =
  listOf(
    PersonaStoreItem(
      id = DEFAULT_PERSONA_ID,
      name = stringResource(R.string.persona_ayan_name),
      description = stringResource(R.string.persona_ayan_description),
      tags =
        listOf(
          stringResource(R.string.persona_tag_concise),
          stringResource(R.string.persona_tag_hot_tempered),
          stringResource(R.string.persona_tag_dry_humor),
        ),
      author = "VoxMate",
      version = installed.firstOrNull { it.personaId == DEFAULT_PERSONA_ID }?.version ?: "1.2.0",
      accentColor = androidx.compose.ui.graphics.Color(0xFFE85D3F),
      installed = installed.any { it.personaId == DEFAULT_PERSONA_ID },
    ),
    PersonaStoreItem(
      id = "com.voxmate.persona.mumu",
      name = stringResource(R.string.persona_mumu_name),
      description = stringResource(R.string.persona_mumu_description),
      tags =
        listOf(
          stringResource(R.string.persona_tag_gentle),
          stringResource(R.string.persona_tag_patient),
          stringResource(R.string.persona_tag_companion),
        ),
      author = "VoxMate Labs",
      version = "1.0.0",
      accentColor = androidx.compose.ui.graphics.Color(0xFF8A6FD1),
      installed = false,
    ),
    PersonaStoreItem(
      id = "com.voxmate.persona.linxu",
      name = stringResource(R.string.persona_linxu_name),
      description = stringResource(R.string.persona_linxu_description),
      tags =
        listOf(
          stringResource(R.string.persona_tag_rational),
          stringResource(R.string.persona_tag_structured),
          stringResource(R.string.persona_tag_calm),
        ),
      author = "VoxMate Labs",
      version = "1.0.0",
      accentColor = androidx.compose.ui.graphics.Color(0xFF3978C5),
      installed = false,
    ),
    PersonaStoreItem(
      id = "com.voxmate.persona.yebo",
      name = stringResource(R.string.persona_yebo_name),
      description = stringResource(R.string.persona_yebo_description),
      tags =
        listOf(
          stringResource(R.string.persona_tag_mysterious),
          stringResource(R.string.persona_tag_witty),
          stringResource(R.string.persona_tag_storyteller),
        ),
      author = "VoxMate Labs",
      version = "1.0.0",
      accentColor = androidx.compose.ui.graphics.Color(0xFF3D4658),
      installed = false,
    ),
  ).let { previews ->
    /** 通用简介不承诺尚未接入的声音克隆或 VRM 外观替换能力。 */
    val installedDescription = stringResource(R.string.persona_store_installed_description)
    installed.map { entity ->
      val preview = previews.firstOrNull { it.id == entity.personaId }
      preview?.copy(version = entity.version, author = entity.author, installed = true)
        ?: PersonaStoreItem(
          id = entity.personaId,
          name = entity.displayName,
          description = installedDescription,
          tags = emptyList(),
          author = entity.author,
          version = entity.version,
          accentColor = androidx.compose.ui.graphics.Color(0xFF3978C5),
          installed = true,
        )
    } + previews.filter { preview -> installed.none { it.personaId == preview.id } }
  }

/** 默认 assets 人物 ID，必须与内置 manifest 保持一致。 */
private const val DEFAULT_PERSONA_ID = "com.voxmate.persona.ayan"
