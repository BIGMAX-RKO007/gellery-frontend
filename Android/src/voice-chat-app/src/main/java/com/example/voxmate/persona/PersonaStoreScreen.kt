package com.example.voxmate.persona

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.voxmate.R

/**
 * 渲染人物商店列表和人物操作，不读取仓库或执行网络请求。
 *
 * @param state 当前人物和商店人物列表
 * @param snackbarHostState 页面提示状态，由 Route 持有
 * @param onBack 返回上一页事件
 * @param onPersonaAction 用户点击人物安装或使用按钮时调用
 * @param onReset 清除人物选择并恢复原有对话的事件
 * @param onRetry 重新读取人物列表；Screen 不访问数据库
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaStoreScreen(
  state: PersonaStoreUiState,
  snackbarHostState: SnackbarHostState,
  onBack: () -> Unit,
  onPersonaAction: (PersonaStoreItem) -> Unit,
  onReset: () -> Unit = {},
  onRetry: () -> Unit = {},
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.persona_store_title)) },
        navigationIcon = {
          TextButton(onClick = onBack, enabled = !state.applying) {
            Text(stringResource(R.string.persona_store_back))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF5F7FB)),
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = Color(0xFFF5F7FB),
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(contentPadding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "store-description") {
        Column {
          Text(
            text = stringResource(R.string.persona_store_heading),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(R.string.persona_store_description),
            modifier = Modifier.padding(top = 8.dp),
            color = Color(0xFF687086),
          )
        }
      }
      item(key = "default-conversation") {
        if (state.activePersonaId == null) {
          Text(stringResource(R.string.persona_store_default))
        }
        if (state.activePersonaId != null || state.errorMessage != null) {
          TextButton(onClick = onReset, enabled = !state.loading && !state.applying) {
            Text(stringResource(R.string.persona_store_reset))
          }
        }
      }
      if (state.loading || state.applying) {
        item(key = "operation-progress") {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
              stringResource(if (state.loading) R.string.persona_store_loading else R.string.persona_store_applying),
              modifier = Modifier.padding(start = 12.dp),
            )
          }
        }
      }
      state.errorMessage?.let { error ->
        item(key = "operation-error") {
          Column {
            Text(
              stringResource(R.string.persona_store_error, error),
              color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry, enabled = !state.loading && !state.applying) {
              Text(stringResource(R.string.persona_store_retry))
            }
          }
        }
      }
      if (!state.loading && state.items.isEmpty()) {
        item(key = "empty-store") { Text(stringResource(R.string.persona_store_empty)) }
      }
      items(items = state.items, key = { item -> "${item.id}@${item.version}" }) { item ->
        PersonaStoreCard(
          item = item,
          isActive = state.activePersonaId == item.id &&
            (state.activePersonaVersion == null || state.activePersonaVersion == item.version),
          onAction = { onPersonaAction(item) },
          enabled = !state.loading && !state.applying,
        )
      }
      item(key = "store-footer") {
        Text(
          text = stringResource(R.string.persona_store_placeholder_notice),
          modifier = Modifier.padding(vertical = 10.dp),
          color = Color(0xFF687086),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

/**
 * 渲染一个人物商店卡片。
 *
 * @param item 人物展示数据
 * @param isActive 该人物是否为当前人物
 * @param onAction 安装、使用或查看人物的统一占位事件
 * @param enabled 是否允许操作；读取或切换期间禁用以防重复提交
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PersonaStoreCard(
  item: PersonaStoreItem,
  isActive: Boolean,
  onAction: () -> Unit,
  enabled: Boolean,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(54.dp).background(item.accentColor, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = item.name.take(1),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = item.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            if (isActive) {
              Text(
                text = stringResource(R.string.persona_store_current),
                modifier =
                  Modifier.padding(start = 8.dp)
                    .background(Color(0xFFE4F4EA), RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                color = Color(0xFF218A55),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
          Text(
            text = stringResource(R.string.persona_store_author_version, item.author, item.version),
            color = Color(0xFF7A8192),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
      Text(
        text = item.description,
        modifier = Modifier.padding(top = 14.dp),
        color = Color(0xFF4F5668),
      )
      FlowRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        item.tags.forEach { tag -> PersonaTag(tag) }
      }
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onAction,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !isActive,
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          stringResource(
            when {
              isActive -> R.string.persona_store_in_use
              item.installed -> R.string.persona_store_use
              else -> R.string.persona_store_download
            }
          )
        )
      }
    }
  }
}

/**
 * 渲染一个不可交互的人物性格标签。
 *
 * @param text 已经本地化的标签文字
 */
@Composable
private fun PersonaTag(text: String) {
  Surface(color = Color(0xFFEEF1F7), shape = RoundedCornerShape(20.dp)) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      color = Color(0xFF596174),
      style = MaterialTheme.typography.labelMedium,
    )
  }
}

/** 提供不依赖仓库和导航的 Android Studio 人物商店预览。 */
@Preview(showBackground = true)
@Composable
private fun PersonaStoreScreenPreview() {
  MaterialTheme {
    PersonaStoreScreen(
      state =
        PersonaStoreUiState(
          items =
            listOf(
              PersonaStoreItem(
                id = "preview",
                name = "阿焰",
                description = "说话精简、略带火气，但关键时刻很靠谱。",
                tags = listOf("精简", "急性子", "冷幽默"),
                author = "VoxMate",
                version = "1.0.0",
                accentColor = Color(0xFFE85D3F),
                installed = true,
              )
            ),
          activePersonaId = "preview",
        ),
      snackbarHostState = SnackbarHostState(),
      onBack = {},
      onPersonaAction = {},
    )
  }
}
