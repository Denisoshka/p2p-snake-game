package d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.GameNodeT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger {}

/**
 * todo fix doc
 */
class GameNode(
  messageComparator: Comparator<SnakesProto.GameMessage>,
  nodeState: NodeT.NodeState,
  override val nodeId: Int,
  override val ipAddress: InetSocketAddress,
  @Volatile override var payload: NodePayloadT? = null,
  private val gameNodesHandler: GameNodesHandler,
) : GameNodeT {
  @Volatile override var lastReceive = System.currentTimeMillis()
  @Volatile override var lastSend = System.currentTimeMillis()
  override val running: Boolean
    get() = with(nodeState) {
      this == NodeT.NodeState.Active || this == NodeT.NodeState.Passive
    }

  override val nodeState: NodeT.NodeState
    get() = stateHolder.get()
  private val resendDelay = gameNodesHandler.resendDelay


  private val thresholdDelay = gameNodesHandler.thresholdDelay
  private val stateHolder = AtomicReference(
    if(nodeState != NodeT.NodeState.Active && nodeState != NodeT.NodeState.Passive) {
      throw IllegalNodeRegisterAttempt("illegal initial node state $nodeState")
    } else {
      nodeState
    }
  )
  @Volatile private var observeJob: Job? = null

  /**
   * Use this valuee within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<SnakesProto.GameMessage, NodeT.MsgInfo> =
    TreeMap(
      messageComparator
    )

  override fun sendToNode(msg: SnakesProto.GameMessage) {
    gameNodesHandler.sendUnicast(msg, ipAddress)
  }

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = gameNodesHandler.nextSeqNum

    val ping = gameNodesHandler.msgUtils.getPingMsg(seq)

    sendToNode(ping)

    lastSend = System.currentTimeMillis()
  }

  override fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage? {
    synchronized(msgForAcknowledge) {
      lastReceive = System.currentTimeMillis()
      return msgForAcknowledge.remove(message)?.msg
    }
  }

  override fun addMessageForAck(message: SnakesProto.GameMessage) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] =
        NodeT.MsgInfo(message, System.currentTimeMillis())
      lastSend = System.currentTimeMillis()
    }
  }

  override fun addAllMessageForAck(messages: List<SnakesProto.GameMessage>) {
    synchronized(msgForAcknowledge) {
      messages.forEach {
        msgForAcknowledge[it] = NodeT.MsgInfo(it, System.currentTimeMillis())
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

  private fun changeState(newState: NodeT.NodeState): Boolean {
    do {
      val prevState = nodeState
      if(newState <= prevState) return false;
    } while(stateHolder.compareAndSet(prevState, newState))
    return true
  }

  override fun detach() {
    changeState(NodeT.NodeState.Disconnected)
  }

  override fun shutdown() {
    changeState(NodeT.NodeState.Terminated)
  }

  @Synchronized
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      var nextDelay = 0L
      var detachedFromCluster = false
      try {
        Logger.trace { "${this@GameNode} startObservation" }
        while(isActive) {
          delay(nextDelay)
          when(nodeState) {
            NodeT.NodeState.Active, NodeT.NodeState.Passive -> {
              nextDelay = onProcessing()
            }

            NodeT.NodeState.Disconnected                    -> {
              if(!detachedFromCluster) {
                Logger.trace { "${this@GameNode} disconnected" }
                detachedFromCluster = true
                this@GameNode.payload?.onContextObserverTerminated()
                this@GameNode.payload = null
                this@GameNode.gameNodesHandler.handleNodeDetach(this@GameNode)
              }
              nextDelay = onDetaching()
            }

            NodeT.NodeState.Terminated                      -> {
              Logger.trace { "${this@GameNode} terminated" }
              this@GameNode.payload?.onContextObserverTerminated()
              this@GameNode.payload = null
              this@GameNode.gameNodesHandler.handleNodeTermination(this@GameNode)
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
   * @throws IllegalUnacknowledgedMessagesGetAttempt if [GameNode.nodeState] <
   * [GameNodeT.NodeState.Disconnected]
   * */
  override fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage> {
    if(nodeState < NodeT.NodeState.Disconnected) {
      throw IllegalUnacknowledgedMessagesGetAttempt()
    }
    return synchronized(msgForAcknowledge) {
      msgForAcknowledge.keys.toList();
    }
  }

  private fun checkNodeConditions(now: Long): Long {
    if(now - lastReceive > thresholdDelay) {
      stateHolder.set(NodeT.NodeState.Terminated)
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
      if(nodeState <= NodeT.NodeState.Disconnected) return 0
      val ret = checkNodeConditions(System.currentTimeMillis())
      if(msgForAcknowledge.isEmpty()) {
        stateHolder.set(NodeT.NodeState.Terminated)
        return 0
      }
      return ret
    }
  }

  override fun toString(): String {
    return "Node(nodeId=$nodeId, ipAddress=$ipAddress, running=$running, nodeState=$nodeState)"
  }
}