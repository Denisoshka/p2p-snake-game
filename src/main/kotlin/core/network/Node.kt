package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.utils.AbstractMessageTranslator
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * ContextNode class represents a node in a peer-to-peer context.
 *
 * @param address The unique identifier for the node, represented as an InetSocketAddress.
 * @param nodeRole The role of the node within the network topology.
 * @param pingDelay Delay in milliseconds for pinging the node.
 * @param resendDelay Delay in milliseconds before resending a message.
 * @param thresholdDelay Threshold delay in milliseconds to determine node state.
 * @param context The P2P context which manages the node's interactions.
 * @param messageComparator Comparator for comparing messages of type [MessageT]. This comparator must compare messages based on the sequence number msg_seq, which is unique to the node within the context and monotonically increasing.
 * @param nodeStateCheckerContext Coroutine context for checking the node state. Default is [Dispatchers.IO].
 */
class Node<
    MessageT,
    InboundMessageTranslator : AbstractMessageTranslator<MessageT>
    >(
  initMsgSeqNum: Long,
  val address: InetSocketAddress,
  val nodeId: Int,
  var nodeRole: NodeRole,
  private val pingDelay: Long,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val context: P2PContext<MessageT, InboundMessageTranslator>,
  messageComparator: Comparator<MessageT>,
  nodeStateCheckerContext: CoroutineContext
) {
  enum class NodeState {
    JoiningCluster,
    Alive,
    Dead,
  }

  @Volatile
  private var nodeState: NodeState = NodeState.Alive
  private val messagesForApprove: TreeMap<MessageT, Long> =
    TreeMap(messageComparator)
  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
  private val observationJob: Job

  /**
   * Change this value within the scope of `synchronized(messagesForApprove)`.
   */
  private val lastAction = AtomicLong(System.currentTimeMillis())

  init {
    val delayCoef = 0.8
    observationJob = CoroutineScope(nodeStateCheckerContext).launch {
      try {
        while (nodeState == NodeState.Alive) {
          val delay = checkNodeState()
          delay((delay * delayCoef).toLong())
        }
      } catch (_: CancellationException) {
      }
//      TODO("посмотреть что написал Лёха по корутинам")
    }
  }

  fun approveMessage(message: MessageT) {
    synchronized(messagesForApprove) {
      messagesForApprove.remove(message)
      lastAction.set(System.currentTimeMillis())
    }
  }

  fun addMessageForAcknowledge(message: MessageT) {
    synchronized(messagesForApprove) {
      messagesForApprove[message] = System.currentTimeMillis()
      TODO("что то я сомневаюсь что здесь не нужно учитывать lastAction")
    }
  }

  private fun checkNodeState(): Long {
    synchronized(messagesForApprove) {
      val curTime = System.currentTimeMillis()
      if (messagesForApprove.isEmpty()) {
        if (curTime - lastAction.get() > pingDelay) {
          TODO("нужно сделать пинг")
        }
        return lastAction.get() + pingDelay - curTime
      }

      val entry = messagesForApprove.firstEntry()
      if (curTime - entry.value < resendDelay) {
        return entry.value + resendDelay - curTime
      }

      for ((msg, actionTime) in messagesForApprove) {
        if (curTime - actionTime < resendDelay) {
          break
        } else if (curTime - actionTime < thresholdDelay) {
          TODO("нужно переслать сообщение")
        } else {
          onNodeDead()
          return 0
        }
      }
      return entry.value + thresholdDelay - curTime
    }
    TODO("что то я сомневаюсь в корректности выбора времени для возвращения ")
    TODO(
      "что если пришло какое то сообщение от ноды но мы ведь смотрим " +
          "по подтвержденным сообщениям и не смотрим по времени последней " +
          "активности ноды, мб нужно удалять это сообщение из множества " +
          "для подтверждения и при переотправке по новой добавлять?"
    )
  }

  private fun onNodeDead() {
    nodeState = NodeState.Dead
    observationJob.cancel()
    context.onNodeDead(this)
  }
}