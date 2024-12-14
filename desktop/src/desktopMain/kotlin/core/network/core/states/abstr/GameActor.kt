package d.zhdanov.ccfit.nsu.core.network.core.states.abstr

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface GameActor : BaseActor {
  fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun steerHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun stateHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
}