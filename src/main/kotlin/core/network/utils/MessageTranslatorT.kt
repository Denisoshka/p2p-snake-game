package d.zhdanov.ccfit.nsu.core.network.utils

import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType

interface MessageTranslatorT<MessageT> {
  fun getMessageType(message: MessageT): MessageType

  fun fromMessageT(message: MessageT): GameMessage
  fun fromMessageT(message: MessageT, msgT: MessageType): GameMessage

  fun toMessageT(msg: GameMessage): MessageT
  fun toMessageT(msg: GameMessage, msgT: MessageType): MessageT
}