package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState

interface MasterStateT : NodeState {
  fun toPassive(
    changeAccessToken: Any
  )
}