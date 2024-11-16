package d.zhdanov.ccfit.nsu.core.game

data class GameConfig(
  val width: Int,
  val height: Int,
  val updatesPerSecond: Int,
  val maxSnakesQuantityAddedPerUpdate: Int
)
