package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.PassiveStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import java.net.InetSocketAddress

class PassiveState(
	val nodeId: Int,
	override val gameConfig: InternalGameConfig,
	private val stateMachine: NetworkStateHolder,
	private val controller: NetworkController,
	private val clusterNodesHandler: ClusterNodesHandler,
) : PassiveStateT, GameStateT {
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = stateMachine.onPingMsg(ipAddress, message, nodeId)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = stateMachine.nonLobbyOnAck(ipAddress, message, msgT)
  
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
    node: ClusterNodeT
  ) {
    TODO("Not yet implemented")
  }
  
  /**
   * @throws IllegalChangeStateAttempt
   * */
  private suspend fun passiveHandleNodeDetach(
    st: PassiveState, node: ClusterNodeT
  ) {
    stateMachine.apply {
      val (msInfo, depInfo) = stateMachine.masterDeputy ?: return
      if(msInfo.second != node.nodeId) throw IllegalChangeStateAttempt(
        "non master node $node in passiveHandleNodeDetach"
      )
      
      if(depInfo == null) {
        reconfigureContext(Event.ControllerEvent.SwitchToLobby)
      } else {
        normalChangeInfoDeputyToMaster(depInfo, node)
      }
    }
  }
}