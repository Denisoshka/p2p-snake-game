package core.network.core

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
  private val msgTranslator = netController.messageTranslator

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

  private fun getP2PAck(
    message: MessageT, node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
  ): P2PMessage {
    val ack = AckMsg()
    val seq = netController.messageUtils.getMSGSeq(message)
    val p2pmsg = P2PMessage(seq, ack, nodeId, node.id)
    return p2pmsg
  }

  private fun getP2PRoleChange(
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
  private fun chooseSetNewDeputy(): Node<MessageT, InboundMessageTranslatorT, PayloadT>? {
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
  private fun updateDeputyFromState(state: P2PMessage) {
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

  class MasterState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
    private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
    private val netController: NetworkController<MessageT, InboundMessageTranslator, Payload>,
    private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
  ) : NetworkState<MessageT, InboundMessageTranslator, Payload> {
    override fun joinHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      val request = ncStateMachine.msgTranslator.fromMessageT(
        message, msgT
      )
      val inMsg = request.msg as JoinMsg
      if(inMsg.playerName.isBlank()) {
        logger.atTrace { "$ipAddress player name empty" }
        return
      }
      if(inMsg.nodeRole != NodeRole.VIEWER || inMsg.nodeRole != NodeRole.NORMAL) {
        logger.atTrace { "$ipAddress invalid node role: ${inMsg.nodeRole}{}" }
        return
      }
      val node = ncStateMachine.nodesHandler.getNode(ipAddress)?.let {
        return
      }
    }

    override fun pingHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      val node = nodesHandler.getNode(ipAddress)
      node ?: return
      val outp2p = ncStateMachine.getP2PAck(message, node)
      val outmsg = ncStateMachine.msgTranslator.toMessageT(
        outp2p, MessageType.AckMsg
      )
      netController.sendUnicast(outmsg, node.ipAddress)
    }

    override fun ackHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      ncStateMachine.nodesHandler.getNode(ipAddress)?.ackMessage(message)
    }

    override fun roleChangeHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      val node = nodesHandler.getNode(ipAddress) ?: return
      if(!node.running) return
      val inp2p = netController.messageTranslator.fromMessageT(
        message, MessageType.RoleChangeMsg
      )
      (inp2p.msg as RoleChangeMsg).let {
        if(it.senderRole != NodeRole.VIEWER && it.receiverRole != null) return
      }
      val outp2p = ncStateMachine.getP2PAck(message, node)
      val outmsg = ncStateMachine.msgTranslator.toMessageT(
        outp2p, MessageType.AckMsg
      )
      netController.sendUnicast(outmsg, ipAddress)
      node.addMessageForAck(outmsg)
      node.handleEvent(NodeT.NodeEvent.ShutdownFromCluster)
    }

    override fun steerHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      val node = nodesHandler.getNode(ipAddress) ?: return
      if(!node.running) return
      val inp2p = netController.messageTranslator.fromMessageT(
        message, MessageType.SteerMsg
      )
      node.payloadT?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
    }


    override fun handleNodeDetach(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ) {
      val (msInfo, depInfo) = ncStateMachine.masterDeputy.get()
      depInfo?.let {
        if(node.id == it.second) onDeputyDetach(msInfo)
      }
    }

    private fun onDeputyDetach(msInfo: Pair<InetSocketAddress, Int>) {
      val newDepInfo = ncStateMachine.chooseSetNewDeputy()?.run {
        return@run Pair(this.ipAddress, this.id)
      }
      ncStateMachine.masterDeputy.set(Pair(msInfo, newDepInfo))
    }
  }

  class ActiveStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
    private val networkStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>
    private val controller: NetworkController<MessageT, InboundMessageTranslator, Payload>,
    private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
  ) : NetworkState<MessageT, InboundMessageTranslator, Payload> {
    override fun joinHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun pingHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun ackHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun stateHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {

      TODO("Not yet implemented")
    }

    override fun roleChangeHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun announcementHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun errorHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun steerHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun handleMasterDeath(
      master: Node<MessageT, InboundMessageTranslator, Payload>
    ) {
      TODO("Not yet implemented")
    }
  }

  class LobbyState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
    private val stateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>
    private val controller: NetworkController<MessageT, InboundMessageTranslator, Payload>,
    private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
  ) : NetworkState<MessageT, InboundMessageTranslator, Payload> {
    override fun joinHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun pingHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun ackHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun roleChangeHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun announcementHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun errorHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }
  }

  class PassiveState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
    private val networkStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
    private val controller: NetworkController<MessageT, InboundMessageTranslator, Payload>,
    private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
  ) : NetworkState<MessageT, InboundMessageTranslator, Payload> {

    override fun pingHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun ackHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun stateHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun roleChangeHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    override fun errorHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }
  }
}