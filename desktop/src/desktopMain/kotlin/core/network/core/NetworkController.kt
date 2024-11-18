package d.zhdanov.ccfit.nsu.core.network.core

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageUtilsT
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}
private val PortRange = 0..65535


class NetworkController<MessageT, InboundMessageTranslatorT : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT>(
  val messageUtils: MessageUtilsT<MessageT, MessageType>,
  val pingDelay: Long,
  val messageTranslator: InboundMessageTranslatorT,
) : AutoCloseable {
  private val unicastNetHandler = UnicastNetHandler(this)
  private val multicastNetHandler = MulticastNetHandler(TODO(), this)
  private val messageHandler: NetworkStateMachine<MessageT, InboundMessageTranslatorT, PayloadT>

  fun handleUnicastMessage(
    message: MessageT, ipAddress: InetSocketAddress
  ) {
    val msgT = messageTranslator.getMessageType(message)
    when(msgT) {
      MessageType.PingMsg       -> messageHandler.pingHandle(
        ipAddress, message, MessageType.PingMsg
      )

      MessageType.SteerMsg      -> messageHandler.steerHandle(
        ipAddress, message, MessageType.SteerMsg
      )

      MessageType.AckMsg        -> messageHandler.ackHandle(
        ipAddress, message, MessageType.AckMsg
      )

      MessageType.StateMsg      -> messageHandler.stateHandle(
        ipAddress, message, MessageType.StateMsg
      )

      MessageType.JoinMsg       -> messageHandler.joinHandle(
        ipAddress, message, MessageType.JoinMsg
      )

      MessageType.ErrorMsg      -> messageHandler.errorHandle(
        ipAddress, message, MessageType.ErrorMsg
      )

      MessageType.RoleChangeMsg -> messageHandler.roleChangeHandle(
        ipAddress, message, MessageType.RoleChangeMsg
      )

      else                      -> {}
    }
  }

  fun handleMulticastMessage(
    message: MessageT, ipAddress: InetSocketAddress
  ) {
    val msgT = messageTranslator.getMessageType(message)
    when(msgT) {
      MessageType.AnnouncementMsg -> messageHandler.announcementHandle(
        ipAddress, message, MessageType.AnnouncementMsg
      )

      else                        -> {}
    }
  }

  fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) = unicastNetHandler.sendUnicastMessage(msg, nodeAddress)

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
  }
}