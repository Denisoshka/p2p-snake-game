package d.zhdanov.ccfit.nsu.core.game.engine.entity.stardart

import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.game.states.impl.GameState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

class Apple(
  x: Int,
  y: Int,
  context: GameState,
) : Entity {
  override var type = GameType.Apple
  override var alive = true
  override val hitBox: MutableList<EntityOnMapInfo> =
    mutableListOf(EntityOnMapInfo(x, y))

  init {
    context.map.addEntity(this)
  }

  override fun checkCollisions(entity: Entity, context: GameState) {
    if(!alive) return
    val b = hitBox.first()
    if(entity.hitBox.any { a -> a.x == b.x && a.y == b.y }) {
      alive = false
    }
  }

  override fun update(context: GameState) {}

  override fun shootState(context: GameState, state: StateMsg) {
    val xy = hitBox.first()
    state.foods.add(Coord(xy.x, xy.y))
  }
}