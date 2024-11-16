package d.zhdanov.ccfit.nsu.core.interaction.v1

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

interface NodePayloadT {
  fun handleEvent(event: SteerMsg, seq: Long)
  fun onObserverTerminated()
}