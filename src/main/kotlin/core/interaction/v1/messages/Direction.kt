package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

enum class Direction(val dx: Int, val dy: Int) {
  UP(0, -1),
  DOWN(0, 1),
  LEFT(-1, 0),
  RIGHT(1, 0);
  fun opposite() : Direction {
    return when (this) {
      UP -> DOWN
      DOWN -> UP
      LEFT -> RIGHT
      RIGHT -> LEFT
    }
  }
}