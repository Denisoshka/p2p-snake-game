package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

open class Player(
  val name: String,
  val snake: Snake,
) : Entity by snake {
  fun changeState(steer: SteerMsg) {
    snake.changeState(steer.direction)
  }

  override fun restoreHitbox(offsets: List<Coord>) {
    snake.restoreHitbox(offsets)
  }
}