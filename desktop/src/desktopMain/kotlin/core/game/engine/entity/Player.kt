package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

open class Player(
  val name: String,
  val snake: Snake,
) : Entity by snake {
  fun update(steer: SteerMsg) {
    snake.changeState(steer.direction)
  }

  fun restore(state: List<Coord>) {
    snake.restoreState(state)
  }

  fun onObservedExpired() {}
}