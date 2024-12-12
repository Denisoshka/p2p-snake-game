package core.network.core.connection.game.impl

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ObserverContext
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger {}

/**
 * todo fix doc
 */
class ClusterNode(
  nodeState: Node.NodeState,
  override val name: String,
  override val nodeId: Int,
  override val ipAddress: InetSocketAddress,
  private val clusterNodesHandler: ClusterNodesHandler
) : ClusterNodeT<Node.MsgInfo> {
  private val thresholdDelay = clusterNodesHandler.thresholdDelay
  @Volatile override var lastReceive = System.currentTimeMillis()
  @Volatile override var lastSend = System.currentTimeMillis()
  @Volatile var changedToPassive = false
  override val running: Boolean
    get() = (payload != null)
  
  override val payload: ObserverContext?
    get() = stateHolder.get().second
  override val nodeState: Node.NodeState
    get() = stateHolder.get().first
  private val resendDelay = clusterNodesHandler.resendDelay
  private val stateHolder: AtomicReference<Pair<Node.NodeState, ObserverContext?>> =
    when(nodeState) {
      Node.NodeState.Actor    -> {
        AtomicReference(Pair(Node.NodeState.Actor, null))
      }
      
      Node.NodeState.Listener -> {
        AtomicReference(Pair(Node.NodeState.Listener, ObserverContext(this)))
      }
      
      else                    -> {
        throw IllegalNodeRegisterAttempt("illegal initial node state $nodeState")
      }
    }
  
  @Volatile private var observeJob: Job? = null
  
  /**
   * Use this valuee within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<SnakesProto.GameMessage, Node.MsgInfo> =
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
  
  override fun ackMessage(message: SnakesProto.GameMessage): Node.MsgInfo? {
    synchronized(msgForAcknowledge) {
      val ret = msgForAcknowledge.remove(message)
      lastReceive = System.currentTimeMillis()
      return ret
    }
  }
  
  override fun addMessageForAck(message: SnakesProto.GameMessage) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] =
        Node.MsgInfo(message, System.currentTimeMillis())
      lastSend = System.currentTimeMillis()
    }
  }
  
  override fun addAllMessageForAck(messages: List<Node.MsgInfo>) {
    synchronized(msgForAcknowledge) {
      messages.forEach {
        msgForAcknowledge[it.req] = it.apply {
          lastReceive = System.currentTimeMillis()
        }
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
  
  private fun changeState(newState: Pair<Node.NodeState, ObserverContext?>): Boolean {
    var prevState: Pair<Node.NodeState, ObserverContext?>
    do {
      prevState = stateHolder.get()
      if(newState.first <= prevState.first) return false;
    } while(stateHolder.compareAndSet(prevState, newState))
    prevState.second?.onContextObserverTerminated()
    return true
  }
  
  override fun detach() {
    payload?.onContextObserverTerminated()
    ObserverContext(this).apply {
      if(!changeState(Node.NodeState.Actor to this)) {
        this.onContextObserverTerminated()
      } else {
        changedToPassive = true
      }
    }
  }
  
  override fun shutdown() {
    payload?.onContextObserverTerminated()
    changeState(Node.NodeState.Terminated to null)
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
            Node.NodeState.Listener, Node.NodeState.Actor -> {
              if(changedToPassive) {
                this@ClusterNode.clusterNodesHandler.handleNodeDetach(
                  this@ClusterNode
                )
              }
              nextDelay = onProcessing()
            }
            
            Node.NodeState.Terminated                     -> {
              Logger.trace { "${this@ClusterNode} terminated" }
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
   * [Node.NodeState.Terminated]
   * */
  override fun getUnacknowledgedMessages(): List<Node.MsgInfo> {
    if(nodeState < Node.NodeState.Terminated) {
      throw IllegalUnacknowledgedMessagesGetAttempt()
    }
    return synchronized(msgForAcknowledge) {
      msgForAcknowledge.values.toList();
    }
  }
  
  private fun checkNodeConditions(now: Long): Long {
    if(now - lastReceive > thresholdDelay) {
      shutdown()
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
  
  override fun toString(): String {
    return "Node(nodeId=$nodeId, ipAddress=$ipAddress, running=$running, nodeState=$nodeState)"
  }
}