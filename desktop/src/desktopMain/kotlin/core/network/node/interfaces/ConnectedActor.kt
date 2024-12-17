package d.zhdanov.ccfit.nsu.core.network.states.abstr

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface ConnectedActor : BaseActor {
  fun submitSteerMsg(steerMsg: SnakesProto.GameMessage.SteerMsg)
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