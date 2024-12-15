package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

import d.zhdanov.ccfit.nsu.SnakesProto

enum class SnakeState {
  ALIVE,
  ZOMBIE;
  
  fun toProto() = when(this) {
    ALIVE  -> SnakesProto.GameState.Snake.SnakeState.ALIVE
    ZOMBIE -> SnakesProto.GameState.Snake.SnakeState.ZOMBIE
  }
  
  companion object {
    fun fromProto(state: SnakesProto.GameState.Snake.SnakeState) = when(state) {
      SnakesProto.GameState.Snake.SnakeState.ALIVE  -> ALIVE
      SnakesProto.GameState.Snake.SnakeState.ZOMBIE -> ZOMBIE
    }
  }
}