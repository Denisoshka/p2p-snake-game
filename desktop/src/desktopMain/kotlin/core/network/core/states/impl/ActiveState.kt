package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import java.net.InetSocketAddress

class ActiveState(
  private val stateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val nodesHandler: NodesHandler,
) : NetworkState {
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  ) = stateMachine.onPingMsg(ipAddress, message, msgT)

  override fun ackHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  ) = stateMachine.onAckMsg(ipAddress, message)

  override fun stateHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  ) = stateMachine.onStateMsg(ipAddress, message)

  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
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
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun handleMasterDeath(
    master: Node
  ) {
    TODO("Not yet implemented")
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    val (masterInfo, _) = stateMachine.masterDeputy.get() ?: return
    nodesHandler.getNode(masterInfo.first)?.let {
      val p2pmsg = P2PMessage(stateMachine.nextSegNum, steerMsg)
      val outMsg = MessageTranslator.toMessageT(p2pmsg, MessageType.SteerMsg)
      it.addMessageForAck(outMsg)
      controller.sendUnicast(outMsg, it.ipAddress)
    }
  }

  override fun initialize() {
    TODO("Not yet implemented")
  }

  override fun cleanup() {
    TODO("Not yet implemented")
  }
}