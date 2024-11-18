package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import java.net.InetSocketAddress

interface NetworkStateHandler :
  NetworkState {
  enum class NetworkEvents {
    JoinAccepted,
    JoinRejected,
    NodeDeputyNow,
    NodeMasterNow,
    ShutdownContext,
  }

  fun sendUnicast(msg: GameMessage, nodeAddress: InetSocketAddress)
}