package core.network.core2.connection.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger { LocalClusterNodeImpl::class.java }

class LocalClusterNodeImpl(
  nodeRole: ClusterNode.NodeState,
  override val nodeId: Int,
  override val nodeHolder: ClusterNodesHolder,
  override val ipAddress: InetSocketAddress,
) : ClusterNode {
  private val onSwitchToPassive = Channel<ClusterNode.NodeState>(capacity = 1)
  private val onTerminatedHandler = Channel<ClusterNode.NodeState>(capacity = 1)
  private val stateHolder = AtomicReference<ClusterNode.NodeState>()
  override val nodeState: ClusterNode.NodeState
    get() = stateHolder.get()
  
  override var lastReceive: Long
    get() = System.currentTimeMillis()
    set(value) {}
  
  override var lastSend: Long
    get() = System.currentTimeMillis()
    set(value) {}
  
  init {
    if(nodeRole > ClusterNode.NodeState.Passive) {
      throw IllegalNodeRegisterAttempt("illegal initial node state $nodeRole")
    }
  }
  
  override fun sendToNode(msg: SnakesProto.GameMessage) {
  }
  
  override fun sendToNodeWithAck(msg: SnakesProto.GameMessage) {
  }
  
  override fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage? {
    return null
  }
  
  override fun addMessageForAck(message: SnakesProto.GameMessage) {
  }
  
  override fun addAllMessageForAck(messages: Iterable<SnakesProto.GameMessage>) {
  }
  
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      try {
        Logger.trace { "${this@LocalClusterNodeImpl} startObservation" }
        while(isActive) {
          select<Unit> {
            onSwitchToPassive.onReceive { state ->
              Logger.trace {
                "${this@LocalClusterNodeImpl} receive switch to $state state"
              }
              onSwitchToPassive.close()
              if(nodeState != ClusterNode.NodeState.Terminated) {
                stateHolder.set(ClusterNode.NodeState.Passive)
                this@LocalClusterNodeImpl.nodeHolder.apply {
                  handleSwitchToPassive(this@LocalClusterNodeImpl)
                }
              }
            }
            
            onTerminatedHandler.onReceive { state ->
              Logger.trace {
                "${this@LocalClusterNodeImpl} receive switch to $state state"
              }
              onTerminatedHandler.close()
              stateHolder.set(ClusterNode.NodeState.Terminated)
              this@LocalClusterNodeImpl.nodeHolder.apply {
                handleNodeTermination(this@LocalClusterNodeImpl)
              }
            }
          }
        }
      } catch(e: CancellationException) {
        this.cancel()
      } finally {
        onSwitchToPassive.close()
        onTerminatedHandler.close()
      }
    }
  }
  
  override fun markAsPassive() {
    onSwitchToPassive.trySend(ClusterNode.NodeState.Passive).onSuccess {
      Logger.trace { "$this mark as passive" }
    }
  }
  
  override fun shutdown() {
    onTerminatedHandler.trySend(ClusterNode.NodeState.Terminated).onSuccess {
      Logger.trace { "$this shutdowned" }
    }
  }
  
  override fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage> {
    return emptyList()
  }
}