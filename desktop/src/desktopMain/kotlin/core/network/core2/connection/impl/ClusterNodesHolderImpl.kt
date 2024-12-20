package d.zhdanov.ccfit.nsu.core.network.core2.connection.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core2.nethandler.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.network.core2.states.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class ClusterNodesHolderImpl(
  override val resendDelay: Long,
  override val thresholdDelay: Long,
  private val stateHolder: StateHolder,
  private val unicastHandler: UnicastNetHandler,
) : ClusterNodesHolder {
  @Volatile private var nodesScope: CoroutineScope? = null
  private val nodesByIp =
    ConcurrentHashMap<InetSocketAddress, ClusterNodeImpl>()
  
  override val launched: Boolean
    get() = nodesScope?.isActive ?: false
  override val nextSeqNum
    get() = stateHolder.nextSeqNum
  
  @Synchronized
  override fun launch() {
    this.nodesScope ?: TODO("Not yet implemented")
    nodesScope = CoroutineScope(Dispatchers.IO)
  }
  
  @Synchronized
  override fun shutdown() {
    nodesScope?.cancel()
    TODO("Not yet implemented")
  }
  
  override fun clear() {
    nodesByIp.clear()
  }
  
  override fun registerNode(
    ipAddress: InetSocketAddress,
    id: Int
  ): ClusterNode {
    TODO("Not yet implemented")
  }
  
  override suspend fun handleNodeTermination(node: ClusterNode) {
    nodesByIp.remove(node.ipAddress)
    stateHolder.handleNodeTermination(node)
  }
  
  override suspend fun handleSwitchToPassive(node: ClusterNode) {
    stateHolder.handleNodeTermination(node)
  }
  
  override fun sendUnicast(
    msg: SnakesProto.GameMessage,
    nodeAddress: InetSocketAddress
  ) {
    unicastHandler.sendUnicastMessage(msg, nodeAddress)
  }
  
  override fun get(ipAddress: InetSocketAddress): ClusterNode? {
    return nodesByIp[ipAddress]
  }
}