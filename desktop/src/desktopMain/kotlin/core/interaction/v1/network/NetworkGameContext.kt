package d.zhdanov.ccfit.nsu.core.interaction.v1.network

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface NetworkGameContext {
  @Throws(NetworkGameContextException::class)
  fun submitNewPlayer(
    playerInfo: Pair<InetSocketAddress, SnakesProto.GameMessage>
  )
  
  fun submitSteerMsq(
    playerId: Int,
    msg: SnakesProto.GameMessage.SteerMsg,
    seq: Long
  ): Boolean
  
  fun launch()
  fun shutdown()
}
