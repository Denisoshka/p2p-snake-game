package d.zhdanov.ccfit.nsu.core.network.messages.types

import d.zhdanov.ccfit.nsu.core.network.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.network.messages.MessageType

class Ping(msgSeq: Long) : GameMessage(msgSeq, MessageType.PingMsg) {
}
