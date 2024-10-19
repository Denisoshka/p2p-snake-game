package d.zhdanov.ccfit.nsu.core.interaction.messages

/**
 * GameMessage class represents a message in the game system.
 *
 * ## Contract:
 * - The [msgSeq] field represents the sequence number of the message and must **strictly increase** with each new message.
 * - Objects of this class and its subclasses should only be compared based on the [msgSeq] field.
 * - Subclasses must not override the [equals] and [hashCode] methods.
 *
 * This contract ensures consistent comparison behavior and correct message ordering in the system.
 */
open class GameMessage(val msgSeq: Long, val messageType: MessageType) :
  Comparable<GameMessage> {
  val senderId = 0
  val receiverId = 0

  final override fun compareTo(other: GameMessage): Int {
    return this.msgSeq.compareTo(other.msgSeq)
  }

  final override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GameMessage

    return msgSeq == other.msgSeq
  }

  final override fun hashCode(): Int {
    return msgSeq.hashCode()
  }
}
