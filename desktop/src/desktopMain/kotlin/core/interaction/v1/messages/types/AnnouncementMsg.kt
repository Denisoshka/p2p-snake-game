package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType

class AnnouncementMsg(
  players: List<GamePlayer>,
  gameConfig: GameConfig,
  canJoin: Boolean,
  gameName: String
) : Msg(MessageType.AnnouncementMsg)