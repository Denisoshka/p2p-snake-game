package d.zhdanov.ccfit.nsu.core.network.core

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}
private val PortRange = 0..65535


class NetworkController(
) : AutoCloseable {
  private val unicastNetHandler = UnicastNetHandler(this)
  private val multicastNetHandler = MulticastNetHandler(TODO(), this)
  private val messageHandler: NetworkStateMachine

  fun handleUnicastMessage(
    message: GameMessage, ipAddress: InetSocketAddress
  ) {
    when(message.typeCase) {
      GameMessage.TypeCase.PING        -> messageHandler.pingHandle(
        ipAddress, message, MessageType.PingMsg
      )

      GameMessage.TypeCase.STEER       -> messageHandler.steerHandle(
        ipAddress, message, MessageType.SteerMsg
      )

      GameMessage.TypeCase.ACK         -> messageHandler.ackHandle(
        ipAddress, message, MessageType.AckMsg
      )

      GameMessage.TypeCase.STATE       -> messageHandler.stateHandle(
        ipAddress, message, MessageType.StateMsg
      )

      GameMessage.TypeCase.JOIN        -> messageHandler.joinHandle(
        ipAddress, message, MessageType.JoinMsg
      )

      GameMessage.TypeCase.ERROR       -> messageHandler.errorHandle(
        ipAddress, message, MessageType.ErrorMsg
      )

      GameMessage.TypeCase.ROLE_CHANGE -> messageHandler.roleChangeHandle(
        ipAddress, message, MessageType.RoleChangeMsg
      )

      else                             -> {}
    }
  }

  fun handleMulticastMessage(
    message: GameMessage, ipAddress: InetSocketAddress
  ) {
    when(message.typeCase) {
      GameMessage.TypeCase.ANNOUNCEMENT -> messageHandler.announcementHandle(
        ipAddress, message, MessageType.AnnouncementMsg
      )

      else                              -> {}
    }
  }

  fun sendUnicast(
    msg: GameMessage, nodeAddress: InetSocketAddress
  ) = unicastNetHandler.sendUnicastMessage(msg, nodeAddress)

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
  }
}