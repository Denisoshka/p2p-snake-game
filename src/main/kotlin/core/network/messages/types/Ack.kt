package d.zhdanov.ccfit.nsu.core.network.messages.types

import d.zhdanov.ccfit.nsu.core.network.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.network.messages.MessageType

class Ack(msgSeq: Long) : GameMessage(msgSeq, MessageType.AckMsg) {
}
