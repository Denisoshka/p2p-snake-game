package d.zhdanov.ccfit.nsu.core.game.core.entity.passive

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.core.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.core.entity.GameType
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils

class AppleEntity(
  x: Int, y: Int, override val gameContext: GameContext
) : Entity {
  override val head = GameMap.Cell(x, y)
  override var type = GameType.Apple
  override var alive = true
  override val hitBox: Iterable<Pair<Int, Int>> = Iterable {
    iterator { yield(Pair(head.x, head.y)) }
  }
  
  
  override fun checkCollisions(entity: Entity) {
    if(!alive) return
    if(entity !== this && head.x == entity.head.x && head.y == entity.head.y) {
      alive = false
    }
  }
  
  override fun update() {
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