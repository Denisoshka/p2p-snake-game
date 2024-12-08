package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.ActiveStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvents
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(ActiveState::class.java.name)

class ActiveState(
  private val stateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val clusterNodesHandler: ClusterNodesHandler,
) : ActiveStateT {
  override fun joinHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun pingHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateMachine.onPingMsg(ipAddress, message, msgT)

  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    stateMachine.onAckMsg(ipAddress, message)
  }

  override fun stateHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateMachine.onStateMsg(ipAddress, message)

  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(!MessageUtils.RoleChangeIdentifier.correctRoleChangeMsg(message)) {
      Logger.debug {
        "incorrect typeCase ${
          message.typeCase
        } has receiverId ${
          message.hasReceiverId()
        } has senderId ${
          message.hasSenderId()
        } "
      }
      return
    }

    if(MessageUtils.RoleChangeIdentifier.fromDeputyDeputyMasterNow(message)) {
      /**not handle wait master dead*/
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterPlayerDead(message)) {
      atFromMasterPlayerDead(message)
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterNodeDeputyNow(message)) {
      atFromMasterNodeDeputyNow(message)
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterNodeMasterNow(message)) {
      atFromMasterNodeMasterNow(message)
    } else {
      Logger.debug {
        "irrelevant ${
          message.typeCase
        } receiverRole : ${
          message.roleChange.receiverRole
        } senderRole : ${
          message.roleChange.senderRole
        }"
      }
    }
  }

  private fun atFromMasterNodeDeputyNow(message: SnakesProto.GameMessage) {
    val (ms, _) = stateMachine.masterDeputy ?: return
    if(ms.second != message.senderId) return

  }

  private fun atFromMasterPlayerDead(message: SnakesProto.GameMessage) {
    val (ms, _) = stateMachine.masterDeputy ?: return
    if(ms.second != message.senderId) return
    stateMachine.changeState(StateEvents.ControllerEvent.SwitchToLobby)
  }

  override fun announcementHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  fun submitSteerMsg(steerMsg: SteerMsg) {
    val (masterInfo, _) = stateMachine.masterDeputy.get() ?: return
    clusterNodesHandler.getNode(masterInfo.first)?.let {
      val p2pmsg = GameMessage(stateMachine.nextSegNum, steerMsg)
      val outMsg = MessageTranslator.toGameMessage(p2pmsg, MessageType.SteerMsg)
      it.addMessageForAck(outMsg)
      controller.sendUnicast(outMsg, it.ipAddress)
    }
  }

  private fun initContext() {}

  override fun cleanup() {
    clusterNodesHandler.shutdown()
  }
}