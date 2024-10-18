package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType

class PingMsg(msgSeq: Long) : GameMessage(msgSeq, MessageType.PingMsg) {
}
