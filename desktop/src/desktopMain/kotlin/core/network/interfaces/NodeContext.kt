package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import core.network.core.Node
import java.net.InetSocketAddress

interface NodeContext<MessageT, InboundMessageTranslator :
MessageTranslatorT<MessageT>, Payload : NodePayloadT> {
  fun shutdown()

  fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  )

  fun addNewNode(
    ipAddress: InetSocketAddress, registerInContext: Boolean = true
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