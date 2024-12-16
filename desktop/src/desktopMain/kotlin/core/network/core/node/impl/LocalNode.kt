package d.zhdanov.ccfit.nsu.core.network.core.node.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ActiveObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.DefaultObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.PlugObserver
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger { LocalNode::class.java }

class LocalNode(
  override val nodeId: Int,
  override val name: String,
  private val clusterNodesHolder: ClusterNodesHolder,
  payload: NodePayloadT,
) : ClusterNodeT<Node.MsgInfo> {
  private val onPassiveHandler = Channel<Node.NodeState>()
  private val onTerminatedHandler = Channel<Node.NodeState>()
  
  override val ipAddress: InetSocketAddress
    get() = LocalIpAddress
  override val running: Boolean
    get() = true
  
  override var lastReceive: Long = 0
    get() = 0
    set(value) {
      field = 0
    }
  
  override var lastSend: Long = 0
    get() = 0
    set(value) {
      field = 0
    }
  
  override val payload: NodePayloadT
    get() = stateHolder.get().second
  
  override val nodeState: Node.NodeState
    get() = stateHolder.get().first
  
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
  
  override fun sendToNode(msg: SnakesProto.GameMessage) {
  }
  
  override fun ackMessage(message: SnakesProto.GameMessage): Node.MsgInfo? {
    return null
  }
  
  override fun addMessageForAck(message: SnakesProto.GameMessage) {
  }
  
  @Synchronized
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      try {
        Logger.trace { "${this@LocalNode} startObservation" }
        while(isActive) {
          select<Unit> {
            onPassiveHandler.onReceive { state ->
              if(state != Node.NodeState.Passive) {
                Logger.warn {
                  "onPassiveHandler ${this@LocalNode} receive wrong $state"
                }
                return@onReceive
              }
              Logger.trace {
                "${this@LocalNode} receive switch to $state state"
              }
              onPassiveHandler.close()
              if(nodeState == Node.NodeState.Terminated) {
                return@onReceive
              }
              payload.observerDetached()
              stateHolder.set(state to DefaultObserverContext(this@LocalNode))
              this@LocalNode.clusterNodesHolder.apply {
                handleNodeDetach(this@LocalNode)
              }
            }
            
            onTerminatedHandler.onReceive { state ->
              if(state != Node.NodeState.Terminated) {
                Logger.warn {
                  "onTerminatedHandler ${this@LocalNode} receive wrong $state"
                }
                return@onReceive
              }
              Logger.trace {
                "${this@LocalNode} receive switch to $state state"
              }
              onTerminatedHandler.close()
              payload.observerDetached()
              stateHolder.set(state to PlugObserver)
              this@LocalNode.clusterNodesHolder.apply {
                handleNodeDetach(this@LocalNode)
              }
            }
          }
        }
      } catch(e: CancellationException) {
        this.cancel()
      }
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
  
  override fun getUnacknowledgedMessages(): List<Node.MsgInfo> {
    return emptyList()
  }
  
  override fun addAllMessageForAck(messages: Iterable<SnakesProto.GameMessage>) {
  }
  
  companion object LocalIp {
    val LocalIpAddress = InetSocketAddress(InetAddress.getLocalHost(), 0)
  }
}
