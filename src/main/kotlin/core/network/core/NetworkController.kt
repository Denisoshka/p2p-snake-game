package d.zhdanov.ccfit.nsu.core.network.controller

import core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.*
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageUtilsT
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}
private val PortRange = 0..65535


class NetworkController<MessageT, InboundMessageTranslatorT : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT>(
  contextJoinBacklog: Int,
  val messageUtils: MessageUtilsT<MessageT, MessageType>, val pingDelay: Long,
  val resendDelay: Long, val thresholdDelay: Long,
  val messageTranslator: InboundMessageTranslatorT,
) : AutoCloseable {
  private val unicastNetHandler = UnicastNetHandler(this)
  private val multicastNetHandler =
    MulticastNetHandler<MessageT, InboundMessageTranslatorT, PayloadT>(
      TODO(), this
    )
  private val messageHandler: NetworkStateMachine<MessageT, InboundMessageTranslatorT, PayloadT>

  /*
  * Храним deputy и msgSeq
  **/
  fun handleUnicastMessage(
    inboundMsg: MessageT, ipAddress: InetSocketAddress
  ) {
  }

  fun handleMulticastMessage(
    message: MessageT, ipAddress: InetSocketAddress
  ) {
  }

  /**
   * В текущей версии протокола не может быть ситуации когда у master умер
   * Deputy и отправилось RoleChangeMsg c msgSeq меньше чем State в условиях
   * конкурентности
   *
   * `Тогда он выбирает нового DEPUTY среди NORMAL-ов, и сообщает об этом самому
   * DEPUTY сообщением RoleChangeMsg (остальные узнают о новом DEPUTY из
   * планового StatusMsg, им это знать не срочно).`*/
  private fun checkPreconditionsChangeDeputy(
    deputy: GamePlayer, msgSeq: Long
  ) {
    val ipAddress = deputy.ipAddress
    val port = deputy.port
    if(port == null || port !in PortRange || ipAddress.isNullOrBlank()) {
      logger.warn { "deputy info is dirty ${deputy.ipAddress} ${deputy.port}" }
      return
    }
    val (currDep, _) = deputyInfoAndStateOrder.get()
    currDep?.run {
      if(this.second == deputy.id) return
    }

    do {
      val prevInfo = deputyInfoAndStateOrder.get()
      if(prevInfo.second > msgSeq) return
      val newDeputy = InetSocketAddress(ipAddress, port) to deputy.id
      val newDeputyInfo = newDeputy to msgSeq
    } while(!deputyInfoAndStateOrder.compareAndSet(prevInfo, newDeputyInfo))
  }

  private fun checkPreconditionsSetDeputyNull(msgSeq: Long) {
    do {
      val prevInfo = deputyInfoAndStateOrder.get()
      if(prevInfo.second > msgSeq) return
      val newInfo = null to msgSeq
    } while(!deputyInfoAndStateOrder.compareAndSet(prevInfo, newInfo))
  }

  private fun checkDeputyInState(
    players: MutableList<GamePlayer>, msgSeq: Long
  ) {
    val deputy: GamePlayer? = players.run {
      for(pl in this) {
        if(pl.nodeRole == NodeRole.DEPUTY) return@run pl
      }
      return@run null
    }

    deputy?.let {
      checkPreconditionsChangeDeputy(it, msgSeq)
    } ?: run {
      checkPreconditionsSetDeputyNull(msgSeq)
    }
  }

  private fun handleInboundState(msg: P2PMessage) {
    if(msg.msgSeq <= curNetworkStateOrder.get()) return

    val state = msg.msg as StateMsg
    checkDeputyInState(state.players, msg.msgSeq)

  }

  fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) = unicastNetHandler.sendUnicastMessage(msg, nodeAddress)

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
  }
}