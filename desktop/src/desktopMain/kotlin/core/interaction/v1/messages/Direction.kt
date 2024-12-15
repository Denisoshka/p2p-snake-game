package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

import d.zhdanov.ccfit.nsu.SnakesProto

enum class Direction(val dx: Int, val dy: Int) {
  UP(0, -1),
  DOWN(0, 1),
  LEFT(-1, 0),
  RIGHT(1, 0);
  
  fun opposite(): Direction {
    return when(this) {
      UP    -> DOWN
      DOWN  -> UP
      LEFT  -> RIGHT
      RIGHT -> LEFT
    }
  }
  
  fun toProto() = when(this) {
    UP    -> SnakesProto.Direction.UP
    DOWN  -> SnakesProto.Direction.DOWN
    LEFT  -> SnakesProto.Direction.LEFT
    RIGHT -> SnakesProto.Direction.RIGHT
  }
  
  companion object {
    fun fromProto(dir: SnakesProto.Direction) = when(dir) {
      SnakesProto.Direction.UP    -> UP
      SnakesProto.Direction.DOWN  -> DOWN
      SnakesProto.Direction.LEFT  -> LEFT
      SnakesProto.Direction.RIGHT -> RIGHT
    }
  }
}