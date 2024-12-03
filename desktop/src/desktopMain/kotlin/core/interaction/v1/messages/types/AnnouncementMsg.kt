package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType

data class AnnouncementMsg(
  val players: List<GamePlayer>,
  val gameConfig: GameConfig,
  val canJoin: Boolean,
  val gameName: String
) : Msg(MessageType.AnnouncementMsg)