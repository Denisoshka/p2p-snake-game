package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType

class AckMsg(msgSeq: Long) : GameMessage(msgSeq, MessageType.AckMsg) {
}
