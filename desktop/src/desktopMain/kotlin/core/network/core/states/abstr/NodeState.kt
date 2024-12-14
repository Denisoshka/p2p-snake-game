package d.zhdanov.ccfit.nsu.core.network.core.states.abstr

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import java.net.InetSocketAddress


sealed interface NodeState {
  /**
   * @return next state if necessary
   * */
  fun atNodeDetachPostProcess(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    accessToken: Any
  ): NodeState?
  
  interface LobbyStateT : NodeState {
    fun toMaster(
      changeAccessToken: Any,
      nodesHandler: ClusterNodesHolder,
      event: Event.State.ByController.LaunchGame
    ): NodeState
    
    fun toActive(
      nodesHandler: ClusterNodesHolder,
      event: Event.State.ByInternal.JoinReqAck,
      changeAccessToken: Any,
    ): NodeState
    
    fun toPassive(
      clusterNodesHolder: ClusterNodesHolder,
      event: Event.State.ByInternal.JoinReqAck,
      changeAccessToken: Any
    ): NodeState
  }
  
  interface MasterStateT : NodeState {
    fun toPassive(
      changeAccessToken: Any
    ): NodeState
    
    fun toLobby(
      event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
    ): NodeState
  }
  
  interface PassiveStateT : NodeState {
    fun toLobby(
      event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
    ): NodeState
  }
  
  interface ActiveStateT : NodeState {
    fun toMaster(
      gameState: SnakesProto.GameState, accessToken: Any
    ): NodeState
    
    fun toPassive(
      changeAccessToken: Any
    ): NodeState
    
    fun toLobby(
      event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
    ): NodeState
  }
}
