package d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe

import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState

class ObservableSnakeEntity(direction: Direction, id: Int) :
  SnakeEntity(direction, id), ObservableEntity {
  
  constructor(x: Int, y: Int, direction: Direction, id: Int) : this(
     direction, id
  ) {
  }
  
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
  
  override fun atDead(context: GameEngine) {
    super.atDead(context)
    observableExpired()
  }
}