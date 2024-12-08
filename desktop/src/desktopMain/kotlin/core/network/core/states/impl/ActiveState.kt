package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.GameNodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.states.NetworkStateT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import java.net.InetSocketAddress

class ActiveState(
  private val stateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val gameNodesHandler: GameNodesHandler,
) : NetworkStateT {
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
    val inp2p = MessageTranslator.fromProto(message)
    if(inp2p.senderId == null || inp2p.receiverId == null) return

    val msDepInfo = stateMachine.masterDeputy.get() ?: return
    if(inp2p.senderId != msDepInfo.first.second) return

    val rlchn = inp2p.msg as RoleChangeMsg

    if(rlchn.receiverRole == NodeRole.VIEWER) {
      TODO("our snake dead, need go to lobby")
    } else if(rlchn.receiverRole == NodeRole.DEPUTY) {
      if(msDepInfo.second?.second == stateMachine.nodeId) return;
      val newDepInfo = EmptyAddress to stateMachine.nodeId
      val newMsDep = msDepInfo.first to newDepInfo
      do {
        val oldMsDepInfo = stateMachine.masterDeputy.get() ?: return
      } while(stateMachine.masterDeputy.compareAndSet(oldMsDepInfo, newMsDep))
    }
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

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    val (masterInfo, _) = stateMachine.masterDeputy.get() ?: return
    gameNodesHandler.getNode(masterInfo.first)?.let {
      val p2pmsg = GameMessage(stateMachine.nextSegNum, steerMsg)
      val outMsg = MessageTranslator.toGameMessage(p2pmsg, MessageType.SteerMsg)
      it.addMessageForAck(outMsg)
      controller.sendUnicast(outMsg, it.ipAddress)
    }
  }

  private fun initContext() {}

  override fun cleanup() {
    gameNodesHandler.shutdown()
  }
}