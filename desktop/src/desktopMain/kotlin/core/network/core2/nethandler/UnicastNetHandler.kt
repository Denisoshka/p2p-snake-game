package d.zhdanov.ccfit.nsu.core.network.core2.nethandler

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface UnicastNetHandler : NetworkHandler {
  fun sendUnicastMessage(
    message: SnakesProto.GameMessage, address: InetSocketAddress
  )
}