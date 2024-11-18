package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord

open class Player(
  val name: String,
  val snakeEnt: SnakeEnt,
) : Entity by snakeEnt {
  override fun restoreHitbox(offsets: List<Coord>) {
    snakeEnt.restoreHitbox(offsets)
  }
}