package d.zhdanov.ccfit.nsu.core.network.core

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeContext
import d.zhdanov.ccfit.nsu.core.network.logger
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.utils.ContextNodeFabricT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
import java.net.InetSocketAddress

class P2PContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  contextJoinBacklog: Int,
  val messageUtils: MessageUtilsT<MessageT, MessageType>,
  val pingDelay: Long,
  val resendDelay: Long,
  val thresholdDelay: Long,
  private val unicastNetHandler: UnicastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
  private val multicastNetHandler: MulticastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
  private val messageTranslator: InboundMessageTranslator,
  private val contextNodeFabric: ContextNodeFabricT<MessageT, InboundMessageTranslator>
) : AutoCloseable {
  init {
    unicastNetHandler.configure(this)
    multicastNetHandler.configure(this)
  }

  var nodes: NodeContext<MessageT, InboundMessageTranslator>
  private val commandHandler = CommandHandler(this)

  fun initContext(master: InetSocketAddress?) {
    nodes = master?.run {
      return@run NodesHandler<>
    } ?: run {
      return@run NodesHandler<>
    }
  }

  fun destroyContext(
  ) {
  }

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
  }

  fun handleUnicastMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    when(val msgT = messageTranslator.getMessageType(message)) {
      MessageType.PingMsg       -> commandHandler.pingHandle(
        inetSocketAddress, message
      )

      MessageType.AckMsg        -> commandHandler.ackHandle(
        inetSocketAddress, message
      )

      MessageType.StateMsg      -> commandHandler.stateHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.JoinMsg       -> commandHandler.joinHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.SteerMsg      -> commandHandler.steerHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.ErrorMsg      -> commandHandler.errorHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.RoleChangeMsg -> commandHandler.roleChangeHandle(
        inetSocketAddress, message, msgT
      )

      else                      -> logger.info {
        "$msgT from $inetSocketAddress not handle as unicast command"
      }
    }
  }

  fun handleMulticastMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    when(val msgT = messageTranslator.getMessageType(message)) {
      MessageType.AnnouncementMsg -> commandHandler.announcementHandle(
        inetSocketAddress, message, msgT
      )

      else                        -> logger.debug {
        "$msgT from $inetSocketAddress not handle as unicast command"
      }
    }
  }

  /**
   * send message without add for acknowledgement monitoring
   * */
  fun sendUnicast(
    msg: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) = unicastNetHandler.sendUnicastMessage(msg, node.address)

  class CommandHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
    private val context: P2PContext<MessageT, InboundMessageTranslator>,
  ) {
    private val translator = context.messageTranslator

    fun joinHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
    }

    fun pingHandle(
      inetSocketAddress: InetSocketAddress, message: MessageT
    ) {
    }

    fun ackHandle(
      inetSocketAddress: InetSocketAddress, message: MessageT
    ) {
    }

    fun stateHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
    }

    fun roleChangeHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
    }

    fun announcementHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
    }

    fun errorHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
    }

    fun steerHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
    }
  }
}