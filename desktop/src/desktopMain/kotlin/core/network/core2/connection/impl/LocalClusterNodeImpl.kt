package core.network.core2.connection.impl

import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core2.connection.impl.ClusterNodeImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger { LocalClusterNodeImpl::class.java }

class LocalClusterNodeImpl(
  nodeRole: ClusterNode.NodeState,
  nodeId: Int,
  nodeHolder: ClusterNodesHolder
) : ClusterNodeImpl(
  nodeRole, nodeId, LocalIpAddress, nodeHolder
) {
  private val onSwitchToPassive = Channel<ClusterNode.NodeState>(capacity = 1)
  private val onTerminatedHandler = Channel<ClusterNode.NodeState>(capacity = 1)
  private val stateHolder = AtomicReference<ClusterNode.NodeState>()
  
  override var lastReceive: Long
    get() = super.lastReceive
    set(value) {}
  
  override var lastSend: Long
    get() = super.lastSend
    set(value) {}
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      var nextDelay = 0L
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
                "${this@ClusterNodeImpl} receive switch to $state state"
              }
              onTerminatedHandler.close()
              stateHolder.set(ClusterNode.NodeState.Terminated)
              this@ClusterNodeImpl.nodeHolder.apply {
                handleNodeTermination(this@ClusterNodeImpl)
              }
            }
            
            onTimeout(nextDelay) {
              when(nodeState) {
                ClusterNode.NodeState.Active, ClusterNode.NodeState.Passive -> {
                  nextDelay = onProcessing()
                }
                
                ClusterNode.NodeState.Terminated                            -> {
                  /**
                   * по идее сюда вообще никогда не должны попасть
                   */
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
        onSwitchToPassive.close()
        onTerminatedHandler.close()
      }
    }
  }
  
  companion object LocalIp {
    val LocalIpAddress = InetSocketAddress(InetAddress.getLocalHost(), 0)
  }
