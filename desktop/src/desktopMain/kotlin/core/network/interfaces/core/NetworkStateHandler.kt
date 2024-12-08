package d.zhdanov.ccfit.nsu.core.network.interfaces.core

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
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
  fun joinToGame(announcement: GameAnnouncement, )

  fun sendUnicast(msg: GameMessage, nodeAddress: InetSocketAddress)
}