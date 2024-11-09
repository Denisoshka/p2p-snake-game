package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.entity.stardart.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

abstract class PlayerT(
  val name: String,
  val snake: Snake,
) : Entity by snake {
  abstract fun update(steer: SteerMsg, seq: Long)
  abstract fun onObservedExpired()
}