package core.network.core.connection.lobby.impl

import d.zhdanov.ccfit.nsu.core.network.core.node.NodeContext
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerAlreadyInitialized
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class NetNodeHandler(
  private val ncStateMachine: NetworkStateHolder,
) : Iterable<Map.Entry<InetSocketAddress, NetNode>>, NodeContext<NetNode> {
  @Volatile private var joinWaitScope: CoroutineScope? = null
  @Volatile private var nodesScope: CoroutineScope? = null
  private val nodesByIp = ConcurrentHashMap<InetSocketAddress, NetNode>()
  override fun iterator(): Iterator<Map.Entry<InetSocketAddress, NetNode>> {
    return nodesByIp.entries.iterator()
  }

  override val launched: Boolean
    get() = nodesScope?.isActive ?: false
  override val nextSeqNum: Long
    get() = ncStateMachine.nextSeqNum


  /**
   * @throws IllegalNodeHandlerAlreadyInitialized
   * */
  @Synchronized
  override fun launch() {
    this.nodesScope ?: throw IllegalNodeHandlerAlreadyInitialized()
    this.nodesScope = CoroutineScope(Dispatchers.IO);
  }
  
  @Synchronized
  override fun shutdown() {
    nodesScope?.cancel()
    nodesByIp.clear()
  }

  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = ncStateMachine.sendUnicast(msg, nodeAddress)

  override fun registerNode(node: NetNode): NetNode {
    nodesByIp.putIfAbsent(node.ipAddress, node)?.let {
      with(it) {
        nodesScope?.startObservation()
        ?: throw IllegalNodeRegisterAttempt("nodesScope absent")
      }
      return it
    } ?: throw IllegalNodeRegisterAttempt("node already registered")
  }

  override fun get(ipAddress: InetSocketAddress): NetNode? {
    return nodesByIp[ipAddress]
  }

  override suspend fun handleNodeTermination(node: NetNode) {
    nodesByIp.remove(node.ipAddress)
  }

  override suspend fun handleNodeDetach(node: NetNode) {
    TODO("Not yet implemented")
  }
}