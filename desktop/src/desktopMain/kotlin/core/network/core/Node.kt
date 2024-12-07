package core.network.core

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNetworkStateIsNull
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
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
  nodeState: NodeT.NodeState,
  override val id: Int,
  override val ipAddress: InetSocketAddress,
  @Volatile override var payload: NodePayloadT? = null,
  private val nodesHandler: NodesHandler,
) : NodeT {
  override val running: Boolean
    get() = with(nodeState) {
      this == NodeT.NodeState.Active || this == NodeT.NodeState.Passive
    }

  override val nodeState: NodeT.NodeState
    get() = stateHolder.get()

  private val resendDelay = nodesHandler.resendDelay
  private val thresholdDelay = nodesHandler.thresholdDelay
  private val stateHolder = AtomicReference(
    if(nodeState != NodeT.NodeState.Active && nodeState != NodeT.NodeState.Passive) {
      throw IllegalNodeRegisterAttempt("illegal initial node state $nodeState")
    } else {
      nodeState
    }
  )

  @Volatile private var lastReceive = System.currentTimeMillis()
  @Volatile private var lastSend = System.currentTimeMillis()
  @Volatile private var observeJob: Job? = null

  /**
   * Use this valuee within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<GameMessage, Long> = TreeMap(
    messageComparator
  )

  override fun shutdown() {
    observeJob?.cancel()
  }

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = nodesHandler.nextSeqNum

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
      val prevState = nodeState
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

  override fun CoroutineScope.startObservation(): Job {
    this@Node.observeJob = launch {
      var nextDelay = 0L
      var detachedFromCluster = false
      try {
        while(isActive) {
          delay(nextDelay)
          when(nodeState) {
            NodeT.NodeState.Active, NodeT.NodeState.Passive -> {
              nextDelay = onProcessing()
            }

            NodeT.NodeState.Disconnected                    -> {
              if(!detachedFromCluster) {
                detachedFromCluster = true
                this@Node.payload?.onContextObserverTerminated()
                this@Node.payload = null
                this@Node.nodesHandler.handleNodeDetachPrepare(this@Node)
              }
              nextDelay = onDetaching()
            }

            NodeT.NodeState.Terminated                      -> {
              this@Node.payload?.onContextObserverTerminated()
              this@Node.payload = null
              TODO("make node detach")
            }
          }
        }
      } catch(e: CancellationException) {
        if(!detachedFromCluster) {
          this@Node.nodesHandler.handleNodeTermination(this@Node)
        }
        this.cancel()
      }
    }
    return this@Node.observeJob!!
  }

  override fun getUnacknowledgedMessages(
    node: Node
  ): List<GameMessage> {
    if(nodeState < NodeT.NodeState.Disconnected) {
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
      val now = System.currentTimeMillis()
      val nextDelay = checkNodeConditions(now)
      sendPingIfNecessary(nextDelay, now)

      return nextDelay
    }
  }

  private fun onDetaching(
  ): Long {
    synchronized(msgForAcknowledge) {
      if(nodeState <= NodeT.NodeState.Disconnected) return 0
      val ret = checkNodeConditions(System.currentTimeMillis())
      if(msgForAcknowledge.isEmpty()) {
        stateHolder.set(NodeT.NodeState.Terminated)
        return 0
      }
      return ret
    }
  }
}