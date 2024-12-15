package d.zhdanov.ccfit.nsu.core.game.engine.entity.passive

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

class AppleEntity(
  x: Int,
  y: Int,
) : Entity {
  override val head = GameMap.Cell(x, y)
  override var type = GameType.Apple
  override var alive = true
  override val hitBox = listOf(head)
  
  override fun checkCollisions(entity: Entity, context: GameEngine) {
    if(!alive) return
    val b = hitBox.first()
    if(entity.hitBox.any { a -> a.x == b.x && a.y == b.y }) {
      alive = false
    }
  }
  
  override fun update(context: GameEngine, sideEffects: List<Entity>) {}
  override fun hitBoxTravel(function: (x: Int, y: Int) -> Unit) {
    TODO("Not yet implemented")
  }
  
  override fun shootState(state: SnakesProto.GameState.Builder) {
    val xyi = hitBox.first()
val     appleShot = Mess
    state.foodsBuilderList.add()
    state.foods.add(Coord(xyi.x, xyi.y))
  }
  
  override fun atDead(context: GameEngine) {
    context
  }
  
  
  override fun restoreState(offsets: List<Coord>) {}
}