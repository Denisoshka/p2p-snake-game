package d.zhdanov.ccfit.nsu.core.network.controller

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageUtilsT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateCommandHandler
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}
private val PortRange = 0..65535


class NetworkController<MessageT, InboundMessageTranslatorT : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT>(
  contextJoinBacklog: Int,
  val messageUtils: MessageUtilsT<MessageT, MessageType>, val pingDelay: Long,
  val resendDelay: Long, val thresholdDelay: Long,
  private val messageTranslator: InboundMessageTranslatorT,
) : AutoCloseable {
  private val unicastNetHandler = UnicastNetHandler(this)
  private val multicastNetHandler =
    MulticastNetHandler<MessageT, InboundMessageTranslatorT, PayloadT>(
      TODO(), this
    )
  private val messageHandler: NetworkControllerStateMachine<MessageT, InboundMessageTranslatorT, PayloadT>

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

  class NetworkControllerStateMachine<MessageT, InboundMessageTranslatorT :
  MessageTranslatorT<MessageT>, PayloadT : NodePayloadT>(
    controller: NetworkController<MessageT, InboundMessageTranslatorT, PayloadT>
  ) :
    NetworkStateCommandHandler<MessageT, InboundMessageTranslatorT, PayloadT> {
    private val masterState =
      MasterStateHandler<MessageT, InboundMessageTranslatorT, PayloadT>(this)
    private val passiveState =
      PassiveStateHandler<MessageT, InboundMessageTranslatorT, PayloadT>(this)
    private val lobbyState =
      LobbyStateHandler<MessageT, InboundMessageTranslatorT, PayloadT>(this)
    private val activeState =
      ActiveStateHandler<MessageT, InboundMessageTranslatorT, PayloadT>(this)
    private val msgTranslator = controller.messageTranslator

    private val state: AtomicReference<NetworkStateCommandHandler<MessageT, InboundMessageTranslatorT, PayloadT>> =
      AtomicReference(lobbyState)

    private val curNetStateOrder = AtomicInteger()
    private val masterDeputy: AtomicReference<Pair<InetSocketAddress, InetSocketAddress>> =
      AtomicReference()

    override fun joinHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().joinHandle(ipAddress, message, msgT)

    override fun pingHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().pingHandle(ipAddress, message, msgT)


    override fun ackHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().ackHandle(ipAddress, message, msgT)


    override fun stateHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().stateHandle(ipAddress, message, msgT)


    override fun roleChangeHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().roleChangeHandle(ipAddress, message, msgT)


    override fun announcementHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().announcementHandle(ipAddress, message, msgT)


    override fun errorHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().errorHandle(ipAddress, message, msgT)


    override fun steerHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) = state.get().steerHandle(ipAddress, message, msgT)


    override fun handleMasterDeath(
      master: Node<MessageT, InboundMessageTranslatorT, PayloadT>
    ) = state.get().handleMasterDeath(master)


    override fun handleDeputyDeath(
      master: Node<MessageT, InboundMessageTranslatorT, PayloadT>
    ) = state.get().handleDeputyDeath(master)

    override fun handleNodeDetach(
      node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
    ) = state.get().handleNodeDetach(node)

    class MasterStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
      val networkControllerStateMachine: NetworkControllerStateMachine<MessageT, InboundMessageTranslator, Payload>
    ) :
      NetworkStateCommandHandler<MessageT, InboundMessageTranslator, Payload> {
      override fun joinHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        val msg = networkControllerStateMachine.msgTranslator.fromMessageT(
          message, msgT
        )
        val inMsg = msg.msg as JoinMsg
      }

      override fun pingHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {

      }

      override fun ackHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun stateHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun roleChangeHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun announcementHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun errorHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun steerHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun handleDeputyDeath(
        master: Node<MessageT, InboundMessageTranslator, Payload>
      ) {
        TODO("Not yet implemented")
      }

      override fun handleNodeDetach(
        node: Node<MessageT, InboundMessageTranslator, Payload>
      ) {
        TODO("Not yet implemented")
      }
    }

    class ActiveStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
      val networkControllerStateMachine: NetworkControllerStateMachine<MessageT, InboundMessageTranslator, Payload>
    ) :
      NetworkStateCommandHandler<MessageT, InboundMessageTranslator, Payload> {
      override fun joinHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun pingHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun ackHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun stateHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun roleChangeHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun announcementHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun errorHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun steerHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun handleMasterDeath(
        master: Node<MessageT, InboundMessageTranslator, Payload>
      ) {
        TODO("Not yet implemented")
      }
    }

    class LobbyStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
      networkControllerStateMachine: NetworkControllerStateMachine<MessageT, InboundMessageTranslator, Payload>
    ) :
      NetworkStateCommandHandler<MessageT, InboundMessageTranslator, Payload> {
      override fun joinHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun pingHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun ackHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun roleChangeHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun announcementHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun errorHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }
    }

    class PassiveStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
      val networkControllerStateMachine: NetworkControllerStateMachine<MessageT, InboundMessageTranslator, Payload>
    ) :
      NetworkStateCommandHandler<MessageT, InboundMessageTranslator, Payload> {

      override fun pingHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun ackHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun stateHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun roleChangeHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }

      override fun errorHandle(
        ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
      ) {
        TODO("Not yet implemented")
      }
    }
  }
}