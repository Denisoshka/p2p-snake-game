package d.zhdanov.ccfit.nsu.core.messages.types

import d.zhdanov.ccfit.nsu.core.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.messages.MessageType

class PingMsg(msgSeq: Long) : GameMessage(msgSeq, MessageType.PingMsg) {
}
