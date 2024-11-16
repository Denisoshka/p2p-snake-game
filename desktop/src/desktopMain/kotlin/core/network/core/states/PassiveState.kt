package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.controller.NodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import java.net.InetSocketAddress

class PassiveState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
  private val controller: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
) : NetworkState<MessageT, InboundMessageTranslator, Payload> {

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
    TODO("Not yet implemented")
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }
}