package core.network.core

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNetworkStateIsNull
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT.NodeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}
private const val NodeAlreadyRegisteredMsg =
  "node already registered in cluster"
private const val IncorrectRegisterEvent =
  "registration channel receive wrong event: "

/**
 * todo fix doc
 */
class Node(
  messageComparator: Comparator<GameMessage>,
  override val id: Int,
  override val ipAddress: InetSocketAddress,
  @Volatile override var nodeRole: NodeRole,
  @Volatile override var payload: NodePayloadT? = null,
  private val nodesHandler: NodesHandler,
) : NodeT {
  override val nodeState: NodeT.NodeState
    get() = stateHolder.get()

  private val resendDelay = nodesHandler.resendDelay
  private val thresholdDelay = nodesHandler.thresholdDelay

  @Volatile private var lastReceive = System.currentTimeMillis()
  @Volatile private var lastSend = System.currentTimeMillis()
  @Volatile private var observeJob: Job? = null
  private val stateHolder = AtomicReference(
    if(nodeRole == NodeRole.VIEWER) NodeT.NodeState.Passive
    else NodeT.NodeState.Active
  )

  /**
   * Use this valuee within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<GameMessage, Long> = TreeMap(
    messageComparator
  )

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = getNextMSGSeqNum()

    val ping = nodesHandler.msgUtils.getPingMsg(seq)
    nodesHandler.sendUnicast(ping, ipAddress)
    lastSend = System.currentTimeMillis()
  }

  fun ackMessage(message: GameMessage): GameMessage? {
    synchronized(msgForAcknowledge) {
      lastReceive = System.currentTimeMillis()
      msgForAcknowledge.remove(message) ?: return null
      return message
    }
  }

  fun addMessageForAck(message: GameMessage): Boolean {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] = System.currentTimeMillis()
      lastSend = System.currentTimeMillis()
    }
    return true
  }

  fun addAllMessageForAck(messages: List<GameMessage>) {
    synchronized(msgForAcknowledge) {
      for(msg in messages) {
        msgForAcknowledge[msg] = System.currentTimeMillis()
      }
      lastSend = System.currentTimeMillis()
    }
  }

  fun getNextMSGSeqNum(): Long {
    return nodesHandler.nextSeqNum
  }

  private fun checkMessages(): Long {
    var ret = resendDelay
    val now = System.currentTimeMillis()
    val it = msgForAcknowledge.iterator()
    while(it.hasNext()) {
      val entry = it.next()
      val (msg, time) = entry
      if(now - time < thresholdDelay) {
        ret = ret.coerceAtMost(thresholdDelay + time - now)
        entry.setValue(now)
        nodesHandler.sendUnicast(msg, ipAddress)
      } else {
        it.remove()
      }
    }
    return ret
  }

  private fun changeState(newState: NodeT.NodeState): Boolean {
    do {
      val prevState = stateHolder.get()
      if(newState <= prevState) return false;
    } while(stateHolder.compareAndSet(prevState, newState))
    return true
  }

  fun handleEvent(event: NodeEvent) {
    when(event) {
      NodeEvent.ShutdownFromCluster, NodeEvent.ShutdownNowFromCluster -> {
        changeState(NodeT.NodeState.Disconnected)
      }

      NodeEvent.ShutdownFromUser                                      -> {
        changeState(NodeT.NodeState.Disconnected)
      }

      else                                                            -> {}
    }
  }

  fun CoroutineScope.startObservation(
    node: Node
  ) = launch {
    var nextDelay = 0L
    var detachedFromCluster = false
    while(isActive) {
      delay(nextDelay)
      when(stateHolder.get()) {
        NodeT.NodeState.Active, NodeT.NodeState.Passive -> {
          nextDelay = onProcessing()
        }

        NodeT.NodeState.Disconnected                    -> {
          if(!detachedFromCluster) {
            detachedFromCluster = true
            node.payload?.onContextObserverTerminated()
            node.payload = null
            node.nodesHandler.handleNodeDetachPrepare(node)
          }
          nextDelay = onDetaching()
        }

        NodeT.NodeState.Terminated                      -> {
          node.payload?.onContextObserverTerminated()
          node.payload = null
          TODO("make node detach")
        }

        else                                            -> {
          logger.error { "state machine state is null" }
          throw IllegalNetworkStateIsNull()
        }
      }
    }
  }

  fun getUnacknowledgedMessages(
    node: Node
  ): List<GameMessage> {
    if(stateHolder.get() > NodeT.NodeState.Disconnected) {
      throw IllegalUnacknowledgedMessagesGetAttempt()
    }
    return synchronized(node.msgForAcknowledge) {
      node.msgForAcknowledge.keys.toList();
    }
  }

  private fun checkNodeConditions(
    now: Long
  ): Long {
    if(now - lastReceive > thresholdDelay) {
      stateHolder.set(NodeT.NodeState.Terminated)
      return 0
    }
    return checkMessages()
  }

  private fun onProcessing(
  ): Long {
    synchronized(msgForAcknowledge) {
      if(!running) return 0

      val now = System.currentTimeMillis()
      val nextDelay = checkNodeConditions(now)
      sendPingIfNecessary(nextDelay, now)

      return nextDelay
    }
  }

  private fun onDetaching(
  ): Long {
    synchronized(msgForAcknowledge) {
      if(stateHolder.get() != NodeT.NodeState.Disconnected) return 0
      val ret = checkNodeConditions(System.currentTimeMillis())
      if(msgForAcknowledge.isEmpty()) {
        stateHolder.set(NodeT.NodeState.Terminated)
        return 0
      }
      return ret
    }
  }
}