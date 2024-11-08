package d.zhdanov.ccfit.nsu.core.network

import com.google.common.base.Preconditions
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

class NodeContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  joinBacklog: Int,
  private val p2p: P2PContext<MessageT, InboundMessageTranslator>,
  @Volatile private var nodeRole: NodeRole/*todo ну или это значение используется только в контексте когда мы смотрим
      ноды или его нужно хранить вместе с мастером и депутей*/,
  addrAndMsg: Pair<InetSocketAddress, P2PMessage>?
) {
  private val localNodeId: Int
  private val nodesScope = CoroutineScope(Dispatchers.Default)
  private val nodesByIp: ConcurrentHashMap<InetSocketAddress, Node<MessageT, InboundMessageTranslator>> =
    ConcurrentHashMap()
  private val nodesById: ConcurrentHashMap<Int, Node<MessageT, InboundMessageTranslator>> =
    ConcurrentHashMap()
  private val deadNodeChannel: Channel<Node<MessageT, InboundMessageTranslator>> =
    Channel(joinBacklog)
  private val registerNewNode: Channel<Pair<Node<MessageT, InboundMessageTranslator>, P2PMessage>> =
    Channel(joinBacklog)
  private val reconfigureContext: Channel<Pair<Node<MessageT, InboundMessageTranslator>, P2PMessage>> =
    Channel(joinBacklog)
  private val masterAndDeputy: AtomicReference<Pair<Int, Int?>> =
    AtomicReference()

  init {
    if (addrAndMsg != null) {
      Preconditions.checkArgument(
        nodeRole == NodeRole.MASTER || nodeRole == NodeRole.DEPUTY,
        "$nodeRole not in allowed roles [NodeRole.MASTER, NodeRole.DEPUTY]"
      )
      val (addr, msg) = addrAndMsg
      val masterId = Preconditions.checkNotNull(
        msg.senderId, "require senderId not null"
      )
      localNodeId = Preconditions.checkNotNull(
        msg.receiverId, "require receiverId not null"
      )
      val masterNode = Node(
        NodeRole.MASTER,
        msg.msgSeq,
        p2p.messageUtils.getComparator(),
        nodesScope,
        masterId,
        addr,
        p2p.resendDelay,
        p2p.thresholdDelay,
        this
      )
      masterAndDeputy.set(Pair(masterId, null))
      addNode(masterNode)
    } else {
      localNodeId = 0
      masterAndDeputy.set(Pair(localNodeId, null))
      nodeRole = NodeRole.MASTER
    }
    launchNodesWatcher()
  }


  fun destroy() {
    nodesScope.cancel()
    nodesByIp.clear()
    nodesById.clear()
  }

  private fun launchNodesWatcher(): Job {
    return nodesScope.launch {
      while (true) {
        select {
          deadNodeChannel.onReceive { node ->
            handleTerminatedNode(node)
          }
          registerNewNode.onReceive { (node, role) ->
            handleRegistrationNewNode(node, role)
          }

          reconfigureContext.onReceive { (node, role) ->
            handleReconfigureContext(node, role)
          }
        }
      }
    }
  }

  private suspend fun handleReconfigureContext(
    node: Node<MessageT, InboundMessageTranslator>, msg: P2PMessage
  ) {
    val (masterId, deputyId) = masterAndDeputy.get()


    if (msg.msg.type != MessageType.RoleChangeMsg) return


  }

  suspend fun handleTerminatedNode(
    node: Node<MessageT, InboundMessageTranslator>
  ) {/*
    * ну вообщем этот контекст будет выполняться в отдельной корутине поэтому
    *  не нужно беспокоиться о линейном порядке исполнения
    * */
    /**
     * [NodeRole] :
     * -> [NodeRole.NORMAL],
     * -> [NodeRole.MASTER],
     * -> [NodeRolez.DEPUTY],
     * -> [NodeRole.VIEWER]
     */
    val (masterId, deputyId) = masterAndDeputy.get()
    deleteNode(node)
    if (localNodeId == masterId) {
      if (node.id == deputyId) masterOnDeputyDead(node)
    } else if (localNodeId == deputyId) {
      if (node.id == masterId) deputyOnMasterDead(node)
    } else if (nodeRole == NodeRole.NORMAL) {
      if (node.id == masterId) normalOnMasterDead(node)/* ну а другие ноды мы и не трекаем*/
    } else {
      logger.debug { node.toString() }/*todo нужно сделать просто выход из изры ибо мы вивер*/
    }
  }

  private fun normalOnMasterDead(master: Node<MessageT, InboundMessageTranslator>) {
    val (_, deputyId) = masterAndDeputy.get()
    if (deputyId == null) {
      takeOverTheBoard()
    } else {
      nodesById[deputyId]?.also {
        val nonAckMsgs = master.getUnacknowledgedMessages()
        for (msg in nonAckMsgs) p2p.retrySendMessage(msg, it)
        it.addAllMessageForAcknowledge(nonAckMsgs)
        return
      }
      throw RuntimeException("какого хуя у нас нет депути")
    }
  }

  private fun deputyOnMasterDead(master: Node<MessageT, InboundMessageTranslator>) {
    setNewDeputy(master)
  }

  private fun masterOnDeputyDead(deputy: Node<MessageT, InboundMessageTranslator>) {
    setNewDeputy(deputy)
  }

  private fun setNewDeputy(oldDeputy: Node<MessageT, InboundMessageTranslator>) {
    val newDeputy = chooseNewDeputy(oldDeputy)
    masterAndDeputy.set(Pair(localNodeId, newDeputy?.id))
  }

  private fun chooseNewDeputy(oldDeputy: Node<MessageT, InboundMessageTranslator>): Node<MessageT, InboundMessageTranslator>? {
    for ((_, node) in nodesByIp) {
      if (node.id == oldDeputy.id || node.nodeRole != NodeRole.NORMAL || node.nodeState != Node.NodeState.Active) continue
      return node
    }
    return null
  }

  private fun takeOverTheBoard() {

  }

  private fun deleteNode(node: Node<MessageT, InboundMessageTranslator>) {
    nodesByIp.remove(node.address)
    nodesById.remove(node.id)
    node.shutdown()
  }

  private fun addNode(masterNode: Node<MessageT, InboundMessageTranslator>) {
    nodesByIp[masterNode.address] = masterNode
    nodesById[masterNode.id] = masterNode
  }
}