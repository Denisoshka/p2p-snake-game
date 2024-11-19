package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import java.net.InetSocketAddress
import java.util.*

class LobbyState(
  private val ncStateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val nodesHandler: NodesHandler,
) : NetworkState {
  private val waitToJoin = HashMap<GameMessage, GameConfig>()

  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    if(ncStateMachine.networkState !is LobbyState) return

    val msg = ncStateMachine.onAckMsg(ipAddress, message) ?: return
    val p2pmsg = MessageTranslator.fromProto(msg, MessageType.JoinMsg)

    synchronized(waitToJoin) {
      if(waitToJoin.isEmpty()) return

      val config = waitToJoin[msg] ?: return
      waitToJoin.clear()
      p2pmsg.msg as JoinMsg

      if(p2pmsg.msg.nodeRole == NodeRole.VIEWER) {
        ncStateMachine.changeState(NetworkStateChangeEvents.MasterNow)
      } else if(p2pmsg.msg.nodeRole == NodeRole.NORMAL) {
        ncStateMachine.changeState(NetworkStateChangeEvents.LaunchGame)
      }
    }
  }

  fun submitJoinMsg(
    joinMsg: JoinMsg,
    address: InetSocketAddress,
    nodeId: Int,
    config: GameConfig
  ) {
    if(ncStateMachine.networkState !is LobbyState) return

    val seq = ncStateMachine.nextSegNum
    val node = nodesHandler.addNewNode(
      seq, NodeRole.MASTER, nodeId, address, false
    )
    val outp2pmsg = P2PMessage(seq, joinMsg, null, nodeId)
    val out = MessageTranslator.toMessageT(outp2pmsg, MessageType.JoinMsg)
    node.addMessageForAck(out)
    waitToJoin[address] = config
    ncStateMachine.sendUnicast(out, address)
  }

  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {

    TODO("Not yet implemented")
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    val msg = ncStateMachine.onAckMsg(ipAddress, message) ?: return
    val p2pmsg = MessageTranslator.fromProto(msg)
    nodesHandler.getNode(ipAddress)?.handleEvent(
      NodeT.NodeEvent.ShutdownNowFromCluster
    )
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {/*not handle in this state lol*/
  }


  override fun initialize() {
    ab()
  }

  override fun cleanup() {
    ab()
  }

  private fun ab() {
    ncStateMachine.latestGameState.set(null)
    ncStateMachine.masterDeputy.set(null)
    synchronized(waitToJoin) {
      waitToJoin.clear()
    }
  }

}