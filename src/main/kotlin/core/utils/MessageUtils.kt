package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT

object MessageUtils : MessageUtilsT<SnakesProto.GameMessage,
    SnakesProto.GameMessage.TypeCase> {
  private val ackMsg: SnakesProto.GameMessage.AckMsg =
    SnakesProto.GameMessage.AckMsg.newBuilder().build()
  private val pingMsg: SnakesProto.GameMessage.PingMsg =
    SnakesProto.GameMessage.PingMsg.newBuilder().build()

  override fun needToAcknowledge(
    msgDescriptor: SnakesProto.GameMessage
    .TypeCase
  ): Boolean {
    return msgDescriptor == SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT
        || msgDescriptor == SnakesProto.GameMessage.TypeCase.ACK
        || msgDescriptor == SnakesProto.GameMessage.TypeCase.DISCOVER
  }

  override fun getMSGSeq(message: SnakesProto.GameMessage): Long {
    return message.msgSeq
  }

  override fun getAckMsg(
    message: SnakesProto.GameMessage,
    senderId: Int, receiverId: Int
  ): SnakesProto.GameMessage {
    return SnakesProto.GameMessage.newBuilder()
      .setMsgSeq(message.msgSeq)
      .setSenderId(senderId)
      .setReceiverId(receiverId)
      .setAck(
        ackMsg
      ).build()
  }

  override fun getErrorMsg(
    message: SnakesProto.GameMessage,
    errorMsg: String?
  ): SnakesProto.GameMessage {
    return SnakesProto.GameMessage.newBuilder()
      .setMsgSeq(message.msgSeq)
      .setError(
        SnakesProto.GameMessage.ErrorMsg.newBuilder()
          .setErrorMessage(errorMsg)
          .build()
      ).build()
  }

  override fun getPingMsg(seq: Long): SnakesProto.GameMessage {
    return SnakesProto.GameMessage.newBuilder()
      .setMsgSeq(seq)
      .setPing(pingMsg)
      .build()
  }

  override fun getSenderId(message: SnakesProto.GameMessage): Int {
    return message.senderId
  }

  override fun getReceiverId(message: SnakesProto.GameMessage): Int {
    return message.receiverId
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