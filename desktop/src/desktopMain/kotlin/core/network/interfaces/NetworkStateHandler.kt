package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import java.net.InetSocketAddress

interface NetworkStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT> :
  NetworkState<MessageT, InboundMessageTranslator, Payload> {
  fun sendUnicast(msg: MessageT, nodeAddress: InetSocketAddress)
  fun onEvent()
}