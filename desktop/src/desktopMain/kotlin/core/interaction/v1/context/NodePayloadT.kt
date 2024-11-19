package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

interface NodePayloadT {
  fun handleEvent(event: SteerMsg, seq: Long)
  fun onContextObserverTerminated()
  fun shootNodeState(state: StateMsg)
}