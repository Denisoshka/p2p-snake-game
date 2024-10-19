package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType

interface AbstractMessageTranslator<MessageT> {
  fun getMessageType(message: MessageT): MessageType
  fun fromMessageT(message: MessageT): MessageType
  fun toMessageT(message: MessageType): MessageT
}