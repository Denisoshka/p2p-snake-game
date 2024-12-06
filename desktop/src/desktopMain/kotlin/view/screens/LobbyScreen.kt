package d.zhdanov.ccfit.nsu.view.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import d.zhdanov.ccfit.nsu.controllers.dto.AnnouncementInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg
import d.zhdanov.ccfit.nsu.view.elements.lobby.AnnouncementItem
import d.zhdanov.ccfit.nsu.view.elements.lobby.GameConfigDialog

@Composable
fun lobbyScreen(
  announcements: List<AnnouncementInfo>,
  onStartGame: (GameConfig, AnnouncementMsg?) -> Unit,
) {
  var showConfigDialog by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    Text(
      "Lobby",
      style = MaterialTheme.typography.h4,
      modifier = Modifier.padding(8.dp)
    )
    Button(
      onClick = { showConfigDialog = true },
      modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
      Text("Start My Game")
    }

    if (showConfigDialog) {
      GameConfigDialog(
        onDismiss = { showConfigDialog = false },
        onConfirm = { config ->
          showConfigDialog = false
          onStartGame(config, null)
        }
      )
    }

    LazyColumn {
      items(announcements) { announcement ->
        AnnouncementItem(announcement) { gameConfig ->
          onStartGame(gameConfig, announcement.msg)
        }
      }
    }
  }
}


