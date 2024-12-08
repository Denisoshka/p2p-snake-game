package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType

interface MessageTranslatorT<MessageT> {
  fun getMessageType(message: MessageT): MessageType

  fun fromProto(msg: MessageT): GameMessage
  fun fromProto(message: MessageT, msgT: MessageType): GameMessage

  fun toMessageT(msg: GameMessage): MessageT
  fun toMessageT(msg: GameMessage, msgT: MessageType): MessageT
}