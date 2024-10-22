package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.messages.PlayerType

//todo что делать если пришло несколько сообщений и игрок уже присоединился
// но ему не дошла информация
class JoinMsg(
  val player: PlayerType,
  val playerName: String,
  val gameName: String,
  val nodeRole: NodeRole,
) : Msg(MessageType.JoinMsg) {
}