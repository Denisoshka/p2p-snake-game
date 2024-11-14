package d.zhdanov.ccfit.nsu.core.network.interfaces

interface MessageUtilsT<MessageT, MessageDescriptor> {
	fun needToAcknowledge(msgDescriptor: MessageDescriptor): Boolean
	fun checkJoinPreconditions(message: MessageT): Boolean
	fun getMSGSeq(message: MessageT): Long

	fun getAckMsg(msgSeq: MessageT, senderId: Int, receiverId: Int): MessageT
	fun getAckMsg(msgSeq: Long, senderId: Int, receiverId: Int): MessageT
	fun newErrorMsg(message: MessageT, errorMsg: String?): MessageT
	fun getPingMsg(seq: Long): MessageT

	fun getSenderId(message: MessageT): Int
	fun getReceiverId(message: MessageT): Int

	fun fromBytes(bytes: ByteArray): MessageT
	fun toBytes(message: MessageT): ByteArray

	fun getComparator(): Comparator<MessageT>
}