package core.network.core

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalStateMachineStateIsNull
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT.NodeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicLong
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
  initMsgSeqNum: Long,
  messageComparator: Comparator<GameMessage>,
  @Volatile override var nodeRole: NodeRole,
  private val observerScope: CoroutineScope,
  override val id: Int,
  override val ipAddress: InetSocketAddress,
  private val nodesHandler: NodesHandler,
  var requireToRegisterInContext: Boolean = true,
) : NodeT {
  @Volatile var payloadT: NodePayloadT? = null

  val resendDelay = nodesHandler.resendDelay
  val thresholdDelay = nodesHandler.thresholdDelay

  @Volatile private var lastReceive = System.currentTimeMillis()
  @Volatile private var lastSend = System.currentTimeMillis()
  private val msgSeqNum = AtomicLong(initMsgSeqNum)
  @Volatile private var observeJob: Job? = null
  private val stateHolder = AtomicReference(
    if(nodeRole == NodeRole.VIEWER) NodeT.NodeState.Passive
    else NodeT.NodeState.Active
  )
  val state: NodeT.NodeState
    get() = stateHolder.get()
  val running: Boolean
    get() {
      val ret = stateHolder.get()
      return ret == NodeT.NodeState.Active || ret == NodeT.NodeState.Passive
    }

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
    return msgSeqNum.incrementAndGet()
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
    while(isActive) {
      var detachedFromCluster: Boolean = false
      when(stateHolder.get()) {
        NodeT.NodeState.Active, NodeT.NodeState.Passive -> {

        }

        NodeT.NodeState.Disconnected                    -> {
          if(!detachedFromCluster) {
            detachedFromCluster = true
            node.payloadT?.onContextObserverTerminated()
            node.payloadT = null
            node.nodesHandler.handleNodeDetachPrepare(node)
          }
        }

        NodeT.NodeState.Terminated                      -> {
          node.payloadT?.onContextObserverTerminated()
          node.payloadT = null
        }

        else                                            -> {
          logger.error { "state machine state is null" }
          throw IllegalStateMachineStateIsNull()
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
    now: Long, node: Node
  ): Long {
    if(now - node.lastReceive > node.thresholdDelay) {
      stateHolder.set(NodeT.NodeState.Terminated)
      return 0
    }
    val nextDelay = node.checkMessages()
    return nextDelay
  }

  private fun onProcessing(
    node: Node
  ): Long {
    synchronized(node.msgForAcknowledge) {
      if(!running) return 0

      val now = System.currentTimeMillis()
      if(now - node.lastReceive > node.thresholdDelay) {
        stateHolder.set(NodeT.NodeState.Terminated)
        return 0
      }

      val nextDelay = node.checkMessages()
      node.sendPingIfNecessary(nextDelay, now)

      return nextDelay
    }
  }

  private fun onFinalizingProcess(
    node: Node
  ): Long {
    synchronized(node.msgForAcknowledge) {
      if(stateHolder.get() != NodeT.NodeState.Disconnected) return 0
      val now = System.currentTimeMillis()
      val ret = checkNodeConditions(now, node)
      if(node.msgForAcknowledge.isEmpty()) {
        stateHolder.set(NodeT.NodeState.Terminated)
        return 0
      }
      return ret;
    }
  }
}