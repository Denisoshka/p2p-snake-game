package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.controller.Node
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import java.net.InetSocketAddress

interface NodeContext<MessageT, InboundMessageTranslator :
MessageTranslatorT<MessageT>, Payload : NodePayloadT> {
  fun shutdown()

  fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  )

  fun addNewNode(
    ipAddress: InetSocketAddress
  ): Node<MessageT, InboundMessageTranslator, Payload>

  suspend fun handleNodeRegistration(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  )

  suspend fun handleNodeTermination(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  )

  suspend fun handleNodeDetachPrepare(
    node: Node<MessageT, InboundMessageTranslator, Payload>
  )
}