package d.zhdanov.ccfit.nsu.controllers

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg

sealed class Screen {
  data object Lobby : Screen()
  data class Game(
    val gameConfig: GameConfig, val gameAnnouncement: AnnouncementMsg?
  ) : Screen()
}