package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import java.net.InetSocketAddress

interface NetworkStateT {
  fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun pingHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun ackHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun stateHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun announcementHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun errorHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun steerHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  )

  fun cleanup()
}
