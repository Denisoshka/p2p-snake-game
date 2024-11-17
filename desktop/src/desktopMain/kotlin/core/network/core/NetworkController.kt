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
  val messageUtils: MessageUtilsT<MessageT, MessageType>, val pingDelay: Long,
  val messageTranslator: InboundMessageTranslatorT,
) : AutoCloseable {
  private val unicastNetHandler = UnicastNetHandler(this)
  private val multicastNetHandler = MulticastNetHandler(TODO(), this)
  private val messageHandler: NetworkStateMachine<MessageT, InboundMessageTranslatorT, PayloadT>

  fun handleUnicastMessage(
    inboundMsg: MessageT, ipAddress: InetSocketAddress
  ) {
  }

  fun handleMulticastMessage(
    message: MessageT, ipAddress: InetSocketAddress
  ) {
  }

  fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) = unicastNetHandler.sendUnicastMessage(msg, nodeAddress)

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
  }
}