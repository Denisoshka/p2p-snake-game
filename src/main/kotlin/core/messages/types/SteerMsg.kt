package d.zhdanov.ccfit.nsu.core.messages.types

import d.zhdanov.ccfit.nsu.core.messages.Direction
import d.zhdanov.ccfit.nsu.core.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.messages.MessageType

class SteerMsg(private val direction: Direction, msgSeq: Long) :
  GameMessage(msgSeq, MessageType.SteerMsg) {
}
