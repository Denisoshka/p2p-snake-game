package d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.NetworkGameContext
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState

class ObservableSnakeEntity : SnakeEntity, ObservableEntity {
  constructor(
    snake: SnakesProto.GameState.Snake,
    score: Int,
    networkGameContext: NetworkGameContext,
  ) : super(snake, score, networkGameContext)
  
  constructor(
    id: Int, networkGameContext: NetworkGameContext, x: Int, y: Int,
  ) : super(id, networkGameContext, x, y)
  
  private val subscribers: MutableList<() -> Unit> = mutableListOf()
  override fun addObserver(action: () -> Unit) {
    synchronized(subscribers) {
      if(!super.alive) {
        action()
      } else {
        subscribers.add(action)
      }
    }
  }
  
  override fun observableExpired() {
    synchronized(subscribers) {
      subscribers.forEach { it() }
    }
  }
  
  override fun observerExpired() {
    super.snakeState = SnakeState.ZOMBIE
  }
  
  override fun atDead() {
    super.atDead()
    observableExpired()
  }
}