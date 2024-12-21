package d.zhdanov.ccfit.nsu.core.network.core2.states

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import java.net.InetSocketAddress


sealed interface NodeState {
  /**
   * @return next state if necessary
   * */
  fun atNodeDetachPostProcess(
    node: ClusterNode,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
  ): NodeState?
  
  interface LobbyState : NodeState {
    fun toMaster(
      nodesHolder: ClusterNodesHolder,
      event: Event.State.ByController.LaunchGame
    ): NodeState
    
    fun toActive(
      nodesHolder: ClusterNodesHolder,
      event: Event.State.ByInternal.JoinReqAck,
    ): NodeState
    
    fun toPassive(
      nodesHolder: ClusterNodesHolder,
      event: Event.State.ByInternal.JoinReqAck,
    ): NodeState
  }
  
  interface MasterState : NodeState {
    fun toPassive(): NodeState
    fun toLobby(event: Event.State.ByController.SwitchToLobby): NodeState
  }
  
  interface PassiveState : NodeState {
    fun toLobby(event: Event.State.ByController.SwitchToLobby): NodeState
  }
  
  interface ActiveState : NodeState {
    fun toMaster(gameState: SnakesProto.GameState): NodeState
    fun toPassive(): NodeState
    fun toLobby(event: Event.State.ByController.SwitchToLobby): NodeState
  }
}
