package d.zhdanov.ccfit.nsu.core.messages.types

import d.zhdanov.ccfit.nsu.core.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.messages.MessageType

class AckMsg(msgSeq: Long) : GameMessage(msgSeq, MessageType.AckMsg) {
}
