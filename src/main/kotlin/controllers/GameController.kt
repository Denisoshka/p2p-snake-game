package d.zhdanov.ccfit.nsu.controllers

import core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.states.State

class GameController<MessageT, InboundMessageTranslatorT : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT>(
  baseState: State,
  val ncStateHandler: NetworkStateMachine<MessageT, InboundMessageTranslatorT, PayloadT>
) {
  private val upSteer = SteerMsg(Direction.UP)
  private val rightSteer = SteerMsg(Direction.RIGHT)
  private val leftSteer = SteerMsg(Direction.LEFT)
  private val downSteer = SteerMsg(Direction.DOWN)
  fun submitSteerMsg() {
    ncStateHandler.submitSteerMsg()
  }
}