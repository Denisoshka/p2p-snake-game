package d.zhdanov.ccfit.nsu.core.network.interfaces

import java.net.InetSocketAddress

interface UnicastNetworkHandler<MessageT> : NetworkHandler {
  fun sendMessage(message: MessageT, address: InetSocketAddress)
}