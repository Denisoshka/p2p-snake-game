package d.zhdanov.ccfit.nsu.core.network.messages

open class GameMessage(val msgSeq: Long, val messageType: MessageType) {
  val senderId = 0
  val receiverId = 0
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GameMessage

    return msgSeq == other.msgSeq
  }

  override fun hashCode(): Int {
    return msgSeq.hashCode()
  }
}
