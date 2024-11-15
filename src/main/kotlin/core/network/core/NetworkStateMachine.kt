package core.network.core

import core.network.core.states.ActiveStateHandler
import core.network.core.states.LobbyState
import core.network.core.states.MasterState
import core.network.core.states.PassiveState
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.*
import d.zhdanov.ccfit.nsu.core.network.controller.NetworkController
import d.zhdanov.ccfit.nsu.core.network.controller.Node
import d.zhdanov.ccfit.nsu.core.network.controller.NodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeDestination
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger(NetworkStateMachine::class.java.name)
private val kPortRange = 1..65535


class NetworkStateMachine<MessageT, InboundMessageTranslatorT : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT>(
  private val netController: NetworkController<MessageT, InboundMessageTranslatorT, PayloadT>
) : NetworkStateHandler<MessageT, InboundMessageTranslatorT, PayloadT> {
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslatorT, PayloadT> =
    TODO()
  private val masterState = MasterState(
    this, netController, nodesHandler
  )
  private val activeState = ActiveStateHandler(
    this, netController, nodesHandler
  )
  private val passiveState = PassiveState(
    this, netController, nodesHandler
  )
  private val lobbyState = LobbyState(
    this, netController, nodesHandler
  )
  val msgTranslator = netController.messageTranslator

  @Volatile private var nodeId = 0
  private val state: AtomicReference<NetworkState<MessageT, InboundMessageTranslatorT, PayloadT>> =
    AtomicReference(lobbyState)

  private val curNetStateOrder = AtomicInteger()

  private val masterDeputy: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>> =
    AtomicReference()

  override fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)

  override fun joinHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().joinHandle(ipAddress, message, msgT)

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().pingHandle(ipAddress, message, msgT)


  override fun ackHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().ackHandle(ipAddress, message, msgT)


  override fun stateHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().stateHandle(ipAddress, message, msgT)


  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().roleChangeHandle(ipAddress, message, msgT)


  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().announcementHandle(ipAddress, message, msgT)


  override fun errorHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().errorHandle(ipAddress, message, msgT)


  override fun steerHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().steerHandle(ipAddress, message, msgT)


  override fun handleMasterDeath(
    master: Node<MessageT, InboundMessageTranslatorT, PayloadT>
  ) = state.get().handleMasterDeath(master)

  override fun handleNodeDetach(
    node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
  ) = state.get().handleNodeDetach(node)

  fun getP2PAck(
    message: MessageT, node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
  ): P2PMessage {
    val ack = AckMsg()
    val seq = netController.messageUtils.getMSGSeq(message)
    val p2pmsg = P2PMessage(seq, ack, nodeId, node.id)
    return p2pmsg
  }

  fun getP2PRoleChange(
    senderRole: NodeRole?,
    receiverRole: NodeRole?,
    senderId: Int,
    receiverId: Int,
    seq: Long
  ): P2PMessage {
    val roleChange = RoleChangeMsg(senderRole, receiverRole)
    val p2pMsg = P2PMessage(seq, roleChange, senderId, receiverId)
    return p2pMsg
  }

  /**
   * @return `Node<MessageT, InboundMessageTranslatorT, PayloadT>` if new
   * deputy was chosen successfully, else `null`
   */
  fun chooseSetNewDeputy(): Node<MessageT, InboundMessageTranslatorT,
    PayloadT>? {
    fun validDeputy(
      node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
    ): Boolean = node.running && node.nodeState == NodeT.NodeState.Active

    val (masterInfo, _) = masterDeputy.get()
    val node = nodesHandler.findNode(::validDeputy)
    val newDeputyInfo = node?.let { Pair(it.ipAddress, it.id) }
    masterDeputy.set(Pair(masterInfo, newDeputyInfo))
    return node
  }

  /**
   * @throws IllegalNodeDestination
   * */
  fun updateDeputyFromState(state: P2PMessage) {
    if(state.msg.type != MessageType.StateMsg) return
    val inp2p = state.msg as StateMsg
    val depStateInfo = inp2p.players.find { it.nodeRole == NodeRole.DEPUTY }
    val (msInfo, depInfo) = masterDeputy.get()
    if(depInfo?.second == depStateInfo?.id) return
    val newDepInfo = depStateInfo?.let {
      try {
        Pair(InetSocketAddress(it.ipAddress, it.port!!), it.id)
      } catch(e: Exception) {
        logger.error(e) { "deputy destination has dirty info" }
        throw IllegalNodeDestination(e)
      }
    }
    masterDeputy.set(Pair(msInfo, newDepInfo))
  }

  fun onPingMsg(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val node = nodesHandler.getNode(ipAddress)
    node ?: return
    val outp2p = getP2PAck(message, node)
    val outmsg = msgTranslator.toMessageT(
      outp2p, MessageType.AckMsg
    )
    netController.sendUnicast(outmsg, node.ipAddress)
  }

  fun onAckMsg(
    ipAddress: InetSocketAddress, message: MessageT
  ) {
    nodesHandler.getNode(ipAddress)?.ackMessage(message)
  }

  override fun initialize() {
    TODO("Not yet implemented")
  }

  override fun cleanup() {
    TODO("Not yet implemented")
  }
}