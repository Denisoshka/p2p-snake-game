package d.zhdanov.ccfit.nsu.core.network.nethandlers

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface UnicastNetworkHandler : NetworkHandler {
  fun sendUnicastMessage(
    message: SnakesProto.GameMessage, address: InetSocketAddress
  )
}