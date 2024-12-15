package d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe

import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity

interface ObservableEntity : Entity {
  fun addObserver(action: () -> Unit)
  fun observableExpired()
  fun observerExpired()
}