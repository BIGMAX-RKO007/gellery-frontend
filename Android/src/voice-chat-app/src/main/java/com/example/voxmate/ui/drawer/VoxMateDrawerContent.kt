package com.example.voxmate.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxmate.R

/**
 * 侧边栏功能场景项数据实体。
 *
 * @property id 唯一标识字符串
 * @property icon 显示在菜单左侧的 Emoji 或符号图标
 * @property titleRes 功能标题文本资源 ID
 * @property descRes 功能简短副标题文本资源 ID
 * @property fullIntro 功能详细机制说明（来自原 Gallery 首页场景规范）
 * @property requiredModel 所需或推荐的端侧模型名称
 */
data class VoxFeatureItem(
  val id: String,
  val icon: String,
  val titleRes: Int,
  val descRes: Int,
  val fullIntro: String,
  val requiredModel: String,
)

/**
 * 预设的全量功能场景列表，对应原 Google AI Edge Gallery 首页核心能力与 VoxMate 3D 数字人。
 */
val ALL_VOXMATE_FEATURES: List<VoxFeatureItem> = listOf(
  VoxFeatureItem(
    id = "avatar",
    icon = "💃",
    titleRes = R.string.menu_avatar_chat,
    descRes = R.string.menu_avatar_chat_desc,
    fullIntro = "端侧 3D 虚拟数字人交互伴侣：支持实时语音识别输入、打字机流式回答、TTS 语音合成、30fps 实时唇形开合与 25 组全身动作姿态变换。",
    requiredModel = "内置智能伴侣 / Gemma-4-E2B-it",
  ),
  VoxFeatureItem(
    id = "models",
    icon = "🧠",
    titleRes = R.string.menu_model_manager,
    descRes = R.string.menu_model_manager_desc,
    fullIntro = "端侧大语言模型中心：浏览、下载、校验与切换本地 LiteRT-LM 模型权重（Gemma 4、Qwen 2.5、Llama 3 等），支持断点续传与离线推理。",
    requiredModel = "模型清单管理器",
  ),
  VoxFeatureItem(
    id = "personas",
    icon = "🎭",
    titleRes = R.string.persona_store_title,
    descRes = R.string.menu_persona_store_desc,
    fullIntro = "",
    requiredModel = "",
  ),
  VoxFeatureItem(
    id = "agent_skills",
    icon = "⚡",
    titleRes = R.string.menu_agent_skills,
    descRes = R.string.menu_agent_skills_desc,
    fullIntro = "智能体技能系统：支持让端侧模型读取 Skill 指令、执行 JavaScript、连接 MCP Server 工具以及触发 Android Intent 自动化任务。",
    requiredModel = "Gemma-4-E2B-it / Gemma-4-E4B-it",
  ),
  VoxFeatureItem(
    id = "ask_image",
    icon = "🖼️",
    titleRes = R.string.menu_ask_image,
    descRes = R.string.menu_ask_image_desc,
    fullIntro = "多模态视觉问答：基于端侧视觉多模态大模型，支持拍照或从相册选择图片，对场景、图表、文本与物体进行深度理解与多轮对话。",
    requiredModel = "Gemma 4 E2B/E4B, Gemma 3n E2B/E4B",
  ),
  VoxFeatureItem(
    id = "audio_scribe",
    icon = "🎙️",
    titleRes = R.string.menu_audio_scribe,
    descRes = R.string.menu_audio_scribe_desc,
    fullIntro = "长音频转写与理解：录制会议或上传音频片段，模型直接处理原生音频输入，完成跨语言翻译、会议纪要生成与针对性问答。",
    requiredModel = "Gemma 4 E2B/E4B, Gemma 3n E2B/E4B",
  ),
  VoxFeatureItem(
    id = "prompt_lab",
    icon = "🧪",
    titleRes = R.string.menu_prompt_lab,
    descRes = R.string.menu_prompt_lab_desc,
    fullIntro = "Prompt 实验室：单轮实验与生成参数调优平台，支持对比不同模型的温度（Temperature）、Top-K、Top-P 输出效果，适用于开发者调试。",
    requiredModel = "Gemma 4, Qwen 2.5, DeepSeek-R1",
  ),
  VoxFeatureItem(
    id = "tiny_garden",
    icon = "🌺",
    titleRes = R.string.menu_tiny_garden,
    descRes = R.string.menu_tiny_garden_desc,
    fullIntro = "端侧花园小游戏：3×3 虚拟花园演示。通过自然语言下达播种、浇水、收获指令，模型自动执行端侧 Function Calling 驱动游戏状态更新。",
    requiredModel = "TinyGarden-270M",
  ),
  VoxFeatureItem(
    id = "mobile_actions",
    icon = "📱",
    titleRes = R.string.menu_mobile_actions,
    descRes = R.string.menu_mobile_actions_desc,
    fullIntro = "手机系统操作指令：端侧模型将用户自然语言转换为手机实际操作，如开关手电筒、创建联系人、打开 Wi-Fi 设置与地图导航等。",
    requiredModel = "MobileActions-270M",
  ),
  VoxFeatureItem(
    id = "scrapbook",
    icon = "✂️",
    titleRes = R.string.menu_scrapbook,
    descRes = R.string.menu_scrapbook_desc,
    fullIntro = "Magic Touch 智能抠图拼贴：使用交互式分割模型点击图片主体即可智能抠图，支持多图层自由缩放、旋转、叠放背景制作拼贴手帐。",
    requiredModel = "Magic touch 分割模型",
  ),
)

/**
 * 参考 Gemini 客户端侧边栏抽屉内容视图。
 *
 * @param activeFeatureId 当前激活的功能 ID
 * @param recentChats 最近对话问题列表
 * @param onNewChat 发起新对话回调
 * @param onSelectFeature 选中特定功能项回调
 * @param onOpenModelManager 打开模型管理回调
 * @param onOpenPersonaStore 打开人物商店回调；不在抽屉内读取或应用人物
 * @param onSelectRecentChat 选中历史问题回调
 */
@Composable
fun VoxMateDrawerContent(
  activeFeatureId: String = "avatar",
  recentChats: List<String>,
  onNewChat: () -> Unit,
  onSelectFeature: (VoxFeatureItem) -> Unit,
  onOpenModelManager: () -> Unit,
  onOpenPersonaStore: () -> Unit,
  onSelectRecentChat: (String) -> Unit,
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF131314))
      .verticalScroll(scrollState)
      .padding(horizontal = 16.dp, vertical = 20.dp),
  ) {
    // 抽屉顶部品牌行
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.menu_title),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2557D6).copy(alpha = 0.35f),
      ) {
        Text(
          text = "Pro · 端侧伴侣",
          fontSize = 11.sp,
          color = Color(0xFF93C5FD),
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }

    Spacer(Modifier.height(14.dp))

    // 发起新对话主操作按钮
    Surface(
      onClick = onNewChat,
      shape = RoundedCornerShape(14.dp),
      color = Color(0xFF1E1F20),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("✏️", fontSize = 18.sp)
        Spacer(Modifier.width(14.dp))
        Text(
          text = stringResource(R.string.menu_new_chat),
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFFE3E3E3),
        )
      }
    }

    Spacer(Modifier.height(8.dp))

    // 搜索对话内容占位条目
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = Color.Transparent,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("🔍", fontSize = 17.sp)
        Spacer(Modifier.width(14.dp))
        Text(
          text = stringResource(R.string.menu_search_chat),
          fontSize = 14.sp,
          color = Color(0xFF8E918F),
        )
      }
    }

    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = Color(0xFF282A2C), thickness = 1.dp)
    Spacer(Modifier.height(14.dp))

    // 功能场景标题
    Text(
      text = stringResource(R.string.menu_section_features),
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = Color(0xFF8E918F),
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )

    Spacer(Modifier.height(4.dp))

    // 渲染全量功能场景列表
    ALL_VOXMATE_FEATURES.forEach { feature ->
      val isActive = feature.id == activeFeatureId
      Surface(
        onClick = {
          if (feature.id == "models") {
            onOpenModelManager()
          } else if (feature.id == "personas") {
            onOpenPersonaStore()
          } else {
            onSelectFeature(feature)
          }
        },
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Color(0xFF282A2C) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(feature.icon, fontSize = 20.sp)
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(feature.titleRes),
              fontSize = 14.sp,
              fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
              color = if (isActive) Color.White else Color(0xFFE3E3E3),
            )
            Text(
              text = stringResource(feature.descRes),
              fontSize = 11.sp,
              color = Color(0xFF8E918F),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (isActive) {
            Box(
              modifier = Modifier.size(7.dp).background(Color(0xFF34D399), CircleShape)
            )
          } else if (feature.id == "models" || feature.id == "personas") {
            Text("➔", color = Color(0xFF8E918F), fontSize = 12.sp)
          }
        }
      }
    }

    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = Color(0xFF282A2C), thickness = 1.dp)
    Spacer(Modifier.height(14.dp))

    // 最近对话段落
    Text(
      text = stringResource(R.string.menu_section_recent),
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = Color(0xFF8E918F),
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )

    Spacer(Modifier.height(4.dp))

    if (recentChats.isEmpty()) {
      Text(
        text = stringResource(R.string.menu_no_recent_chats),
        fontSize = 13.sp,
        color = Color(0xFF5E6266),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      )
    } else {
      recentChats.take(5).forEach { chatText ->
        Surface(
          onClick = { onSelectRecentChat(chatText) },
          shape = RoundedCornerShape(10.dp),
          color = Color.Transparent,
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("💬", fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(
              text = chatText,
              fontSize = 13.sp,
              color = Color(0xFFC4C7C5),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Color(0xFF282A2C), thickness = 1.dp)
    Spacer(Modifier.height(12.dp))

    // 抽屉底部：本地端侧设备隐私卡片
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(36.dp).background(Color(0xFF2557D6), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text("📱", fontSize = 18.sp)
      }
      Spacer(Modifier.width(10.dp))
      Column {
        Text(
          text = stringResource(R.string.menu_local_device),
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFFE3E3E3),
        )
        Text(
          text = stringResource(R.string.menu_privacy_safe),
          fontSize = 11.sp,
          color = Color(0xFF34D399),
        )
      }
    }
  }
}

/**
 * 点击功能场景时弹出的详情对话框，引导用户了解场景并跳转到模型管理下载适配模型。
 *
 * @param feature 选中的功能实体
 * @param onDismiss 关闭弹窗
 * @param onGoToModels 前往模型管理页面
 */
@Composable
fun VoxFeatureIntroDialog(
  feature: VoxFeatureItem,
  onDismiss: () -> Unit,
  onGoToModels: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(20.dp),
    containerColor = Color.White,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(feature.icon, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Text(
          text = stringResource(feature.titleRes),
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF0F172A),
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = feature.fullIntro,
          fontSize = 14.sp,
          color = Color(0xFF334155),
          lineHeight = 21.sp,
        )

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFFF1F5F9),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "兼容与推荐模型：",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF475569),
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = feature.requiredModel,
              fontSize = 13.sp,
              color = Color(0xFF2557D6),
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onDismiss()
          onGoToModels()
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2557D6)),
      ) {
        Text(stringResource(R.string.feature_intro_dialog_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(
          stringResource(R.string.feature_intro_dialog_dismiss),
          color = Color(0xFF64748B),
        )
      }
    },
  )
}
