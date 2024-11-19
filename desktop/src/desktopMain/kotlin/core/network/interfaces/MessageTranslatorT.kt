package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType

interface MessageTranslatorT<MessageT> {
  fun getMessageType(message: MessageT): MessageType

  fun fromProto(msg: MessageT): P2PMessage
  fun fromProto(message: MessageT, msgT: MessageType): P2PMessage

  fun toMessageT(msg: P2PMessage): MessageT
  fun toMessageT(msg: P2PMessage, msgT: MessageType): MessageT
}