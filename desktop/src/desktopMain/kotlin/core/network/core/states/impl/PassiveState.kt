package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.PassiveStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import java.net.InetSocketAddress

class PassiveState(
  val nodeId: Int,
  override val gameConfig: InternalGameConfig,
  private val stateHolder: NetworkStateHolder,
  private val clusterNodesHandler: ClusterNodesHandler,
) : PassiveStateT, GameStateT {
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = stateHolder.onPingMsg(ipAddress, message, nodeId)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = stateHolder.nonLobbyOnAck(ipAddress, message, msgT)
  
  override fun stateHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
  }
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }
  
  override fun steerHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }
  
  override fun cleanup() {
    clusterNodesHandler.shutdown()
  }
  
  override suspend fun handleNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>,
    changeAccessToken: Any
  ) {
    TODO("Not yet implemented")
  }
}