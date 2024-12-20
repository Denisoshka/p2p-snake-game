package d.zhdanov.ccfit.nsu.core.game.core.entity.active

import d.zhdanov.ccfit.nsu.core.game.core.entity.Entity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction

interface ActiveEntity : Entity {
  val id: Int
  fun changeState(direction: Direction)
}