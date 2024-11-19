package d.zhdanov.ccfit.nsu.core.game.engine.entity.standart

import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

class AppleEnt(
  x: Int,
  y: Int,
) : Entity {
  override var type = GameType.Apple
  override var alive = true
  override val hitBox: MutableList<EntityOnMapInfo> =
    mutableListOf(EntityOnMapInfo(x, y))

  override fun checkCollisions(entity: Entity, context: GameEngine) {
    if(!alive) return
    val b = hitBox.first()
    if(entity.hitBox.any { a -> a.x == b.x && a.y == b.y }) {
      alive = false
    }
  }

  override fun update(context: GameEngine, sideEffects: List<Entity>) {}

  override fun shootState(state: StateMsg) {
    val xyi = hitBox.first()
    state.foods.add(Coord(xyi.x, xyi.y))
  }

  override fun atDead(context: GameEngine) {
    context.map.removeEntity(this)
  }

  override fun restoreHitbox(offsets: List<Coord>) {}
}