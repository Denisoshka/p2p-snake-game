package core.network.core

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerInit
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class NodesHandler(
  joinBacklog: Int,
  @Volatile var resendDelay: Long,
  @Volatile var thresholdDelay: Long,
  private val ncStateMachine: NetworkStateMachine,
) : NodeContext, Iterable<Map.Entry<InetSocketAddress, Node>> {
  @Volatile private var nodesScope: CoroutineScope? = null
  val nodesByIp = ConcurrentHashMap<InetSocketAddress, Node>()
  private val deadNodeChannel = Channel<Node>(joinBacklog)
  private val registerNewNode = Channel<Node>(joinBacklog)
  private val reconfigureContext = Channel<Node>(joinBacklog)
  val nextSeqNum
    get() = ncStateMachine.nextSegNum

  /**
   * @throws IllegalNodeHandlerInit
   * */
  override fun launch() {
    synchronized(this) {
      nodesScope ?: throw IllegalNodeHandlerInit()
      nodesScope = CoroutineScope(Dispatchers.Default);
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
          registerNewNode.onReceive { node -> onNodeRegistration(node) }
          reconfigureContext.onReceive { node ->
            ncStateMachine.handleNodeDetach(node)
          }
          deadNodeChannel.onReceive { node ->
            nodesByIp.remove(node.ipAddress)
            ncStateMachine.handleNodeDetach(node)
          }
        }
      }
    } ?: throw RuntimeException(
      "xyi ну вообще node scope не должен быть равен null"
    )
  }

  fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = ncStateMachine.sendUnicast(msg, nodeAddress)

  override fun registerNode(
    node: Node, registerInContext: Boolean
  ): Node {
    nodesByIp.putIfAbsent(node.ipAddress, node) ?: return node
    throw IllegalNodeRegisterAttempt("node already registered")
  }

  override suspend fun handleNodeRegistration(
    node: Node
  ) = registerNewNode.send(node)

  override suspend fun handleNodeTermination(
    node: Node
  ) = deadNodeChannel.send(node)

  override suspend fun handleNodeDetachPrepare(
    node: Node
  ) = reconfigureContext.send(node)

  override fun iterator(): Iterator<Map.Entry<InetSocketAddress, Node>> {
    return nodesByIp.entries.iterator()
  }

  override operator fun get(ipAddress: InetSocketAddress): Node? {
    return nodesByIp[ipAddress]
  }
}