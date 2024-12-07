package d.zhdanov.ccfit.nsu.core.network.core.states.nodes

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerAlreadyInitialized
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class NodesHandler(
  joinBacklog: Int,
  @Volatile var resendDelay: Long,
  @Volatile var thresholdDelay: Long,
  private val ncStateMachine: NetworkStateMachine,
) : NodeContext, Iterable<Map.Entry<InetSocketAddress, NodeT>> {
  override val launched: Boolean
    get() = nodesScope?.isActive ?: false

  @Volatile private var nodesScope: CoroutineScope? = null
  private val nodesByIp = ConcurrentHashMap<InetSocketAddress, NodeT>()
  private val deadNodeChannel = Channel<NodeT>(joinBacklog)
  private val registerNewNode = Channel<NodeT>(joinBacklog)
  private val reconfigureContext = Channel<NodeT>(joinBacklog)
  val nextSeqNum
    get() = ncStateMachine.nextSegNum

  /**
   * @throws IllegalNodeHandlerAlreadyInitialized
   * */
  override fun launch() {
    synchronized(this) {
      this.nodesScope ?: throw IllegalNodeHandlerAlreadyInitialized()
      this.nodesScope = CoroutineScope(Dispatchers.Default);
    }
  }

  override fun shutdown() {
    synchronized(this) {
      nodesScope?.cancel()
      nodesByIp.clear()
    }
  }

  /**
   * Мы меняем состояние кластера в одной функции так что исполнение линейно
   */
  private fun launchNodesWatcher(): Job {
    return nodesScope?.launch {
      while(true) {
        select {
          registerNewNode.onReceive { node -> TODO() }
          reconfigureContext.onReceive { node ->
            ncStateMachine.handleNodeDetach(node)
          }
          deadNodeChannel.onReceive { node ->
            nodesByIp.remove(node.ipAddress)
            ncStateMachine.handleNodeDetach(node)
          }
        }
      }
    } ?: throw IllegalNodeRegisterAttempt("nodesScope absent")
  }

  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = ncStateMachine.sendUnicast(msg, nodeAddress)

  override fun registerNode(
    node: NodeT, registerInContext: Boolean
  ): NodeT {
    nodesByIp.putIfAbsent(node.ipAddress, node)?.let {
      with(it) {
        nodesScope?.startObservation()
          ?: throw IllegalNodeRegisterAttempt("nodesScope absent")
      }
      return it
    } ?: throw IllegalNodeRegisterAttempt("node already registered")
  }

  override suspend fun handleNodeRegistration(
    node: NodeT
  ) = registerNewNode.send(node)

  override suspend fun handleNodeTermination(
    node: NodeT
  ) = deadNodeChannel.send(node)

  override suspend fun handleNodeDetachPrepare(
    node: NodeT
  ) = reconfigureContext.send(node)

  override fun iterator(): Iterator<Map.Entry<InetSocketAddress, NodeT>> {
    return nodesByIp.entries.iterator()
  }

  override operator fun get(ipAddress: InetSocketAddress): NodeT? {
    return nodesByIp[ipAddress]
  }
}