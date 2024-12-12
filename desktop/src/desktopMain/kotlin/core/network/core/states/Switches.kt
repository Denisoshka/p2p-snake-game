package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event

interface Switches {
  interface FromLobby {
    fun toMaster(
      changeAccessToken: Any,
      nodesHandler: ClusterNodesHandler,
      event: Event.State.ByController.LaunchGame
    )
    
    fun toActive(
      nodesHandler: ClusterNodesHandler,
      event: Event.State.ByInternal.JoinReqAck,
      changeAccessToken: Any,
    )
    
    fun toPassive(
      clusterNodesHandler: ClusterNodesHandler,
      event: Event.State.ByInternal.JoinReqAck,
      changeAccessToken: Any
    )
  }
  
  interface FromActive {
    fun toMaster(
      accessToken: Any,
      gameState: SnakesProto.GameMessage.StateMsg
    )
    
    fun toLobby(
      event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
    )
    
    fun toPassive(
      changeAccessToken: Any
    )
  }
  
  interface FromPassive {
    fun toLobby(
      event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
    )
  }
  
  interface FromMaster {
    fun toLobby(
      event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
    )
    
    fun toPassive(
      changeAccessToken: Any
    )
  }
}