package com.example.voxmate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import com.example.voxmate.ai.CompanionAiChatSession
import com.example.voxmate.ai.SessionStatus
import com.example.voxmate.ai.VoxMateAiSessionManager
import com.example.voxmate.bridge.VrmBridgeController
import com.example.voxmate.ui.avatar.VrmAvatarView
import com.example.voxmate.ui.chat.ChatMessage
import com.example.voxmate.voice.AndroidSpeechInput
import com.example.voxmate.voice.AndroidSpeechOutput
import com.example.voxmate.voice.EmotionParser
import com.example.voxmate.voice.LipSyncDriver
import com.example.voxmate.voice.SpeechInputEvent
import com.example.voxmate.voice.SpeechOutputEvent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.example.voxmate.ui.drawer.VoxFeatureIntroDialog
import com.example.voxmate.ui.drawer.VoxFeatureItem
import com.example.voxmate.ui.drawer.VoxMateDrawerContent
import com.google.ai.edge.gallery.aicore.AiChatEvent
import com.google.ai.edge.gallery.aicore.AiChatRequest
import com.google.ai.edge.gallery.modelmanagerui.ModelManagerRoute
import com.google.ai.edge.gallery.modelmanagerui.SelectedModel
import kotlinx.coroutines.launch
import java.util.UUID

private val VoxBlue = Color(0xFF2557D6)
private val PageBackground = Color(0xFFF5F7FB)
private val ReadyGreen = Color(0xFF218A55)

@Composable
fun VoxMateApp() {
  MaterialTheme {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    // 统管端侧本地大模型生命周期（自动嗅探已下载的 Qwen2.5 / Gemma 模型）
    val aiSessionManager = remember { VoxMateAiSessionManager(context, coroutineScope) }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
      NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
          VoiceChatHome(
            aiManager = aiSessionManager,
            onConfigureModels = { navController.navigate("models") },
          )
        }
        composable("models") {
          ModelManagerRoute(
            onClose = { navController.popBackStack() },
            onModelSelected = { model ->
              aiSessionManager.loadModel(model)
              navController.popBackStack()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun VoiceChatHome(
  aiManager: VoxMateAiSessionManager,
  onConfigureModels: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  val sessionStatus by aiManager.status.collectAsState()
  val currentModel by aiManager.currentModel.collectAsState()
  val chatSession = aiManager

  var bridgeController by remember { mutableStateOf<VrmBridgeController?>(null) }
  var isAvatarReady by remember { mutableStateOf(false) }
  var isSpeaking by remember { mutableStateOf(false) }
  var isListening by remember { mutableStateOf(false) }
  var isThinking by remember { mutableStateOf(false) }
  var cameraMode by remember { mutableStateOf("upper") }
  var selectedPose by remember { mutableStateOf(0) }
  var isPosesExpanded by remember { mutableStateOf(false) }

  // 消息列表与输入框状态
  var inputText by remember { mutableStateOf("") }
  var isTextMode by remember { mutableStateOf(false) }
  val messages = remember { mutableStateListOf<ChatMessage>() }

  // Gemini 风格侧边栏抽屉与场景功能介绍弹窗状态
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  var introDialogFeature by remember { mutableStateOf<VoxFeatureItem?>(null) }

  // 语音输入与输出
  val speechOutput = remember { AndroidSpeechOutput(context, coroutineScope) }
  val speechInput = remember { AndroidSpeechInput(context, coroutineScope) }

  val lipSyncDriver = remember {
    LipSyncDriver(bridgeControllerProvider = { bridgeController }, coroutineScope = coroutineScope).apply {
      attachSpeechOutput(speechOutput)
    }
  }

  // 麦克风动态权限申请 Launcher
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { isGranted ->
    if (isGranted) {
      isListening = true
      speechInput.startListening()
    } else {
      Toast.makeText(context, "请在系统设置中允许麦克风权限以使用语音交互", Toast.LENGTH_SHORT).show()
    }
  }

  fun requestAndStartListening() {
    val hasMicPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasMicPermission) {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    } else {
      isListening = true
      speechInput.startListening()
    }
  }

  // 核心发信与 AI 驱动流程
  fun handleSendMessage(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || isThinking || isSpeaking) return

    messages.add(ChatMessage(isUser = true, text = trimmed))
    inputText = ""
    isThinking = true
    bridgeController?.setExpression("relaxed")

    coroutineScope.launch {
      val assistantMsgId = UUID.randomUUID().toString()
      messages.add(ChatMessage(id = assistantMsgId, isUser = false, text = "", isStreaming = true))

      val fullResponse = StringBuilder()
      chatSession.send(AiChatRequest(text = trimmed)).collect { event ->
        when (event) {
          is AiChatEvent.TextDelta -> {
            fullResponse.append(event.text)
            val parsed = EmotionParser.parse(fullResponse.toString())
            parsed.expression?.let { bridgeController?.setExpression(it) }
            val index = messages.indexOfFirst { it.id == assistantMsgId }
            if (index >= 0) {
              messages[index] = messages[index].copy(text = parsed.cleanText)
            }
          }
          is AiChatEvent.Completed -> {
            isThinking = false
            val parsed = EmotionParser.parse(fullResponse.toString())
            parsed.expression?.let { bridgeController?.setExpression(it) }
            val index = messages.indexOfFirst { it.id == assistantMsgId }
            if (index >= 0) {
              messages[index] = messages[index].copy(text = parsed.cleanText, isStreaming = false)
            }
            speechOutput.speak(parsed.cleanText)
          }
          is AiChatEvent.Cancelled -> {
            isThinking = false
            val index = messages.indexOfFirst { it.id == assistantMsgId }
            if (index >= 0) {
              messages[index] = messages[index].copy(isStreaming = false)
            }
          }
        }
      }
    }
  }

  // 监听语音识别结果
  LaunchedEffect(speechInput) {
    speechInput.events.collect { event ->
      when (event) {
        is SpeechInputEvent.PartialText -> {
          // 实时展示正在转写的文字
          inputText = event.text
        }
        is SpeechInputEvent.FinalText -> {
          isListening = false
          handleSendMessage(event.text)
        }
        is SpeechInputEvent.Error -> {
          isListening = false
        }
      }
    }
  }

  // 监听语音发声状态
  LaunchedEffect(speechOutput) {
    speechOutput.events.collect { event ->
      when (event) {
        is SpeechOutputEvent.Started -> isSpeaking = true
        is SpeechOutputEvent.Completed, is SpeechOutputEvent.Error -> {
          isSpeaking = false
          bridgeController?.resetExpression()
        }
      }
    }
  }

  // 自动滚动到最新消息
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      scrollState.animateScrollTo(scrollState.maxValue)
    }
  }

  DisposableEffect(speechOutput, speechInput) {
    onDispose {
      lipSyncDriver.stopLipSync()
      speechOutput.release()
      speechInput.release()
      chatSession.stop()
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(
        drawerContainerColor = Color(0xFF131314),
        modifier = Modifier.width(310.dp).fillMaxHeight(),
      ) {
        VoxMateDrawerContent(
          activeFeatureId = "avatar",
          recentChats = messages.filter { it.isUser }.map { it.text }.reversed().distinct(),
          onNewChat = {
            coroutineScope.launch { drawerState.close() }
            speechOutput.stop()
            speechInput.stopListening()
            isSpeaking = false
            isListening = false
            isThinking = false
            messages.clear()
            bridgeController?.resetPose()
            bridgeController?.resetExpression()
          },
          onSelectFeature = { feature ->
            coroutineScope.launch { drawerState.close() }
            if (feature.id != "avatar") {
              introDialogFeature = feature
            }
          },
          onOpenModelManager = {
            coroutineScope.launch { drawerState.close() }
            onConfigureModels()
          },
          onSelectRecentChat = { chatText ->
            coroutineScope.launch { drawerState.close() }
            inputText = chatText
            isTextMode = true
          },
        )
      }
    },
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(PageBackground),
    ) {
      // 1. 底层：真正全屏铺满的 3D AI 数字人视口
      VrmAvatarView(
        modifier = Modifier.fillMaxSize(),
        onControllerReady = { controller -> bridgeController = controller },
        onAvatarReady = { isAvatarReady = true },
      )

      // 2. 顶部悬浮栏（Gemini 风格左上角圆钮 + VoxMate ▾ + 右侧模型状态与新建对话）
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Gemini 风格左上角双横线圆形菜单按钮
          Surface(
            onClick = { coroutineScope.launch { drawerState.open() } },
            shape = CircleShape,
            color = Color(0xFF1E293B),
            shadowElevation = 4.dp,
            modifier = Modifier.size(42.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Column(
                verticalArrangement = Arrangement.spacedBy(4.5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  Modifier
                    .width(18.dp)
                    .height(2.5.dp)
                    .background(Color.White, RoundedCornerShape(2.dp))
                )
                Box(
                  Modifier
                    .width(18.dp)
                    .height(2.5.dp)
                    .background(Color.White, RoundedCornerShape(2.dp))
                )
              }
            }
          }

          Spacer(Modifier.width(12.dp))

          // 品牌标题与下拉展开指示符（点击亦可呼出侧边栏）
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable { coroutineScope.launch { drawerState.open() } }
              .padding(horizontal = 4.dp, vertical = 4.dp),
          ) {
            Text(
              text = stringResource(com.example.voxmate.R.string.app_name),
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0F172A),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "▾",
              fontSize = 16.sp,
              color = Color(0xFF64748B),
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // 模型状态胶囊按钮
          Surface(
            onClick = onConfigureModels,
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 2.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val (dotColor, label) = when (val s = sessionStatus) {
                is SessionStatus.Ready -> ReadyGreen to s.modelName
                is SessionStatus.Loading -> Color(0xFFE59B2F) to "装载中..."
                is SessionStatus.Error -> Color(0xFFE53935) to "模型异常"
                SessionStatus.Idle -> Color(0xFF64748B) to "智能伴侣"
              }
              Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
              Spacer(Modifier.width(6.dp))
              Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF334155),
                fontWeight = FontWeight.Medium,
              )
            }
          }

          // 发起新对话快捷圆形按钮
          Surface(
            onClick = {
              speechOutput.stop()
              speechInput.stopListening()
              isSpeaking = false
              isListening = false
              isThinking = false
              messages.clear()
              bridgeController?.resetPose()
              bridgeController?.resetExpression()
            },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 2.dp,
            modifier = Modifier.size(38.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text("✏️", fontSize = 15.sp)
            }
          }
        }
      }

      // 3. 悬浮相机景别与表情快捷条（右上角，浮动在 3D 人物侧方）
      if (isAvatarReady) {
        Surface(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 64.dp, end = 16.dp),
          shape = RoundedCornerShape(14.dp),
          color = Color.White.copy(alpha = 0.88f),
          border = BorderStroke(1.dp, Color(0x33CBD5E1)),
          shadowElevation = 2.dp,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            listOf(
              "upper" to com.example.voxmate.R.string.camera_mode_upper,
              "full" to com.example.voxmate.R.string.camera_mode_full,
              "portrait" to com.example.voxmate.R.string.camera_mode_portrait,
            ).forEach { (mode, strId) ->
              val isSelected = cameraMode == mode
              Surface(
                onClick = {
                  cameraMode = mode
                  bridgeController?.setCameraMode(mode)
                },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) VoxBlue else Color.Transparent,
              ) {
                Text(
                  text = stringResource(strId),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  fontSize = 11.sp,
                  color = if (isSelected) Color.White else Color(0xFF475569),
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
              }
            }

            Box(Modifier.width(1.dp).height(14.dp).background(Color(0xFFCBD5E1)))

            // 微笑
            Text(
              text = "微笑",
              fontSize = 11.sp,
              color = VoxBlue,
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { bridgeController?.setExpression("happy") }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            )

            // 复位
            Text(
              text = "复位",
              fontSize = 11.sp,
              color = Color.Gray,
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                  bridgeController?.resetExpression()
                  bridgeController?.resetPose()
                  selectedPose = 0
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            )
          }
        }
      }

      // 4. 数字人发声 / 思考 / 倾听状态微标（悬浮于左上方）
      Surface(
        modifier = Modifier
          .align(Alignment.TopStart)
          .statusBarsPadding()
          .padding(top = 64.dp, start = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0x33CBD5E1)),
        shadowElevation = 2.dp,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val (badgeColor, badgeText) = when {
            isListening -> Color(0xFFE53935) to "正在倾听..."
            isThinking -> Color(0xFF2557D6) to "端侧思考中..."
            isSpeaking -> Color(0xFFE59B2F) to "正在发声..."
            sessionStatus is SessionStatus.Loading -> Color(0xFFE59B2F) to "模型装载中..."
            sessionStatus is SessionStatus.Ready -> ReadyGreen to "端侧大模型就绪"
            isAvatarReady -> ReadyGreen to "3D AI 数字人"
            else -> Color.Gray to "加载中..."
          }
          Box(Modifier.size(7.dp).background(badgeColor, CircleShape))
          Spacer(Modifier.width(6.dp))
          Text(badgeText, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
        }
      }

      // 5. 中下部沉浸式半透明对话气泡流 (Floating Dialogue Subtitles)
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = if (isPosesExpanded) 360.dp else 96.dp)
          .padding(horizontal = 16.dp)
          .fillMaxWidth(),
      ) {
        if (messages.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.90f),
            shadowElevation = 2.dp,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
          ) {
            Text(
              text = stringResource(com.example.voxmate.R.string.chat_empty_prompt),
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
              fontSize = 13.sp,
              color = Color(0xFF64748B),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
          }
        } else {
          // 最多展示最新 3 条消息，支持滚动
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            messages.takeLast(3).forEach { msg ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
              ) {
                Surface(
                  shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (msg.isUser) 16.dp else 4.dp,
                    bottomEnd = if (msg.isUser) 4.dp else 16.dp,
                  ),
                  color = if (msg.isUser) VoxBlue else Color.White.copy(alpha = 0.92f),
                  shadowElevation = if (msg.isUser) 2.dp else 3.dp,
                  border = if (msg.isUser) null else BorderStroke(1.dp, Color(0x33CBD5E1)),
                  modifier = Modifier.fillMaxWidth(0.85f),
                ) {
                  val cleanMsgText = if (msg.isUser) msg.text else EmotionParser.parse(msg.text).cleanText
                  Text(
                    text = if (msg.isStreaming && cleanMsgText.isEmpty()) "正在思考中..." else cleanMsgText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 14.sp,
                    color = if (msg.isUser) Color.White else Color(0xFF1E293B),
                    lineHeight = 20.sp,
                  )
                }
              }
            }
          }
        }
      }

      // 6. 底部悬浮交互操作栏 (Voice Mic / Text Keyboard / 25 Poses Toggle)
      Surface(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
      ) {
        if (isTextMode) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
              onClick = { isTextMode = false },
              shape = CircleShape,
              color = Color(0xFFF1F5F9),
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text("🎤", fontSize = 18.sp)
              }
            }

            OutlinedTextField(
              value = inputText,
              onValueChange = { inputText = it },
              placeholder = { Text(stringResource(com.example.voxmate.R.string.chat_input_hint), fontSize = 13.sp) },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(18.dp),
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
              keyboardActions = KeyboardActions(onSend = { handleSendMessage(inputText) }),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VoxBlue,
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC),
              ),
            )

            Button(
              onClick = { handleSendMessage(inputText) },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = VoxBlue),
              modifier = Modifier.size(44.dp),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text("➤", color = Color.White, fontSize = 16.sp)
            }
          }
        } else {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            // 键盘输入切换
            Surface(
              onClick = { isTextMode = true },
              shape = CircleShape,
              color = Color(0xFFF1F5F9),
              modifier = Modifier.size(46.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text("⌨️", fontSize = 20.sp)
              }
            }

            // 中心语音大按键
            Button(
              onClick = {
                if (isListening) {
                  speechInput.stopListening()
                  isListening = false
                } else if (isSpeaking) {
                  speechOutput.stop()
                  isSpeaking = false
                } else {
                  requestAndStartListening()
                }
              },
              modifier = Modifier.height(52.dp).weight(1f).padding(horizontal = 10.dp),
              shape = RoundedCornerShape(22.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = when {
                  isListening -> Color(0xFFE53935)
                  isSpeaking -> Color(0xFFE59B2F)
                  else -> ReadyGreen
                }
              ),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
              Text(
                text = when {
                  isListening -> "🎙 正在倾听... 点击发送"
                  isSpeaking -> "🔊 正在说话... 点击打断"
                  else -> "🎙 点击开始语音"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
              )
            }

            // 姿态展开切换
            Surface(
              onClick = { isPosesExpanded = !isPosesExpanded },
              shape = CircleShape,
              color = if (isPosesExpanded) VoxBlue else Color(0xFFF1F5F9),
              modifier = Modifier.size(46.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  if (isPosesExpanded) "▲" else "💃",
                  fontSize = 18.sp,
                  color = if (isPosesExpanded) Color.White else Color.Unspecified,
                )
              }
            }
          }
        }
      }

      // 7. 25 动作姿态展开面板 (当 isPosesExpanded 时从底部浮起)
      AnimatedVisibility(
        visible = isPosesExpanded,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(com.example.voxmate.R.string.avatar_poses_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B),
              )
              TextButton(
                onClick = {
                  selectedPose = 0
                  bridgeController?.resetPose()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              ) {
                Text(
                  text = stringResource(com.example.voxmate.R.string.avatar_pose_stand),
                  fontSize = 12.sp,
                  color = if (selectedPose == 0) VoxBlue else Color.Gray,
                  fontWeight = if (selectedPose == 0) FontWeight.Bold else FontWeight.Normal,
                )
              }
            }

            Spacer(Modifier.height(6.dp))

            // 5x5 矩阵
            for (row in 0 until 5) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                for (col in 0 until 5) {
                  val poseId = row * 5 + col + 1
                  val isSelected = selectedPose == poseId
                  Surface(
                    onClick = {
                      selectedPose = poseId
                      bridgeController?.setPose(poseId)
                    },
                    modifier = Modifier.weight(1f).aspectRatio(1.35f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) VoxBlue else Color(0xFFF1F5F9),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = "$poseId",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF334155),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // 场景功能说明弹窗
  introDialogFeature?.let { feature ->
    VoxFeatureIntroDialog(
      feature = feature,
      onDismiss = { introDialogFeature = null },
      onGoToModels = {
        introDialogFeature = null
        onConfigureModels()
      },
    )
  }
}

@Preview
@Composable
private fun VoxMateAppPreview() {
  VoxMateApp()
}
