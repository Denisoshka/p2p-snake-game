package d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState

class ObservableSnakeEntity : SnakeEntity, ObservableEntity {
  constructor(
    snake: SnakesProto.GameState.Snake,
    score: Int,
    gameContext: GameContext,
  ) : super(snake, score, gameContext)
  
  constructor(
    id: Int, gameContext: GameContext, x: Int, y: Int,
  ) : super(id, gameContext, x, y)
  
  private val subscribers: MutableList<() -> Unit> = mutableListOf()
  override fun addObserver(action: () -> Unit) {
    synchronized(subscribers) {
      if(super.snakeState == SnakeState.ZOMBIE) {
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