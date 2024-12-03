package d.zhdanov.ccfit.nsu.view.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg

@Composable
fun GameAnnouncementUI(announcement: AnnouncementMsg) {
  Column {
    Column {
      Text(
        "Игра: ${announcement.gameName}",
        style = MaterialTheme.typography.h6
      )

      if(announcement.canJoin) {
        Text("possible to join")
      } else {
        Text("not possible to join")
      }

      // Настройки из GameConfig
      Text("Game field width: ${announcement.gameConfig.width}")
      Text("Game field height: ${announcement.gameConfig.height}")
      Text("Static food quantity: ${announcement.gameConfig.foodStatic}")
      Text("State delay: ${announcement.gameConfig.stateDelayMs} ms")
    }
  }
}
