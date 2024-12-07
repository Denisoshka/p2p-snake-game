package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.nodes.NodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import java.net.InetSocketAddress

class LobbyState(
  private val ncStateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val nodesHandler: NodesHandler,
) : NetworkState {
  private val waitToJoin = HashMap<SnakesProto.GameMessage, GameConfig>()

  init {
    nodesHandler.launch()
  }

  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(ncStateMachine.networkState !is LobbyState) return

    val msg = ncStateMachine.onAckMsg(ipAddress, message) ?: return
    val p2pmsg = MessageTranslator.fromProto(msg, MessageType.JoinMsg)
  }

  fun submitJoinMsg(
    joinMsg: JoinMsg,
    address: InetSocketAddress,
    nodeId: Int,
    config: GameConfig
  ) {

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
    val msg = ncStateMachine.onAckMsg(ipAddress, message) ?: return
    val p2pmsg = MessageTranslator.fromProto(msg)
    TODO("wtf")
    nodesHandler[ipAddress]?.handleEvent(
      NodeT.NodeEvent.ShutdownNowFromCluster
    )
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {/*not handle in this state lol*/
  }

  override fun cleanup() {
  }
}