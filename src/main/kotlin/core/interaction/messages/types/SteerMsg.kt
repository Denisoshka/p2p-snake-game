package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType

class SteerMsg(val direction: Direction, msgSeq: Long) :
  GameMessage(msgSeq, MessageType.SteerMsg) {
}
