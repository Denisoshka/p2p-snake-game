package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState

interface ActiveStateT : NodeState {
  fun toMaster(
    accessToken: Any, gameState: SnakesProto.GameState?
  )
  
  fun toPassive(
    changeAccessToken: Any
  )
}