package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType

//todo что делать если пришло несколько сообщений и игрок уже присоединился
// но ему не дошла информация
class JoinMsg(
  val player: d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType,
  val playerName: String,
  val gameName: String,
  val nodeRole: NodeRole,
) : d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.Msg(MessageType.JoinMsg) {
}