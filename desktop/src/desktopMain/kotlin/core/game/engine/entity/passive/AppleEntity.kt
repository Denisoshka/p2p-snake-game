package d.zhdanov.ccfit.nsu.core.game.engine.entity.passive

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.NetworkGameContext
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils

class AppleEntity(
  x: Int,
  y: Int,
  override val networkGameContext: NetworkGameContext
) : Entity {
  override val head = GameMap.Cell(x, y)
  override var type = GameType.Apple
  override var alive = true
  override val hitBox = listOf(head)
  
  override fun checkCollisions(entity: Entity) {
    if(!alive) return
    if(entity !== this && head.x == entity.head.x && head.y == entity.head.y) {
      alive = false
    }
  }
  
  override fun update() {
  }
  
  override fun hitBoxTravel(function: (x: Int, y: Int) -> Unit) {
  }
  
  override fun shootState(state: SnakesProto.GameState.Builder) {
    val appleShot = MessageUtils.MessageProducer.getCoordBuilder(head.x, head.y)
    state.apply {
      foodsBuilderList.add(appleShot)
    }
  }
  
  override fun atDead() {
  }
}