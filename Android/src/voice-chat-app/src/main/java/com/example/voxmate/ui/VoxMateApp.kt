package com.example.voxmate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.example.voxmate.ai.CompanionAiChatSession
import com.example.voxmate.bridge.VrmBridgeController
import com.example.voxmate.ui.avatar.VrmAvatarView
import com.example.voxmate.ui.chat.ChatMessage
import com.example.voxmate.voice.AndroidSpeechInput
import com.example.voxmate.voice.AndroidSpeechOutput
import com.example.voxmate.voice.EmotionParser
import com.example.voxmate.voice.LipSyncDriver
import com.example.voxmate.voice.SpeechInputEvent
import com.example.voxmate.voice.SpeechOutputEvent
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
    val navController = rememberNavController()
    var selectedModel by remember { mutableStateOf<SelectedModel?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
      NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
          VoiceChatHome(
            selectedModelName = selectedModel?.artifact?.displayName,
            onConfigureModels = { navController.navigate("models") },
          )
        }
        composable("models") {
          ModelManagerRoute(
            onClose = { navController.popBackStack() },
            onModelSelected = { model ->
              selectedModel = model
              navController.popBackStack()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun VoiceChatHome(selectedModelName: String?, onConfigureModels: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

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

  // 语音输入、语音输出与智能伴侣会话
  val speechOutput = remember { AndroidSpeechOutput(context, coroutineScope) }
  val speechInput = remember { AndroidSpeechInput(context, coroutineScope) }
  val chatSession = remember { CompanionAiChatSession() }

  val lipSyncDriver = remember {
    LipSyncDriver(bridgeControllerProvider = { bridgeController }, coroutineScope = coroutineScope).apply {
      attachSpeechOutput(speechOutput)
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
            val index = messages.indexOfFirst { it.id == assistantMsgId }
            if (index >= 0) {
              messages[index] = messages[index].copy(text = fullResponse.toString())
            }
          }
          is AiChatEvent.Completed -> {
            isThinking = false
            val index = messages.indexOfFirst { it.id == assistantMsgId }
            if (index >= 0) {
              messages[index] = messages[index].copy(isStreaming = false)
            }
            // 提取情绪标签并驱动发声与唇形
            val parsed = EmotionParser.parse(fullResponse.toString())
            parsed.expression?.let { bridgeController?.setExpression(it) }
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

  Column(
    modifier =
      Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(scrollState)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // 顶部顶栏：品牌与模型状态
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(40.dp).background(VoxBlue, RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            stringResource(com.example.voxmate.R.string.brand_initial),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
          )
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
          Text(
            stringResource(com.example.voxmate.R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(stringResource(com.example.voxmate.R.string.app_tagline), color = Color(0xFF687086), fontSize = 12.sp)
        }
      }

      // 模型状态胶囊按钮
      Surface(
        onClick = onConfigureModels,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.size(6.dp).background(ReadyGreen, CircleShape))
          Spacer(Modifier.width(6.dp))
          Text(
            text = selectedModelName ?: "智能伴侣",
            fontSize = 12.sp,
            color = Color(0xFF334155),
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }

    Spacer(Modifier.height(14.dp))

    // 3D 数字人核心视口卡片 (高度 320dp)
    Card(
      modifier = Modifier.fillMaxWidth().height(320.dp),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        VrmAvatarView(
          modifier = Modifier.fillMaxSize(),
          onControllerReady = { controller -> bridgeController = controller },
          onAvatarReady = { isAvatarReady = true },
        )

        // 数字人交互状态标签（左上角）
        Surface(
          modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
          shape = RoundedCornerShape(12.dp),
          color = Color(0xCCF5F7FB),
        ) {
          val statusText = when {
            isSpeaking -> stringResource(com.example.voxmate.R.string.voice_demo_speaking)
            isThinking -> "正在思考..."
            isListening -> stringResource(com.example.voxmate.R.string.chat_listening)
            isAvatarReady -> stringResource(com.example.voxmate.R.string.avatar_title)
            else -> stringResource(com.example.voxmate.R.string.avatar_loading)
          }
          val statusColor = when {
            isSpeaking -> ReadyGreen
            isThinking -> Color(0xFFE59B2F)
            isListening -> Color(0xFFE53935)
            else -> VoxBlue
          }
          Text(
            text = statusText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = statusColor,
            fontWeight = FontWeight.Medium,
          )
        }

        // 摄像机景别切换栏（右上角：半身 / 全身 / 特写）
        if (isAvatarReady) {
          Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                color = if (isSelected) VoxBlue else Color(0xCCF0F4FF),
              ) {
                Text(
                  text = stringResource(strId),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  fontSize = 11.sp,
                  color = if (isSelected) Color.White else VoxBlue,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
              }
            }
          }
        }

        // 底部快捷操作栏（动作抽屉切换 & 微笑）
        if (isAvatarReady) {
          Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { isPosesExpanded = !isPosesExpanded },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isPosesExpanded) VoxBlue else Color(0xFFF0F4FF)
              ),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text(
                text = if (isPosesExpanded) "收起姿态 ▲" else "姿态动作 ▼",
                color = if (isPosesExpanded) Color.White else VoxBlue,
                fontSize = 12.sp,
              )
            }
            Button(
              onClick = { bridgeController?.setExpression("happy") },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F4FF)),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text(stringResource(com.example.voxmate.R.string.avatar_action_happy), color = VoxBlue, fontSize = 12.sp)
            }
            Button(
              onClick = {
                speechOutput.stop()
                bridgeController?.speak(0f)
                bridgeController?.resetExpression()
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F7FB)),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text(stringResource(com.example.voxmate.R.string.avatar_action_reset), color = Color.Gray, fontSize = 12.sp)
            }
          }
        }
      }
    }

    // 可折叠 25 动作姿态面板
    AnimatedVisibility(
      visible = isPosesExpanded,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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

          for (row in 0 until 5) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
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
                  modifier = Modifier.weight(1f).aspectRatio(1.3f),
                  shape = RoundedCornerShape(10.dp),
                  color = if (isSelected) VoxBlue else Color(0xFFF1F5F9),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = "$poseId",
                      fontSize = 13.sp,
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

    Spacer(Modifier.height(14.dp))

    // 对话气泡历史流
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (messages.isEmpty()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
        ) {
          Text(
            text = stringResource(com.example.voxmate.R.string.chat_empty_prompt),
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp,
            color = Color(0xFF64748B),
          )
        }
      } else {
        messages.forEach { msg ->
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
              color = if (msg.isUser) VoxBlue else Color.White,
              shadowElevation = if (msg.isUser) 1.dp else 2.dp,
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

    Spacer(Modifier.height(18.dp))

    // 底部交互控制台（语音麦克风大按钮 / 文字键盘双模）
    if (isTextMode) {
      // 文字键盘输入模式
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          onClick = { isTextMode = false },
          shape = CircleShape,
          color = Color.White,
          shadowElevation = 2.dp,
          modifier = Modifier.size(48.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text("🎤", fontSize = 20.sp)
          }
        }

        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = { Text(stringResource(com.example.voxmate.R.string.chat_input_hint), fontSize = 13.sp) },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(20.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { handleSendMessage(inputText) }),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VoxBlue,
            unfocusedBorderColor = Color(0xFFCBD5E1),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
          ),
        )

        Button(
          onClick = { handleSendMessage(inputText) },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = VoxBlue),
          modifier = Modifier.size(48.dp),
          contentPadding = PaddingValues(0.dp),
        ) {
          Text("➤", color = Color.White, fontSize = 18.sp)
        }
      }
    } else {
      // 语音交互模式（中心大麦克风）
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
      ) {
        // 键盘输入切换按钮
        Surface(
          onClick = { isTextMode = true },
          shape = CircleShape,
          color = Color.White,
          shadowElevation = 2.dp,
          modifier = Modifier.size(50.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text("⌨️", fontSize = 22.sp)
          }
        }

        // 中心大号语音按键
        Button(
          onClick = {
            if (isListening) {
              speechInput.stopListening()
              isListening = false
            } else if (isSpeaking) {
              speechOutput.stop()
              isSpeaking = false
            } else {
              isListening = true
              speechInput.startListening()
            }
          },
          modifier = Modifier.height(58.dp).weight(1f).padding(horizontal = 14.dp),
          shape = RoundedCornerShape(24.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = when {
              isListening -> Color(0xFFE53935)
              isSpeaking -> Color(0xFFE59B2F)
              else -> ReadyGreen
            }
          ),
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
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
        }

        // 姿态快捷切换按钮
        Surface(
          onClick = { isPosesExpanded = !isPosesExpanded },
          shape = CircleShape,
          color = if (isPosesExpanded) VoxBlue else Color.White,
          shadowElevation = 2.dp,
          modifier = Modifier.size(50.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(if (isPosesExpanded) "▲" else "💃", fontSize = 20.sp)
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))
  }
}

@Preview
@Composable
private fun VoxMateAppPreview() {
  VoxMateApp()
}
