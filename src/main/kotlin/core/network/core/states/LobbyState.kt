package core.network.core.states

import core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.controller.NodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import java.net.InetSocketAddress

class LobbyState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
  private val controller: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
) : NetworkState<MessageT, InboundMessageTranslator, Payload> {
  override fun ackHandle(
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