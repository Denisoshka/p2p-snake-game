package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

import d.zhdanov.ccfit.nsu.SnakesProto

enum class PlayerType {
  /**
   * A human player
   */
  HUMAN,
  
  /**
   * A robot, controlling its snake via an algorithm (this does not need
   * to be implemented yet but is provided in the protocol for future use)
   */
  ROBOT;
  
  fun toProto() = when(this) {
    HUMAN -> SnakesProto.PlayerType.HUMAN
    ROBOT -> SnakesProto.PlayerType.ROBOT
  }
  
  companion object {
    fun fromProto(type: SnakesProto.PlayerType) = when(type) {
      SnakesProto.PlayerType.HUMAN -> HUMAN
      SnakesProto.PlayerType.ROBOT -> ROBOT
    }
  }
}