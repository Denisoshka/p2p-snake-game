package core.network.nodes

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.network.controller.NetworkController
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * Changes the state of the cluster in a single thread, so that if any
 * other node fails during the state change, it will be taken into account.
 *
 * This ensures that the system remains consistent and can handle
 * failures gracefully. The following scenarios illustrate how nodes
 * manage role transitions based on their current state:
 * Describes the role management and transitions in a distributed game system.
 *
 * The following scenarios outline how nodes handle the failure
 * of their peers based on their roles:
 *
 * a) **Node [NodeRole.NORMAL] notices that [NodeRole.MASTER] has dropped.**
 * The node replaces the information about the central node with the
 * [NodeRole.DEPUTY]. It starts sending all unicast messages toward the
 * [NodeRole.DEPUTY].
 *
 * b) **Node [NodeRole.MASTER] notices that [NodeRole.DEPUTY] has dropped.**
 * The [NodeRole.MASTER] node selects a new [NodeRole.DEPUTY] from among
 * the [NodeRole.NORMAL] nodes. It informs the current DEPUTY about this
 * change using a
 * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
 * Other nodes learn about the new [NodeRole.DEPUTY] from a scheduled
 * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg],
 * as it is not urgent for them to know immediately.
 *
 * c) **Node with [NodeRole.DEPUTY] role notices that [NodeRole.MASTER] has
 * dropped.** The DEPUTY node becomes the [NodeRole.MASTER] (takes
 * over control of the game). It selects a new [NodeRole.DEPUTY]
 * and informs all players about this change using a
 * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
 *
 * ***Message regarding role changes in the game.***
 *
 * **This message can take different forms depending on the roles of the
 * participants and their actions:**
 *
 * 1. **From a Deputy** to other players indicating that they should start
 * considering him as the Master. ( *senderRole* = [NodeRole.MASTER])
 *
 * 2. **From an intentionally exiting player**.
 * (*senderRole* = [NodeRole.VIEWER])
 *
 * 3. **From the Master** to a deceased player.
 * (*receiverRole* = [NodeRole.VIEWER])
 *
 * 4. **Appointment of someone as Deputy**.
 * In combination with 1, 2, or separately
 * (*receiverRole* = [NodeRole.DEPUTY])
 *
 * 5. In combination with 2, from the Master to the Deputy indicating
 * that he is becoming the Master.
 * (*receiverRole* = [NodeRole.MASTER])
 */
class NodesHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  joinBacklog: Int,
  masterNode: Node<MessageT, InboundMessageTranslator, Payload>? = null,
  val resendDelay: Long,
  val thresholdDelay: Long,
  private val localNode: NodeT<InetSocketAddress>,
  private val p2p: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val messageTranslator: InboundMessageTranslator,
) : NodeContext<MessageT, InboundMessageTranslator> {
  private val nodesScope = CoroutineScope(Dispatchers.Default)
  private val nodesByIp =
    ConcurrentHashMap<InetSocketAddress, Node<MessageT, InboundMessageTranslator, Payload>>()
  private val deadNodeChannel =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)
  private val registerNewNode =
    Channel<Node<MessageT, InboundMessageTranslator, Payload>>(joinBacklog)
  private val reconfigureContext =
    Channel<Pair<Node<MessageT, InboundMessageTranslator, Payload>, P2PMessage>>(
      joinBacklog
    )
  private val masterAndDeputy =
    AtomicReference<Pair<NodeT<InetSocketAddress>, NodeT<InetSocketAddress>?>>()

  init {
    masterNode?.let { nodesByIp.put(it.address, it) }
    launchNodesWatcher()
  }

  fun destroy() {
    nodesScope.cancel()
    nodesByIp.clear()
  }

  /*
  * мы меняем состояние кластера в одной функции так что исполнение линейно
  * */
  private fun launchNodesWatcher(): Job {
    return nodesScope.launch {
      while(true) {
        select {
          deadNodeChannel.onReceive { node ->
            onNodeTermination(node)
          }
          registerNewNode.onReceive { node ->
            onNodeRegistration(node)
          }
          reconfigureContext.onReceive { (node, _) ->
            prepareForDetachNode(node)
          }
        }
      }
    }
  }

  fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) {
    p2p.sendUnicast(msg, nodeAddress)
  }

  override suspend fun handleNodeRegistration(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) = registerNewNode.send(node)

  override suspend fun handleNodeTermination(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) = deadNodeChannel.send(node)

  override suspend fun handleNodeRoleChange(
    node: Node<MessageT, InboundMessageTranslator, Payload>,
    p2pRoleChange: P2PMessage
  ) = reconfigureContext.send(Pair(node, p2pRoleChange))

  private fun onNodeTermination(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    /*можем попасть сюда только когда сама нода закончит работать, так что нам
    нужно проверить нет ли у ноды каких либо привилегий и просто удалить ее*/
    val (master, deputy) = masterAndDeputy.get()
    deleteNode(node)
    /*сюда мы можем попадаем когда  */
    if(localNode.id == master.id) {
      if(node.id == deputy?.id) masterOnDeputyExit(node)
      else if(node.id == master.id) deputyOnMasterExit(node)
    } else {
      if(localNode.id == deputy?.id)
      /**/
        if() {
          deputy?.let {

          } ?: {

          }
        }
    }
  }

  /**
   * @throws IllegalArgumentException if [NodeRole] != [NodeRole.NORMAL]
   * */
  private fun onNodeRegistration(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    if(node.nodeState != NodeT.NodeState.Active) return
    val (masterId, deputyId) = masterAndDeputy.get()
    deputyId ?: return
    nodeRole = NodeRole.DEPUTY
    masterAndDeputy.set(Pair(masterId, node.id))
    node.also {
      sendRoleChange(it, NodeRole.MASTER, NodeRole.DEPUTY)
    }
  }

  private fun sendRoleChange(
    node: Node<MessageT, InboundMessageTranslator, Payload>,
    senderRole: NodeRole?, receiverRole: NodeRole?
  ) {
    val p2pMsg = getRoleChangeMsg(
      senderRole, receiverRole, node.getNextMSGSeqNum(), localNode.id, node.id
    )
    val msg = messageTranslator.toMessageT(p2pMsg, MessageType.RoleChangeMsg)
    sendWithAck(msg, node)
  }

  /**
   * Perform logic only when [NodeRole]==[NodeRole.MASTER]
   * other msgs handle higher
   */
  fun prepareForDetachNode(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    val (masterId, deputyId) = masterAndDeputy.get()
    assert(
      localNode === masterId
    ) { "only when NodesContext.nodeRole==NodeRole.MASTER" }

    /*todo что здесь вообще */
    node.nodeState = NodeT.NodeState.Listening
    if(node.id == deputyId?.id) {
      masterSetNewDeputy(node)?.also {
        sendRoleChange(it, NodeRole.MASTER, NodeRole.DEPUTY)
      } ?: run {
        masterAndDeputy.set(Pair(masterId, null))
      }
    }
  }

  suspend fun detachNode(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    if(node.nodeState == NodeRole.VIEWER) {
      deleteNode(node)
      return
    }
    deadNodeChannel.send(node)
  }

  private fun onMasterExit(
  ) {
    /**
     * Processes the current state received from the cluster.
     *
     * If we are aware of other nodes in the cluster but do not receive notification
     * of a new leader election, we assume that we have been disconnected from the cluster.
     *
     * However, if we appear to be the only remaining node, we designate ourselves as the master.
     */
    val (master, deputy) = masterAndDeputy.get()
    @Suppress("UNCHECKED_CAST")
    master as Node<MessageT, InboundMessageTranslator, Payload>

    when(deputy) {
      null             -> {
        takeOverTheBoard()
      }

      localNode        -> {

      }

      is Node<*, *, *> -> {
        @Suppress("UNCHECKED_CAST")
        deputy as Node<MessageT, InboundMessageTranslator, Payload>
        deputy.also {
          val nonAckMsgs = master.getUnacknowledgedMessages()
          for(msg in nonAckMsgs) sendUnicast(msg, it.address)
          it.addAllMessageForAcknowledge(nonAckMsgs)
          return
        }
      }

      else             -> {
        throw RuntimeException("любопытно сэр, какого хуя у нас нет депути")
      }
    }
  }

  private fun deputyOnMasterExit(
    master: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    val newDeputy = masterSetNewDeputy(master) ?: return
    for((_, node) in nodesByIp) {
      if(newDeputy != node) sendRoleChange(node, NodeRole.MASTER, null)
    }
    sendRoleChange(newDeputy, NodeRole.MASTER, NodeRole.DEPUTY)
  }

  private fun getRoleChangeMsg(
    sendRole: NodeRole?, recvRole: NodeRole?, seqNum: Long, sendId: Int,
    recId: Int
  ): P2PMessage {
    val role = RoleChangeMsg(sendRole, recvRole)
    return P2PMessage(seqNum, role, sendId, recId)
  }

  private fun masterSetNewDeputy(
    oldDeputy: Node<MessageT, InboundMessageTranslator, Payload>
  ): Node<MessageT, InboundMessageTranslator, Payload>? {
    val newDeputy = chooseNewDeputy(oldDeputy)?.also {
      masterAndDeputy.set(Pair(localNode, it))
    }
    return newDeputy
  }

  /**
   * Selects a new deputy, assigns it to [oldDeputy], and returns it. If no
   * suitable candidate for deputy is found, assigns and returns `null`.
   *
   * @return [oldDeputy] if a deputy was chosen, otherwise `null`.
   */
  private fun chooseNewDeputy(
    oldDeputy: Node<MessageT, InboundMessageTranslator, Payload>
  ): Node<MessageT, InboundMessageTranslator, Payload>? {
    for((_, node) in nodesByIp) {
      if(node.nodeState != NodeT.NodeState.Active) continue
      return node
    }
    return null
  }

  private fun takeOverTheBoard() {

  }

  private fun deleteNode(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    nodesByIp.remove(node.address)
  }

  private fun addNode(
    masterNode: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    nodesByIp[masterNode.address] = masterNode
  }

  private fun sendWithAck(
    msg: MessageT, node: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    sendUnicast(msg, node)
    node.addMessageForAcknowledge(msg)
  }

  private fun masterOnDeputyExit(
    deputy: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
    val newDeputy = masterSetNewDeputy(deputy) ?: return
    sendRoleChange(newDeputy, NodeRole.MASTER, NodeRole.DEPUTY)
  }
}