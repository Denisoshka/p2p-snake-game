package d.zhdanov.ccfit.nsu.core.interaction.v1

import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT

fun <MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT> restoreGameState(
  gameEngine: GameEngine,
  gameController: GameController<MessageT, InboundMessageTranslator, Payload>,
  state: StateMsg
) {
  val entities: MutableList<Entity> = mutableListOf()
  val players: MutableMap<Int, NetPlayerContext> = HashMap()
  for(pl in state.players) {
    val sn = SnakeEnt()
    NetPlayerContext(mutableListOf())
  }
}

fun restoreSnake(pl: GamePlayer, eng): SnakeEnt {
  val sn = SnakeEnt()
}