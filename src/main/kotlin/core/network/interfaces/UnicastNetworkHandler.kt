package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import java.net.InetSocketAddress

interface UnicastNetworkHandler<
    MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>
    > : NetworkHandler<MessageT, InboundMessageTranslator> {
  fun sendMessage(message: MessageT, address: InetSocketAddress)
}