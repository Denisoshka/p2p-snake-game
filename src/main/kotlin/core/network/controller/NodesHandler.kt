package d.zhdanov.ccfit.nsu.core.network.controller

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class NodesHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  joinBacklog: Int,
  val resendDelay: Long, val thresholdDelay: Long,
  private val localNode: NodeT<InetSocketAddress>,
  private val netController: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val messageTranslator: InboundMessageTranslator,
) : NodeContext<MessageT, InboundMessageTranslator, Payload> {
  private val nodesScope = CoroutineScope(Dispatchers.Default)
  private val nodesByIp =
    ConcurrentHashMap<InetSocketAddress, Node<MessageT, InboundMessageTranslator, Payload>>()
  @Volatile lateinit var observerJob: Job
  private val deadNodeChannel =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)
  private val registerNewNode =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)
  private val reconfigureContext =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)

  init {
    observerJob = launchNodesWatcher()
  }

  fun initHandler() {
    synchronized(this) {
      observerJob = launchNodesWatcher()
    }
  }

  fun shutdownHandler() {
    synchronized(this) {
      nodesScope.cancel()
      nodesByIp.clear()
    }
  }

  /**
   * Мы меняем состояние кластера в одной функции так что исполнение линейно
   */
  private fun launchNodesWatcher(): Job {
    return nodesScope.launch {
      while(true) {
        select {
          deadNodeChannel.onReceive { node -> onNodeTermination(node) }
          registerNewNode.onReceive { node -> onNodeRegistration(node) }
          reconfigureContext.onReceive { node -> onNodeDetach(node) }
        }
      }
    }
  }

  override fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)


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