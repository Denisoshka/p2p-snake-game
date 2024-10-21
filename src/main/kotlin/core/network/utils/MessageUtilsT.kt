package d.zhdanov.ccfit.nsu.core.network.utils

interface MessageUtilsT<MessageT, MessageDescriptor> {
  fun needToAcknowledge(msgDescriptor: MessageDescriptor): Boolean
  fun getMSGSeq(message: MessageT): Long

  fun getAckMsg(message: MessageT, senderId: Int, receiverId: Int): MessageT
  fun getErrorMsg(message: MessageT, errorMsg: String?): MessageT
  fun getPingMsg(seq: Long): MessageT

  fun getSenderId(message: MessageT): Int
  fun getReceiverId(message: MessageT): Int

  fun fromBytes(bytes: ByteArray): MessageT
  fun toBytes(message: MessageT): ByteArray

  fun getComparator(): Comparator<MessageT>
}