package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.GameNodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.core.NetworkState
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import java.net.InetSocketAddress

class LobbyState(
  private val ncStateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val gameNodesHandler: GameNodesHandler,
) : NetworkState {
  private val waitToJoin = HashMap<SnakesProto.GameMessage, GameConfig>()

  init {
    gameNodesHandler.launch()
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
    gameNodesHandler[ipAddress]?.handleEvent(
      NodeT.NodeEvent.ShutdownNowFromCluster
    )
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {/*not handle in this state lol*/
  }

  override fun cleanup() {
  }
}