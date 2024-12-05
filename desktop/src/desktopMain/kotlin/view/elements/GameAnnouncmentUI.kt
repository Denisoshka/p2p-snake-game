package d.zhdanov.ccfit.nsu.view.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import d.zhdanov.ccfit.nsu.controllers.dto.AnnouncementInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig

@Composable
fun AnnouncementItem(
  announcement: AnnouncementInfo, onStartGame: (GameConfig) -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth().padding(8.dp), elevation = 4.dp) {
    Column(modifier = Modifier.padding(8.dp)) {
      Text(
        "Game: ${announcement.msg.gameName}",
        style = MaterialTheme.typography.h6
      )
      Text("Players: ${announcement.msg.players.joinToString { it.name }}")
      Text("Config: ${announcement.msg.gameConfig}")
      Text("Can Join: ${announcement.msg.canJoin}")

      Button(
        onClick = { onStartGame(announcement.msg.gameConfig) },
        modifier = Modifier.padding(top = 8.dp)
      ) {
        Text("Start Game")
      }
    }
  }
}