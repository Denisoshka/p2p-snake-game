package d.zhdanov.ccfit.nsu.core.network.core2.connection.impl

import core.network.core2.connection.impl.LocalClusterNodeImpl
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
  @Volatile private var _localNode: ClusterNode? = null
  private val nodesByIp = ConcurrentHashMap<InetSocketAddress, ClusterNode>()
  
  override val launched: Boolean
    get() = nodesScope?.isActive ?: false
  override val nextSeqNum
    get() = stateHolder.nextSeqNum
  
  @Synchronized
  override fun launch(
    nodeId: Int, nodeRole: ClusterNode.NodeState
  ): ClusterNode {
    this.nodesScope ?: TODO("Not yet implemented")
    nodesScope = CoroutineScope(Dispatchers.IO)
    val locNode = appendLocalNode(nodeId, nodeRole)
    return locNode
  }
  
  @Synchronized
  override fun shutdown() {
    nodesScope?.cancel()
    clear()
  }
  
  override fun clear() {
//    todo нужно ли оставить локальную ноду?
    nodesByIp.clear()
  }
  
  override fun registerNode(
    ipAddress: InetSocketAddress, id: Int, nodeRole: ClusterNode.NodeState
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
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) {
    unicastHandler.sendUnicastMessage(msg, nodeAddress)
  }
  
  override fun get(ipAddress: InetSocketAddress): ClusterNode? {
    return nodesByIp[ipAddress]
  }
  
  override fun iterator(): Iterator<Map.Entry<InetSocketAddress, ClusterNode>> {
    return nodesByIp.entries.iterator()
  }
  
  private fun appendLocalNode(
    nodeId: Int, nodeRole: ClusterNode.NodeState
  ): ClusterNode {
    nodesByIp[ClusterNodesHolder.LocalIpAddress] = LocalClusterNodeImpl(
      nodeRole = nodeRole,
      nodeId = nodeId,
      nodeHolder = this,
      ipAddress = ClusterNodesHolder.LocalIpAddress
    ).apply {
      with(this) { nodesScope!!.startObservation() }
      _localNode = this
      return this
    }
  }
}