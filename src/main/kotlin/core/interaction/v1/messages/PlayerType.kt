package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

enum class PlayerType {
  /**
   * A human player
   */
  HUMAN,

  /**
   * A robot, controlling its snake via an algorithm (this does not need
   * to be implemented yet but is provided in the protocol for future use)
   */
  ROBOT
}