package d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe

interface ObservableEntity {
  fun addObserver(action: () -> Unit)
  fun observableExpired()
  fun observerExpired()
}