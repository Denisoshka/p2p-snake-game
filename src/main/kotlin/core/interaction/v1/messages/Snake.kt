package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

data class Snake(
  var snakeState: SnakeState = SnakeState.ALIVE,
  var playerId: Int,
  var cords: List<Coord>,
  var direction: Direction
)