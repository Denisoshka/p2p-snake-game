package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import java.net.InetSocketAddress

class ActiveState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
  private val controller: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
) : NetworkState<MessageT, InboundMessageTranslator, Payload> {
  private val translator = ncStateMachine.msgTranslator
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = ncStateMachine.onPingMsg(ipAddress, message, msgT)

  override fun ackHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = ncStateMachine.onAckMsg(ipAddress, message)

  override fun stateHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = ncStateMachine.onStateMsg(ipAddress, message)

  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val inp2p = translator.fromMessageT(message)
    if(inp2p.senderId == null || inp2p.receiverId == null) return

    val msDepInfo = ncStateMachine.masterDeputy.get() ?: return
    if(inp2p.senderId != msDepInfo.first.second) return

    val rlchn = inp2p.msg as RoleChangeMsg

    if(rlchn.receiverRole == NodeRole.VIEWER) {
      TODO("our snake dead, need go to lobby")
    } else if(rlchn.receiverRole == NodeRole.DEPUTY) {
      if(msDepInfo.second?.second == ncStateMachine.nodeId) return;
      val newDepInfo = ncStateMachine.emptyAddress to ncStateMachine.nodeId
      val newMsDep = msDepInfo.first to newDepInfo
      do {
        val oldMsDepInfo = ncStateMachine.masterDeputy.get() ?: return
      } while(ncStateMachine.masterDeputy.compareAndSet(oldMsDepInfo, newMsDep))
    }
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

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    val (masterInfo, _) = ncStateMachine.masterDeputy.get() ?: return
    val master = nodesHandler.getNode(masterInfo.first)?.let {
      val p2pmsg = P2PMessage()
      it.addMessageForAck()
      controller.sendUnicast()
    }
    TODO("Not yet implemented")
  }

  override fun initialize() {
    TODO("Not yet implemented")
  }

  override fun cleanup() {
    TODO("Not yet implemented")
  }
}