package core.network.core.connection.game.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger {}

/**
 * todo fix doc
 */
class ClusterNode(
  nodeState: Node.NodeState,
  override val nodeId: Int,
  override val ipAddress: InetSocketAddress,
  @Volatile override var payload: NodePayloadT? = null,
  private val clusterNodesHandler: ClusterNodesHandler,
) : ClusterNodeT {
  @Volatile override var lastReceive = System.currentTimeMillis()
  @Volatile override var lastSend = System.currentTimeMillis()
  override val running: Boolean
    get() = with(nodeState) {
      this == Node.NodeState.Active || this == Node.NodeState.Passive
    }

  override val nodeState: Node.NodeState
    get() = stateHolder.get()
  private val resendDelay = clusterNodesHandler.resendDelay


  private val thresholdDelay = clusterNodesHandler.thresholdDelay
  private val stateHolder = AtomicReference(
    if(nodeState != Node.NodeState.Active && nodeState != Node.NodeState.Passive) {
      throw IllegalNodeRegisterAttempt("illegal initial node state $nodeState")
    } else {
      nodeState
    }
  )
  @Volatile private var observeJob: Job? = null

  /**
   * Use this valuee within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<SnakesProto.GameMessage, d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT.ClusterNode.MsgInfo> =
    TreeMap(MessageUtils.messageComparator)

  override fun sendToNode(msg: SnakesProto.GameMessage) {
    clusterNodesHandler.sendUnicast(msg, ipAddress)
  }

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = clusterNodesHandler.nextSeqNum
    val ping = MessageUtils.MessageProducer.getPingMsg(seq)

    addMessageForAck(ping)
    sendToNode(ping)

    lastSend = System.currentTimeMillis()
  }

  override fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage? {
    synchronized(msgForAcknowledge) {
      return msgForAcknowledge.remove(message)?.let {
        lastReceive = System.currentTimeMillis()
        it.msg
      }
    }
  }

  override fun addMessageForAck(message: SnakesProto.GameMessage) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] =
        Node.MsgInfo(message, System.currentTimeMillis())
      lastSend = System.currentTimeMillis()
    }
  }

  override fun addAllMessageForAck(messages: List<SnakesProto.GameMessage>) {
    synchronized(msgForAcknowledge) {
      messages.forEach {
        msgForAcknowledge[it] = Node.MsgInfo(it, System.currentTimeMillis())
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
      val (msg, msgInfo) = entry
      if(now - msgInfo.lastCheck < thresholdDelay) {
        ret = ret.coerceAtMost(thresholdDelay + msgInfo.lastCheck - now)
        msgInfo.lastCheck = now
        sendToNode(msg)
      } else {
        it.remove()
      }
    }
    return ret
  }

  private fun changeState(newState: Node.NodeState): Boolean {
    do {
      val prevState = nodeState
      if(newState <= prevState) return false;
    } while(stateHolder.compareAndSet(prevState, newState))
    return true
  }

  override fun detach() {
    changeState(Node.NodeState.Disconnected)
  }

  override fun shutdown() {
    changeState(Node.NodeState.Terminated)
  }

  @Synchronized
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      var nextDelay = 0L
      var detachedFromCluster = false
      try {
        Logger.trace { "${this@ClusterNode} startObservation" }
        while(isActive) {
          delay(nextDelay)
          when(nodeState) {
            Node.NodeState.Active, Node.NodeState.Passive -> {
              nextDelay = onProcessing()
            }

            Node.NodeState.Disconnected                   -> {
              if(!detachedFromCluster) {
                Logger.trace { "${this@ClusterNode} disconnected" }
                detachedFromCluster = true
                this@ClusterNode.payload?.onContextObserverTerminated()
                this@ClusterNode.payload = null
                this@ClusterNode.clusterNodesHandler.handleNodeDetach(this@ClusterNode)
              }
              nextDelay = onDetaching()
            }

            Node.NodeState.Terminated                     -> {
              Logger.trace { "${this@ClusterNode} terminated" }
              this@ClusterNode.payload?.onContextObserverTerminated()
              this@ClusterNode.payload = null
              this@ClusterNode.clusterNodesHandler.handleNodeTermination(this@ClusterNode)
              break
            }
          }
        }
      } catch(e: CancellationException) {
        this.cancel()
      }
    }.also { observeJob = it }
  }

  /**
   * @throws IllegalUnacknowledgedMessagesGetAttempt if [Node.nodeState] <
   * [ClusterNodeT.NodeState.Disconnected]
   * */
  override fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage> {
    if(nodeState < Node.NodeState.Disconnected) {
      throw IllegalUnacknowledgedMessagesGetAttempt()
    }
    return synchronized(msgForAcknowledge) {
      msgForAcknowledge.keys.toList();
    }
  }

  private fun checkNodeConditions(now: Long): Long {
    if(now - lastReceive > thresholdDelay) {
      stateHolder.set(Node.NodeState.Terminated)
      return 0
    }
    return checkMessages()
  }

  private fun onProcessing(): Long {
    synchronized(msgForAcknowledge) {
      val now = System.currentTimeMillis()
      val nextDelay = checkNodeConditions(now)
      sendPingIfNecessary(nextDelay, now)

      return nextDelay
    }
  }

  private fun onDetaching(): Long {
    synchronized(msgForAcknowledge) {
      if(nodeState <= Node.NodeState.Disconnected) return 0
      val ret = checkNodeConditions(System.currentTimeMillis())
      if(msgForAcknowledge.isEmpty()) {
        stateHolder.set(Node.NodeState.Terminated)
        return 0
      }
      return ret
    }
  }

  override fun toString(): String {
    return "Node(nodeId=$nodeId, ipAddress=$ipAddress, running=$running, nodeState=$nodeState)"
  }
}