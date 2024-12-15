package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

data class Snake(
  val snakeState: SnakeState = SnakeState.ALIVE,
  val playerId: Int,
  val cords: List<Coord>,
  val direction: Direction
)