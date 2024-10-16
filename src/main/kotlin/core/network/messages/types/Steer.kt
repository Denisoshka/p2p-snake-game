package d.zhdanov.ccfit.nsu.core.network.messages.types

import d.zhdanov.ccfit.nsu.core.network.messages.Direction
import d.zhdanov.ccfit.nsu.core.network.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.network.messages.MessageType

class Steer(private val direction: Direction, msgSeq: Long) :
  GameMessage(msgSeq, MessageType.SteerMsg) {
}
