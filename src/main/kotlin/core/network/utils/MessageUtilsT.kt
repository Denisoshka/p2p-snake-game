package d.zhdanov.ccfit.nsu.core.network.utils

interface MessageUtilsT<MessageT, MessageDescriptor> {
  fun needToApprove(msgDescriptor: MessageDescriptor): Boolean
  fun fromBytes(bytes: ByteArray): MessageT
  fun toBytes(message: MessageT): ByteArray
  fun getComparator() : Comparator<MessageT>
}