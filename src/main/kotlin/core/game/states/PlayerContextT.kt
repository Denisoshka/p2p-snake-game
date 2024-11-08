package d.zhdanov.ccfit.nsu.core.game.states

import d.zhdanov.ccfit.nsu.core.game.engine.core.entity.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

interface PlayerContextT {
  val snake: Snake
  fun update(steer: SteerMsg, seq: Long)
  fun observedIsDead()
}