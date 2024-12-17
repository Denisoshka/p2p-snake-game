package d.zhdanov.ccfit.nsu.core.network.states.abstr

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface BaseActor {
  fun pingHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun ackHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun errorHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun announcementHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
}