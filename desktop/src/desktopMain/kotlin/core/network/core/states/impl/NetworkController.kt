package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import java.net.InetSocketAddress

class NetworkController(
) : AutoCloseable {
  private val unicastNetHandler = UnicastNetHandler(this)
  private val multicastNetHandler = MulticastNetHandler(TODO(), this)
  private val messageHandler: NetworkStateHolder

  fun handleUnicastMessage(
    message: SnakesProto.GameMessage, ipAddress: InetSocketAddress
  ) {
    when(message.typeCase) {
      SnakesProto.GameMessage.TypeCase.PING        -> messageHandler.pingHandle(
        ipAddress, message, MessageType.PingMsg
      )

      SnakesProto.GameMessage.TypeCase.STEER       -> messageHandler.steerHandle(
        ipAddress, message, MessageType.SteerMsg
      )

      SnakesProto.GameMessage.TypeCase.ACK         -> messageHandler.ackHandle(
        ipAddress, message, MessageType.AckMsg
      )

      SnakesProto.GameMessage.TypeCase.STATE       -> messageHandler.stateHandle(
        ipAddress, message, MessageType.StateMsg
      )

      SnakesProto.GameMessage.TypeCase.JOIN        -> messageHandler.joinHandle(
        ipAddress, message, MessageType.JoinMsg
      )

      SnakesProto.GameMessage.TypeCase.ERROR       -> messageHandler.errorHandle(
        ipAddress, message, MessageType.ErrorMsg
      )

      SnakesProto.GameMessage.TypeCase.ROLE_CHANGE -> messageHandler.roleChangeHandle(
        ipAddress, message, MessageType.RoleChangeMsg
      )

      else                             -> {}
    }
  }

  fun handleMulticastMessage(
    message: SnakesProto.GameMessage, ipAddress: InetSocketAddress
  ) {
    when(message.typeCase) {
      SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT -> messageHandler.announcementHandle(
        ipAddress, message, MessageType.AnnouncementMsg
      )

      else                              -> {}
    }
  }

  fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = unicastNetHandler.sendUnicastMessage(msg, nodeAddress)

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
  }
}