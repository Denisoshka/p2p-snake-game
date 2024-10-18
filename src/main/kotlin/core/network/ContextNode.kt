package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

class ContextNode(
  val id: Int,
  private val pingDelay: Long,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val context: P2PContext,
  nodeStateCheckerContext: CoroutineContext = Dispatchers.IO
//  private val checkNodeManager: ((() -> Unit) -> Unit)? = null
) : AutoCloseable {
  enum class NodeState {
    Alive,
    Dead,
  }

  private val messagesForApprove: TreeMap<GameMessage, Long> = TreeMap()

  @Volatile
  private var nodeState: NodeState = NodeState.Alive
  private val observationJob: Job

  init {
    observationJob = CoroutineScope(nodeStateCheckerContext).launch {
      val interval = (resendDelay * 0.75).toLong();
      while (nodeState == NodeState.Alive) {
        checkNodeState()
        delay(interval)
      }
    }
  }

  /**
   * Change this value within the scope of `synchronized(messagesForApprove)`.
   */
  private val lastAction = AtomicLong(System.currentTimeMillis())
  fun approveMessage(message: GameMessage) {
    synchronized(messagesForApprove) {
      messagesForApprove.remove(message)
      lastAction.set(System.currentTimeMillis())
    }
  }

  fun addMessageForApproval(message: GameMessage) {
    synchronized(messagesForApprove) {
      messagesForApprove.put(message, System.currentTimeMillis())
    }
  }

  private fun checkNodeState() {
    synchronized(messagesForApprove) {
      val curTime = System.currentTimeMillis()
      val entry = messagesForApprove.firstEntry()
      if (entry == null) {
        if (curTime - lastAction.get() > pingDelay) {
          TODO("нужно сделать пинг")
        }
        return
      }
      if (curTime.compareTo(entry.value) < resendDelay) {
        return
      } else if (curTime.compareTo(entry.value) < thresholdDelay) {
        TODO("нужно переслать сообщение")
      } else {
        if (nodeState != NodeState.Dead) {
          nodeState = NodeState.Dead
          context.onNodeDead(this)
        }
      }
    }
  }

  fun startObserving() {

  }

  override fun close() {
    TODO("Not yet implemented")
  }
}