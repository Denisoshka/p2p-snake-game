package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.core.network.AbstractMessageUtils
import dzhdanov.ccfit.nsu.ru.SnakesProto

object MessageUtils : AbstractMessageUtils<SnakesProto.GameMessage,
    SnakesProto.GameMessage.TypeCase> {
  override fun needToApprove(msgDescriptor: SnakesProto.GameMessage.TypeCase):
      Boolean {
    return msgDescriptor == SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT
        || msgDescriptor == SnakesProto.GameMessage.TypeCase.ACK
        || msgDescriptor == SnakesProto.GameMessage.TypeCase.DISCOVER
  }

  override fun fromBytes(bytes: ByteArray): SnakesProto.GameMessage {
    return SnakesProto.GameMessage.parseFrom(bytes);
  }

  override fun getComparator(): Comparator<SnakesProto.GameMessage> {
    return Comparator { msg1, msg2 ->
      msg1.msgSeq.compareTo(msg2.msgSeq)
    }
  }

  override fun toBytes(message: SnakesProto.GameMessage): ByteArray {
    return message.toByteArray();
  }
}