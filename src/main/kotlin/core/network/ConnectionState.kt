package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.messages.GameMessage
import java.util.concurrent.atomic.AtomicLong

class ConnectionState(initTime: Long) {
  private val messagesForApprove: MutableSet<GameMessage> = mutableSetOf()

  /**
   * Change this value within the scope of `synchronized(messagesForApprove)`.
   */
  private val lastUpdateTime: AtomicLong = AtomicLong(initTime)

  fun approveMessage(message: GameMessage) {
    synchronized(messagesForApprove) {
      messagesForApprove.remove(message);
      lastUpdateTime.set(System.currentTimeMillis())
    }
  }

  fun addMessageForApproval(message: GameMessage) {
    synchronized(messagesForApprove) {
      messagesForApprove.add(message)
      lastUpdateTime.set(System.currentTimeMillis())
    }
  }
}