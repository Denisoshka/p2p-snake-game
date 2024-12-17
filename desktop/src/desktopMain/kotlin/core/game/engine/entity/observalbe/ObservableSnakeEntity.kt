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
  
  private var observer: (() -> Unit)? = null
  override fun mountObserver(action: () -> Unit) {
    synchronized(this) {
      if(observer != null) throw RuntimeException("Observer already initialized")
      observer = action
      super.snakeState = SnakeState.ALIVE
    }
  }
  
  override fun observableExpired() {
    synchronized(this) {
      observer?.invoke()
    }
  }
  
  override fun observerExpired() {
    synchronized(this) {
      super.snakeState = SnakeState.ZOMBIE
      observer = null
    }
  }
  
  override fun atDead() {
    super.atDead()
    observableExpired()
  }
}