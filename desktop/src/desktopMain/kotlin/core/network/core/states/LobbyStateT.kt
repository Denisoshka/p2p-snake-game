package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event

interface LobbyStateT : NodeState {
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