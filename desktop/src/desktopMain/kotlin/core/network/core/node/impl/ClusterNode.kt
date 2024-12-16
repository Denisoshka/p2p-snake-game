package d.zhdanov.ccfit.nsu.core.network.core.node.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ActiveObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.DefaultObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.PlugObserver
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger { ClusterNode::class.java }

class ClusterNode(
  override val nodeId: Int,
  override val ipAddress: InetSocketAddress,
  private val clusterNodesHolder: ClusterNodesHolder,
  payload: NodePayloadT,
  override val name: String = ""
) : ClusterNodeT<Node.MsgInfo> {
  private val onPassiveHandler = Channel<Node.NodeState>()
  private val onTerminatedHandler = Channel<Node.NodeState>()
  private val onObserverSupplier = Channel<Node.NodeState>()
  private val thresholdDelay = clusterNodesHolder.thresholdDelay
  override var lastReceive = System.currentTimeMillis()
  override var lastSend = System.currentTimeMillis()
  
  override val running: Boolean
    get() = (payload !is PlugObserver)
  override val payload: NodePayloadT
    get() = stateHolder.get().second
  
  override val nodeState: Node.NodeState
    get() = stateHolder.get().first
  private val resendDelay = clusterNodesHolder.resendDelay
  private val stateHolder: AtomicReference<Pair<Node.NodeState, NodePayloadT>> =
    when(payload) {
      is ActiveObserverContext  -> {
        AtomicReference(Pair(Node.NodeState.Active, payload))
      }
      
      is DefaultObserverContext -> {
        AtomicReference(Pair(Node.NodeState.Passive, payload))
      }
      
      else                      -> {
        throw IllegalNodeRegisterAttempt("illegal initial node state $payload")
      }
    }
  
  /**
   * Use this value within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<SnakesProto.GameMessage, Node.MsgInfo> =
    TreeMap(MessageUtils.messageComparator)
  
  override fun sendToNode(msg: SnakesProto.GameMessage) {
    clusterNodesHolder.sendUnicast(msg, ipAddress)
  }
  
  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return
    
    val seq = clusterNodesHolder.nextSeqNum
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
  
  override fun addAllMessageForAck(messages: Iterable<SnakesProto.GameMessage>) {
    synchronized(msgForAcknowledge) {
      messages.forEach {
        msgForAcknowledge[it] = Node.MsgInfo(it, System.currentTimeMillis())
      }
      lastSend = System.currentTimeMillis()
    }
  }
  
  
  override fun detach() {
    onPassiveHandler.trySend(Node.NodeState.Passive).onSuccess {
      Logger.trace { "$this marked as passive" }
    }
  }
  
  override fun shutdown() {
    onTerminatedHandler.trySend(Node.NodeState.Terminated).onSuccess {
      Logger.trace { "$this marked as terminated" }
    }
  }
  
  @OptIn(ExperimentalCoroutinesApi::class)
  @Synchronized
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      var nextDelay = 0L
      try {
        Logger.trace { "${this@ClusterNode} startObservation" }
        while(isActive) {
          select<Unit> {
            onPassiveHandler.onReceive { state ->
              if(state != Node.NodeState.Passive) {
                Logger.warn {
                  "onPassiveHandler ${this@ClusterNode} receive wrong $state"
                }
                return@onReceive
              }
              Logger.trace {
                "${this@ClusterNode} receive switch to $state state"
              }
              onPassiveHandler.close()
              if(nodeState == Node.NodeState.Terminated) {
                return@onReceive
              }
              payload.observerDetached()
              stateHolder.set(state to DefaultObserverContext(this@ClusterNode))
              this@ClusterNode.clusterNodesHolder.apply {
                handleNodeDetach(this@ClusterNode)
              }
            }
            
            onTerminatedHandler.onReceive { state ->
              if(state != Node.NodeState.Terminated) {
                Logger.warn {
                  "onTerminatedHandler ${this@ClusterNode} receive wrong $state"
                }
                return@onReceive
              }
              Logger.trace {
                "${this@ClusterNode} receive switch to $state state"
              }
              onTerminatedHandler.close()
              payload.observerDetached()
              stateHolder.set(state to PlugObserver)
              this@ClusterNode.clusterNodesHolder.apply {
                handleNodeDetach(this@ClusterNode)
              }
            }
            
            onTimeout(nextDelay) {
              when(nodeState) {
                Node.NodeState.Active, Node.NodeState.Passive -> {
                  nextDelay = onProcessing()
                }
                
                Node.NodeState.Terminated                     -> {
                  /**
                   * по идее сюда вообще никогда не должны попасть
                   */
                }
              }
            }
          }
        }
      } catch(e: CancellationException) {
        this.cancel()
      } finally {
        onPassiveHandler.close()
        onTerminatedHandler.close()
        onObserverSupplier.close()
      }
    }
  }
  
  /**
   * @throws IllegalUnacknowledgedMessagesGetAttempt if [Node.nodeState] <
   * [Node.NodeState.Terminated]
   * */
  override fun getUnacknowledgedMessages(): List<Node.MsgInfo> {
    if(nodeState < Node.NodeState.Passive) {
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
  
  private fun onProcessing(): Long {
    synchronized(msgForAcknowledge) {
      val now = System.currentTimeMillis()
      val nextDelay = checkNodeConditions(now)
      sendPingIfNecessary(nextDelay, now)
      
      return nextDelay
    }
  }
  
  override fun toString(): String {
    return "ClusterNode(name='$name', nodeId=$nodeId, ipAddress=$ipAddress, nodeState=$nodeState)"
  }
}