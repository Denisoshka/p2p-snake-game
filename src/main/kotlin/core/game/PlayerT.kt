package d.zhdanov.ccfit.nsu.core.game

import d.zhdanov.ccfit.nsu.core.game.engine.core.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.core.entity.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

abstract class PlayerT(
   open val snake: Snake
) : Entity by snake {
  abstract fun update(steer: SteerMsg, seq: Long)
  abstract fun onObservedExpired()
}