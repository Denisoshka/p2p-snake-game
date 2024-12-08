package d.zhdanov.ccfit.nsu.core.network.core.states.node.lobby.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerAlreadyInitialized
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeContext
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class NetNodeContext(
  private val netHandler: UnicastNetHandler, override val launched: Boolean
) : Iterable<Map.Entry<InetSocketAddress, NodeT>>, NodeContext {
  @Volatile private var joinWaitScope: CoroutineScope? = null
  @Volatile private var nodesScope: CoroutineScope? = null
  private val nodesByIp = ConcurrentHashMap<InetSocketAddress, NodeT>()
  override fun iterator(): Iterator<Map.Entry<InetSocketAddress, NodeT>> {
    return nodesByIp.entries.iterator()
  }

  @Synchronized
  /**
   * @throws IllegalNodeHandlerAlreadyInitialized
   * */
  override fun launch() {
    this.nodesScope ?: throw IllegalNodeHandlerAlreadyInitialized()
    this.nodesScope = CoroutineScope(Dispatchers.Default);
  }

  override fun shutdown() {
    nodesScope?.cancel()
    nodesByIp.clear()
  }

  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = netHandler.sendUnicastMessage(msg, nodeAddress)

  override fun registerNode(node: NodeT): NodeT {
    nodesByIp.putIfAbsent(node.ipAddress, node)?.let {
      with(it) {
        nodesScope?.startObservation()
          ?: throw IllegalNodeRegisterAttempt("nodesScope absent")
      }
      return it
    } ?: throw IllegalNodeRegisterAttempt("node already registered")
  }

  override fun get(ipAddress: InetSocketAddress): NodeT? {
    return nodesByIp[ipAddress]
  }

  override suspend fun handleNodeTermination(node: NodeT) {
    TODO("Not yet implemented")
  }

  override suspend fun handleNodeDetach(node: NodeT) {
    TODO("Not yet implemented")
  }
}