package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event

interface MasterStateT : NodeState {
  fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  )
  
  fun toPassive(
    changeAccessToken: Any
  )
}