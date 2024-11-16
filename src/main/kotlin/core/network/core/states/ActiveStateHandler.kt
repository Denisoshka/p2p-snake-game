package core.network.core.states

import core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.controller.Node
import d.zhdanov.ccfit.nsu.core.network.controller.NodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import java.net.InetSocketAddress

class ActiveStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
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
  ) = ncStateMachine.onPingMsg(ipAddress, message, msgT)

  override fun ackHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = ncStateMachine.onAckMsg(ipAddress, message)

  override fun stateHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val inP2PMsg = ncStateMachine.msgTranslator.fromMessageT(message)
    val (master, )
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

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    val (masterInfo,  _) = ncStateMachine.masterDeputy.get()
    controller.sendUnicast()
    TODO("Not yet implemented")
  }

  override fun initialize() {
    TODO("Not yet implemented")
  }

  override fun cleanup() {
    TODO("Not yet implemented")
  }
}