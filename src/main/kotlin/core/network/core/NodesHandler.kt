package d.zhdanov.ccfit.nsu.core.network.controller

import core.network.core.NetworkStateMachine
import core.network.core.exceptions.IllegalNodeHandlerInit
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class NodesHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  joinBacklog: Int,
  val resendDelay: Long,
  val thresholdDelay: Long,
//  private val netController: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
) : NodeContext<MessageT, InboundMessageTranslator, Payload> {
  @Volatile private var nodesScope: CoroutineScope? = null
  private val nodesByIp =
    ConcurrentHashMap<InetSocketAddress, Node<MessageT, InboundMessageTranslator, Payload>>()
  private val deadNodeChannel =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)
  private val registerNewNode =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)
  private val reconfigureContext =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)

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
    condition: (Node<MessageT, InboundMessageTranslator, Payload>) -> Boolean
  ): Node<MessageT, InboundMessageTranslator, Payload>? {
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


  override fun addNewNode(
    ipAddress: InetSocketAddress
  ): Node<MessageT, InboundMessageTranslator, Payload> {
    TODO("Not yet implemented")
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