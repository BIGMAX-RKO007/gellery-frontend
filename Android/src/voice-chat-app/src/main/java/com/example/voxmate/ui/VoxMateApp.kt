package com.example.voxmate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.voxmate.bridge.VrmBridgeController
import com.example.voxmate.ui.avatar.VrmAvatarView
import com.example.voxmate.voice.AndroidSpeechOutput
import com.example.voxmate.voice.EmotionParser
import com.example.voxmate.voice.LipSyncDriver
import com.example.voxmate.voice.SpeechOutputEvent
import com.google.ai.edge.gallery.modelmanagerui.ModelManagerRoute
import com.google.ai.edge.gallery.modelmanagerui.SelectedModel

private val VoxBlue = Color(0xFF2557D6)
private val PageBackground = Color(0xFFF5F7FB)
private val ReadyGreen = Color(0xFF218A55)

@Composable
fun VoxMateApp() {
  MaterialTheme {
    val navController = rememberNavController()
    var selectedModel by remember { mutableStateOf<SelectedModel?>(null) }
    NavHost(navController = navController, startDestination = "home") {
      composable("home") {
        Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
          VoiceChatHome(
            selectedModelName = selectedModel?.artifact?.displayName,
            onConfigureModels = { navController.navigate("models") },
          )
        }
      }
      composable("models") {
        ModelManagerRoute(
          onClose = { navController.navigateUp() },
          onModelSelected = { model ->
            selectedModel = model
            navController.navigateUp()
          },
        )
      }
    }
  }
}

@Composable
private fun VoiceChatHome(selectedModelName: String?, onConfigureModels: () -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier.size(44.dp).background(VoxBlue, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          stringResource(com.example.voxmate.R.string.brand_initial),
          color = Color.White,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
        )
      }
      Column(modifier = Modifier.padding(start = 12.dp)) {
        Text(
          stringResource(com.example.voxmate.R.string.app_name),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(stringResource(com.example.voxmate.R.string.app_tagline), color = Color(0xFF687086))
      }
    }

    Spacer(Modifier.height(16.dp))

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bridgeController by remember { mutableStateOf<VrmBridgeController?>(null) }
    var isAvatarReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf("upper") }
    var selectedPose by remember { mutableStateOf(0) }

    val speechOutput = remember { AndroidSpeechOutput(context, coroutineScope) }
    val lipSyncDriver = remember {
      LipSyncDriver(bridgeControllerProvider = { bridgeController }, coroutineScope = coroutineScope).apply {
        attachSpeechOutput(speechOutput)
      }
    }

    LaunchedEffect(speechOutput) {
      speechOutput.events.collect { event ->
        when (event) {
          is SpeechOutputEvent.Started -> isSpeaking = true
          is SpeechOutputEvent.Completed, is SpeechOutputEvent.Error -> isSpeaking = false
        }
      }
    }

    DisposableEffect(speechOutput) {
      onDispose {
        lipSyncDriver.stopLipSync()
        speechOutput.release()
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth().height(360.dp),
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

        // 数字人状态标签
        Surface(
          modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
          shape = RoundedCornerShape(12.dp),
          color = Color(0xCCF5F7FB),
        ) {
          Text(
            text = if (isSpeaking) stringResource(com.example.voxmate.R.string.voice_demo_speaking)
            else if (isAvatarReady) stringResource(com.example.voxmate.R.string.avatar_title)
            else stringResource(com.example.voxmate.R.string.avatar_loading),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = if (isSpeaking) ReadyGreen else if (isAvatarReady) VoxBlue else Color.Gray,
            fontWeight = FontWeight.Medium,
          )
        }

        // 摄像机视角切换栏（右上角：半身 / 全身 / 特写）
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

        // 原生交互快捷测试栏（微调表情与复位）
        if (isAvatarReady && !isSpeaking) {
          Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                bridgeController?.setExpression("happy")
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F4FF)),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text(stringResource(com.example.voxmate.R.string.avatar_action_happy), color = VoxBlue, fontSize = 12.sp)
            }
            Button(
              onClick = {
                bridgeController?.speak(0f)
                bridgeController?.resetExpression()
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F7FB)),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text(stringResource(com.example.voxmate.R.string.avatar_action_reset), color = Color.Gray, fontSize = 12.sp)
            }
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    // 智能语音发声与自动唇形同步按钮
    if (isAvatarReady) {
      Button(
        onClick = {
          if (isSpeaking) {
            speechOutput.stop()
            bridgeController?.resetExpression()
          } else {
            val rawGreeting = context.getString(com.example.voxmate.R.string.voice_demo_greeting)
            val parsed = EmotionParser.parse(rawGreeting)
            parsed.expression?.let { bridgeController?.setExpression(it) }
            speechOutput.speak(parsed.cleanText)
          }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isSpeaking) Color(0xFFE53935) else ReadyGreen
        ),
      ) {
        Text(
          text = if (isSpeaking) stringResource(com.example.voxmate.R.string.voice_demo_stop)
          else stringResource(com.example.voxmate.R.string.voice_demo_btn),
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Spacer(Modifier.height(12.dp))
    }

    StatusCard(
      stringResource(com.example.voxmate.R.string.ai_chat_core),
      stringResource(com.example.voxmate.R.string.status_connected),
      true,
    )
    if (selectedModelName != null) {
      Spacer(Modifier.height(8.dp))
      StatusCard(stringResource(com.example.voxmate.R.string.current_model), selectedModelName, true)
    }

    Spacer(Modifier.height(14.dp))

    // 25 个动作姿态控制面板 (5x5 矩阵)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(com.example.voxmate.R.string.avatar_poses_title),
            fontSize = 14.sp,
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

        Spacer(Modifier.height(8.dp))

        // 5 列 x 5 行动作编号网格 (1 ~ 25)
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
                modifier = Modifier.weight(1f).aspectRatio(1.25f),
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

    Spacer(Modifier.height(16.dp))
    Button(
      onClick = onConfigureModels,
      modifier = Modifier.fillMaxWidth().height(58.dp),
      shape = RoundedCornerShape(18.dp),
      colors = ButtonDefaults.buttonColors(containerColor = VoxBlue),
    ) {
      Text(
        stringResource(
          if (selectedModelName == null) com.example.voxmate.R.string.select_model_and_configure
          else com.example.voxmate.R.string.change_model
        ),
        fontSize = 16.sp,
      )
    }
  }
}

@Composable
private fun StatusCard(title: String, status: String, ready: Boolean) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(title, fontWeight = FontWeight.Medium)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(8.dp)
            .background(if (ready) ReadyGreen else Color(0xFFE59B2F), CircleShape)
        )
        Text(
          status,
          modifier = Modifier.padding(start = 8.dp),
          color = if (ready) ReadyGreen else Color(0xFF9B6415),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun VoxMatePreview() {
  VoxMateApp()
}
