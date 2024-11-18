package core.network.core

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerInit
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
) : NodeContext {
  val msgComparator = ncStateMachine.utils.getComparator()
  @Volatile private var nodesScope: CoroutineScope? = null
  val nodesByIp =
    ConcurrentHashMap<InetSocketAddress, Node>()
  private val deadNodeChannel =
    Channel<Node>(joinBacklog)
  private val registerNewNode =
    Channel<Node>(joinBacklog)
  private val reconfigureContext =
    Channel<Node>(joinBacklog)

  /**
   * @throws IllegalNodeHandlerInit
   * */
  fun initHandler() {
    synchronized(this) {
      nodesScope ?: throw IllegalNodeHandlerInit()
      nodesScope = CoroutineScope(Dispatchers.Default);
    }
  }

  fun shutdownHandler() {
    synchronized(this) {
      nodesScope?.cancel()
      nodesByIp.clear()
    }
  }

  fun getNode(ipAddr: InetSocketAddress) = nodesByIp[ipAddr]
  fun findNode(
    condition: (Node) -> Boolean
  ): Node? {
    for((_, node) in nodesByIp) {
      if(condition(node)) return node
    }
    return null
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
    } ?: throw RuntimeException("xyi")

  }

  override fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) = ncStateMachine.sendUnicast(msg, nodeAddress)

  override fun shutdown() {
    TODO("Not yet implemented")
  }


  fun addNewNode(
    initialSeq: Long,
    nodeRole: NodeRole,
    nodeId: Int,
    ipAddress: InetSocketAddress,
    registerInContext: Boolean
  ): Node<MessageT, InboundMessageTranslator, Payload> {
    nodesByIp[ipAddress]?.let { return it }
    val node = Node(
      initialSeq,
      msgComparator,
      nodeRole,
      nodesScope!!,
      nodeId,
      ipAddress,
      this,
      registerInContext
    )
    val ret = nodesByIp.putIfAbsent(ipAddress, node) ?: return node
    return ret
  }

  override suspend fun handleNodeRegistration(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) = registerNewNode.send(node)

  override suspend fun handleNodeTermination(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) = deadNodeChannel.send(node)

  override suspend fun handleNodeDetachPrepare(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) = reconfigureContext.send(node)
}