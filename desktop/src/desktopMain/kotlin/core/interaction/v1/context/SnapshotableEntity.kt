package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface SnapshotableEntity {
  fun shootState(state: StateMsg)
  fun restoreHitbox(offsets: List<Coord>)
}