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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    Spacer(Modifier.height(48.dp))
    Text(
      stringResource(com.example.voxmate.R.string.ready_to_chat),
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      stringResource(com.example.voxmate.R.string.on_device_privacy_message),
      modifier = Modifier.padding(top = 10.dp),
      color = Color(0xFF687086),
    )

    Spacer(Modifier.height(32.dp))
    StatusCard(
      stringResource(com.example.voxmate.R.string.ai_chat_core),
      stringResource(com.example.voxmate.R.string.status_connected),
      true,
    )
    Spacer(Modifier.height(12.dp))
    StatusCard(
      stringResource(com.example.voxmate.R.string.model_download),
      stringResource(com.example.voxmate.R.string.status_connected),
      true,
    )
    Spacer(Modifier.height(12.dp))
    StatusCard(
      stringResource(com.example.voxmate.R.string.speech_recognition_and_playback),
      stringResource(com.example.voxmate.R.string.status_next_step),
      false,
    )
    if (selectedModelName != null) {
      Spacer(Modifier.height(12.dp))
      StatusCard(stringResource(com.example.voxmate.R.string.current_model), selectedModelName, true)
    }

    Spacer(Modifier.weight(1f))
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
