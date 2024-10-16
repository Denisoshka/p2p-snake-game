package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.core.network.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.network.messages.MessageType

object MessageUtils {
  fun needToApprove(message: GameMessage): Boolean {
    val msgType = message.messageType
    return msgType == MessageType.AnnouncementMsg
        || msgType == MessageType.AckMsg
        || msgType == MessageType.DiscoverMsg
  }

  fun getData(message: GameMessage): ByteArray {
    return MessageTranslator.toProto(message).toByteArray()
  }

  fun toByte(message: GameMessage): ByteArray {

  }
}