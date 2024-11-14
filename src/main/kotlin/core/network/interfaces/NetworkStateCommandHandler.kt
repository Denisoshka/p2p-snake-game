package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.controller.Node
import java.net.InetSocketAddress

interface NetworkStateCommandHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT> {
  fun joinHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun pingHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun ackHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun stateHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun announcementHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun errorHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun steerHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
  }

  fun handleMasterDeath(
    master: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
  }

  fun handleDeputyDeath(
    master: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
  }

  fun handleNodeDetach(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  ) {
  }

  fun initState() {}
}