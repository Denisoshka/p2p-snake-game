package d.zhdanov.ccfit.nsu.core.network.nethandlers

import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import java.net.InetSocketAddress

interface UnicastNetworkHandler<MessageT, InboundMessageTranslatorT : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT> :
  NetworkHandler<MessageT, InboundMessageTranslatorT, PayloadT> {
  fun sendUnicastMessage(message: MessageT, address: InetSocketAddress)
}