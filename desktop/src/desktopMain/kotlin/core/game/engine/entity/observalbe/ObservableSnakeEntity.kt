package d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe

import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState

class ObservableSnakeEntity(direction: Direction, id: Int) :
  SnakeEntity(direction, id), ObservableEntity {
  private val subscribers: MutableList<() -> Unit> = mutableListOf()
  override fun addObserver(action: () -> Unit) {
    subscribers.add(action)
  }
  
  override fun observableExpired() {
    subscribers.forEach { it() }
  }
  
  override fun observerExpired() {
    super.snakeState = SnakeState.ZOMBIE
  }
  
  override fun atDead(context: GameEngine) {
    super.atDead(context)
    observableExpired()
  }
}