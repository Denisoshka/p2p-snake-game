package d.zhdanov.ccfit.nsu.controllers

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine

class GameController(
) {
  private val ncStateHandler: NetworkStateMachine = TODO()
  private val upSteer = SteerMsg(Direction.UP)
  private val rightSteer = SteerMsg(Direction.RIGHT)
  private val leftSteer = SteerMsg(Direction.LEFT)
  private val downSteer = SteerMsg(Direction.DOWN)
  fun submitSteerMsg() {
    ncStateHandler.submitSteerMsg()
  }
}